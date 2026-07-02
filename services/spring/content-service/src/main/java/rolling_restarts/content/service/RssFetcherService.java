package rolling_restarts.content.service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import rolling_restarts.content.util.RssHtmlUtils;
import rolling_restarts.content.util.UrlSafetyValidator;

@Service
public class RssFetcherService {

	private static final Logger log = LoggerFactory.getLogger(RssFetcherService.class);

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final SourceRepository sourceRepository;
	private final ArticleRepository articleRepository;
	private final MongoTemplate mongoTemplate;

	// Redirects are disabled so a public URL cannot 30x-bounce us to an internal address after
	// the up-front SSRF validation.
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
		// Re-validate at fetch time: DNS may have re-resolved to an internal address since the
		// source was created (TOCTOU), so the create-time check alone is not sufficient.
		UrlSafetyValidator.validatePublicUrl(source.getRssUrl());

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(source.getRssUrl()))
				.timeout(REQUEST_TIMEOUT)
				.GET()
				.build();
		HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

		// Redirects are not followed; a 3xx is treated as a failed fetch rather than chased to a
		// potentially internal Location.
		if (response.statusCode() >= 300) {
			response.body().close();
			throw new IllegalStateException("Unexpected HTTP status " + response.statusCode()
					+ " fetching " + source.getRssUrl());
		}

		SyndFeedInput input = new SyndFeedInput();
		try (XmlReader reader = new XmlReader(response.body())) {
			SyndFeed feed = input.build(reader);

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
			// pinned to the dead id and never appear for the re-created source, so re-point them here.
			List<Article> reassigned = new ArrayList<>();
			for (Article existing : existingArticles) {
				if (!source.getId().equals(existing.getSourceId())) {
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
}
