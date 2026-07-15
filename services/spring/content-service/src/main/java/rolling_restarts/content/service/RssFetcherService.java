package rolling_restarts.content.service;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import rolling_restarts.content.model.Article;
import rolling_restarts.content.model.FetchStatus;
import rolling_restarts.content.model.Source;
import rolling_restarts.content.repository.ArticleRepository;
import rolling_restarts.content.repository.SourceRepository;
import rolling_restarts.content.util.PinnedDnsResolverProvider;
import rolling_restarts.content.util.RssHtmlUtils;
import rolling_restarts.content.util.RssUrlNormalizer;
import rolling_restarts.content.util.UrlSafetyValidator;

@Service
public class RssFetcherService {

	private static final Logger log = LoggerFactory.getLogger(RssFetcherService.class);

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final SourceRepository sourceRepository;
	private final ArticleRepository articleRepository;
	private final MongoTemplate mongoTemplate;

	// Redirects are not auto-followed; same-host Location headers are handled manually in
	// fetchWithSameHostRedirect so http→https upgrades stay safe from SSRF bounce attacks.
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	public RssFetcherService(SourceRepository sourceRepository, ArticleRepository articleRepository,
			MongoTemplate mongoTemplate) {
		this.sourceRepository = sourceRepository;
		this.articleRepository = articleRepository;
		this.mongoTemplate = mongoTemplate;
	}

	public void fetchAllActiveSources() {
		List<Source> sources = sourceRepository.findByActiveTrue();
		for (Source source : sources) {
			fetchAndRecord(source);
		}
	}

	/**
	 * Fetches a single source's feed off the request thread. Used to populate articles
	 * immediately after a source is created without blocking the create response.
	 */
	@Async
	public void fetchSourceAsync(String sourceId) {
		sourceRepository.findById(sourceId).ifPresent(this::fetchAndRecord);
	}

	/**
	 * Fetches a source and records the outcome ({@link FetchStatus#SUCCESS} or
	 * {@link FetchStatus#FAILED} with a short error message) so clients can surface progress.
	 */
	private void fetchAndRecord(Source source) {
		Update update = new Update();
		try {
			fetchSource(source);
			update.set("fetchStatus", FetchStatus.SUCCESS)
					.set("fetchError", null)
					.set("lastFetchedAt", Instant.now());
		} catch (Exception e) {
			log.error("Failed to fetch RSS feed for source {}: {}", source.getName(), e.getMessage());
			update.set("fetchStatus", FetchStatus.FAILED)
					.set("fetchError", summarizeError(e));
		}
		// Update only the fetch-related fields so a concurrent subscribe/unsubscribe (which mutates
		// subscriberCount via an atomic $inc) is never clobbered by a stale full-document write.
		mongoTemplate.updateFirst(
				Query.query(Criteria.where("_id").is(source.getId())), update, Source.class);
	}

	private static String summarizeError(Exception e) {
		String message = e.getMessage();
		if (message == null || message.isBlank()) {
			message = e.getClass().getSimpleName();
		}
		return message.length() > 300 ? message.substring(0, 300) : message;
	}

	private void fetchSource(Source source) throws Exception {
		List<String> candidates = RssUrlNormalizer.fetchCandidates(source.getRssUrl());
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("Invalid RSS URL");
		}

