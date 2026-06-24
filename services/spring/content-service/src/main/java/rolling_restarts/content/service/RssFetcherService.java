package rolling_restarts.content.service;

import java.net.URI;
import java.time.Instant;
import java.util.List;

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

@Service
public class RssFetcherService {

	private static final Logger log = LoggerFactory.getLogger(RssFetcherService.class);

	private final SourceRepository sourceRepository;
	private final ArticleRepository articleRepository;

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
		SyndFeedInput input = new SyndFeedInput();
		try (XmlReader reader = new XmlReader(URI.create(source.getRssUrl()).toURL().openStream())) {
			SyndFeed feed = input.build(reader);
			int newArticles = 0;

			for (SyndEntry entry : feed.getEntries()) {
				String url = entry.getLink();
				if (url == null || articleRepository.existsByExternalUrl(url)) {
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
								: Instant.now());
				article.setFetchedAt(Instant.now());

				articleRepository.save(article);
				newArticles++;
			}

			source.setLastFetchedAt(Instant.now());
			sourceRepository.save(source);
			log.info("Fetched {} new articles from {}", newArticles, source.getName());
		}
	}
}
