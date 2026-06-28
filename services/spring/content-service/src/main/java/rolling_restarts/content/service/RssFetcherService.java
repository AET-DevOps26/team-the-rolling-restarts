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
import org.springframework.stereotype.Service;

import rolling_restarts.content.model.Article;
import rolling_restarts.content.model.Source;
import rolling_restarts.content.repository.ArticleRepository;
import rolling_restarts.content.repository.SourceRepository;
import rolling_restarts.content.util.UrlSafetyValidator;

@Service
public class RssFetcherService {

	private static final Logger log = LoggerFactory.getLogger(RssFetcherService.class);

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final SourceRepository sourceRepository;
	private final ArticleRepository articleRepository;

	// Redirects are disabled so a public URL cannot 30x-bounce us to an internal address after
	// the up-front SSRF validation.
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	public RssFetcherService(SourceRepository sourceRepository, ArticleRepository articleRepository) {
		this.sourceRepository = sourceRepository;
		this.articleRepository = articleRepository;
	}

	public void fetchAllActiveSources() {
		List<Source> sources = sourceRepository.findByActiveTrue();
		for (Source source : sources) {
			try {
				fetchSource(source);
			} catch (Exception e) {
				log.error("Failed to fetch RSS feed for source {}: {}", source.getName(), e.getMessage());
			}
		}
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

			Set<String> existingUrls = articleRepository.findByExternalUrlIn(entryUrls).stream()
					.map(Article::getExternalUrl)
					.collect(Collectors.toSet());

			Instant now = Instant.now();
			List<Article> newArticles = new ArrayList<>();

			for (SyndEntry entry : feed.getEntries()) {
				String url = entry.getLink();
				if (url == null || existingUrls.contains(url)) {
					continue;
				}

				Article article = new Article();
				article.setHeadline(entry.getTitle());
				article.setSnippet(entry.getDescription() != null ? entry.getDescription().getValue() : "");
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
			source.setLastFetchedAt(now);
			sourceRepository.save(source);
			log.info("Fetched {} new articles from {}", newArticles.size(), source.getName());
		}
	}
}