		Exception lastError = null;
		for (String candidate : candidates) {
			try {
				fetchFromUrl(source, candidate);
				return;
			} catch (Exception e) {
				lastError = e;
				log.debug("Failed to fetch {} for source {}: {}", candidate, source.getName(), e.getMessage());
			}
		}
		if (lastError != null) {
			throw lastError;
		}
		throw new IllegalStateException("Could not fetch RSS feed for " + source.getName());
	}

	private void fetchFromUrl(Source source, String url) throws Exception {
		// Re-validate at fetch time: DNS may have re-resolved to an internal address since the
		// source was created (TOCTOU), so the create-time check alone is not sufficient.
		HttpResponse<InputStream> response = fetchWithSameHostRedirect(url);

		SyndFeedInput input = new SyndFeedInput();
		try (XmlReader reader = new XmlReader(response.body())) {
			SyndFeed feed = input.build(reader);
			persistFeedEntries(source, feed);
		}
	}

	/**
	 * Fetches a URL without auto-following redirects, but will follow a single same-host
	 * {@code Location} on 3xx so http→https upgrades on the original host still work safely.
	 */
	private HttpResponse<InputStream> fetchWithSameHostRedirect(String url) throws Exception {
		InetAddress[] addresses = UrlSafetyValidator.validatePublicUrl(url);
		URI originalUri = URI.create(url);
		String originalHost = originalUri.getHost();

		// Pin the connect-time resolution to exactly the addresses just validated, closing the
		// TOCTOU gap where a DNS-rebinding attacker could serve a different (internal) address to
		// HttpClient's own, independent re-resolution moments later. See PinnedDnsResolverProvider.
		PinnedDnsResolverProvider.pin(originalHost, List.of(addresses));
		try {
			String currentUrl = url;
			for (int hop = 0; hop < 2; hop++) {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(currentUrl))
						.timeout(REQUEST_TIMEOUT)
						.GET()
						.build();
				HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
				int status = response.statusCode();

				if (status >= 200 && status < 300) {
					return response;
				}

				if (status >= 300 && status < 400 && hop == 0) {
					String location = response.headers().firstValue("Location").orElse(null);
					response.body().close();
					if (location == null || location.isBlank()) {
						throw new IllegalStateException("Redirect without Location fetching " + currentUrl);
					}
					String redirectUrl = URI.create(currentUrl).resolve(location).toString();
					URI redirectUri = URI.create(redirectUrl);
					if (redirectUri.getHost() == null
							|| !redirectUri.getHost().equalsIgnoreCase(originalHost)) {
						throw new IllegalStateException("Cross-host redirect not allowed for " + currentUrl);
					}
					InetAddress[] redirectAddresses = UrlSafetyValidator.validatePublicUrl(redirectUrl);
					PinnedDnsResolverProvider.pin(originalHost, List.of(redirectAddresses));
					currentUrl = redirectUrl;
					continue;
				}

				response.body().close();
				throw new IllegalStateException(
						"Unexpected HTTP status " + status + " fetching " + currentUrl);
			}
			throw new IllegalStateException("Too many redirects fetching " + url);
		} finally {
			PinnedDnsResolverProvider.unpin(originalHost);
		}
	}

	void persistFeedEntries(Source source, SyndFeed feed) {
		List<String> entryUrls = feed.getEntries().stream()
				.map(SyndEntry::getLink)
				.filter(url -> url != null)
				.toList();

		List<Article> existingArticles = articleRepository.findByExternalUrlIn(entryUrls);
		Set<String> existingUrls = existingArticles.stream()
				.map(Article::getExternalUrl)
				.collect(Collectors.toSet());

		// A shared source is deleted when its last subscriber leaves; re-adding the same feed
		// mints a new source id. Existing articles (deduped globally by URL) would otherwise stay
		// pinned to the dead id and never appear for the re-created source, so re-point them here
		// — but only when the article's original source is actually gone. Two distinct, both-live
		// sources can legitimately carry the same external_url (syndicated/wire content); an id
		// mismatch alone doesn't mean the original source is dead.
		Set<String> otherSourceIds = existingArticles.stream()
				.map(Article::getSourceId)
				.filter(id -> !source.getId().equals(id))
				.collect(Collectors.toSet());
		Set<String> liveOtherSourceIds = new HashSet<>();
		if (!otherSourceIds.isEmpty()) {
			sourceRepository.findAllById(otherSourceIds).forEach(s -> liveOtherSourceIds.add(s.getId()));
		}

		List<Article> reassigned = new ArrayList<>();
		for (Article existing : existingArticles) {
			if (!source.getId().equals(existing.getSourceId())
					&& !liveOtherSourceIds.contains(existing.getSourceId())) {
				existing.setSourceId(source.getId());
				reassigned.add(existing);
			}
		}
		if (!reassigned.isEmpty()) {
			articleRepository.saveAll(reassigned);
			log.info("Re-pointed {} existing articles to source {}", reassigned.size(), source.getName());
		}

		Instant now = Instant.now();
		List<Article> newArticles = new ArrayList<>();

		for (SyndEntry entry : feed.getEntries()) {
			String url = entry.getLink();
			if (url == null || existingUrls.contains(url)) {
				continue;
			}

			Article article = new Article();
			article.setHeadline(entry.getTitle());
			String rawDescription = entry.getDescription() != null ? entry.getDescription().getValue() : "";
			article.setImageUrl(RssHtmlUtils.extractImageUrl(rawDescription));
			article.setSnippet(RssHtmlUtils.toPlainText(rawDescription));
			article.setBody(List.of());
			article.setSourceId(source.getId());
			article.setAuthor(entry.getAuthor());
			article.setExternalUrl(url);
			article.setPublishedAt(
					entry.getPublishedDate() != null
							? entry.getPublishedDate().toInstant()
							: now);
			article.setFetchedAt(now);
			newArticles.add(article);
		}

		if (!newArticles.isEmpty()) {
			articleRepository.saveAll(newArticles);
		}
		// lastFetchedAt / fetchStatus are persisted by fetchAndRecord via an atomic partial
		// update so the source's subscriberCount is never overwritten here.
		log.info("Fetched {} new articles from {}", newArticles.size(), source.getName());
	}
}
