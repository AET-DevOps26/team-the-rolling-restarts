package rolling_restarts.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import rolling_restarts.content.model.Article;
import rolling_restarts.content.model.Source;
import rolling_restarts.content.repository.ArticleRepository;
import rolling_restarts.content.repository.SourceRepository;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class RssFetcherServiceTest {

	@Mock
	private SourceRepository sourceRepository;

	@Mock
	private ArticleRepository articleRepository;

	@Mock
	private MongoTemplate mongoTemplate;

	private RssFetcherService rssFetcherService;

	private RssFetcherService service() {
		return new RssFetcherService(sourceRepository, articleRepository, mongoTemplate);
	}

	private static Source source(String id) {
		Source source = new Source();
		source.setId(id);
		source.setName("Source " + id);
		return source;
	}

	private static Article existingArticle(String sourceId, String externalUrl) {
		Article article = new Article();
		article.setId("article-" + externalUrl.hashCode());
		article.setSourceId(sourceId);
		article.setExternalUrl(externalUrl);
		return article;
	}

	private static SyndFeed feedWithEntry(String link) {
		SyndEntryImpl entry = new SyndEntryImpl();
		entry.setLink(link);
		entry.setTitle("Headline");
		SyndFeedImpl feed = new SyndFeedImpl();
		feed.setEntries(List.of(entry));
		return feed;
	}

	@Test
	void persistFeedEntries_orphanSource_repointsArticle() {
		rssFetcherService = service();
		String url = "https://example.com/article";
		Article existing = existingArticle("dead-source", url);
		when(articleRepository.findByExternalUrlIn(List.of(url))).thenReturn(List.of(existing));
		// The article's original source no longer exists (last subscriber unsubscribed, hard-deleting it).
		when(sourceRepository.findAllById(anyIterable())).thenReturn(List.of());

		rssFetcherService.persistFeedEntries(source("new-source"), feedWithEntry(url));

		ArgumentCaptor<List<Article>> savedCaptor = ArgumentCaptor.forClass(List.class);
		verify(articleRepository).saveAll(savedCaptor.capture());
		assertThat(savedCaptor.getValue()).hasSize(1);
		assertThat(savedCaptor.getValue().get(0).getSourceId()).isEqualTo("new-source");
	}

	@Test
	void persistFeedEntries_liveOtherSource_doesNotRepoint() {
		rssFetcherService = service();
		String url = "https://example.com/shared-wire-article";
		Article existing = existingArticle("other-live-source", url);
		when(articleRepository.findByExternalUrlIn(List.of(url))).thenReturn(List.of(existing));
		// The article's original source is a different source that is still live.
		when(sourceRepository.findAllById(anyIterable())).thenReturn(List.of(source("other-live-source")));

		rssFetcherService.persistFeedEntries(source("new-source"), feedWithEntry(url));

		assertThat(existing.getSourceId()).isEqualTo("other-live-source");
		verify(articleRepository, never()).saveAll(anyIterable());
	}

	@Test
	void persistFeedEntries_sameSource_noOp() {
		rssFetcherService = service();
		String url = "https://example.com/own-article";
		Article existing = existingArticle("same-source", url);
		when(articleRepository.findByExternalUrlIn(List.of(url))).thenReturn(List.of(existing));

		rssFetcherService.persistFeedEntries(source("same-source"), feedWithEntry(url));

		assertThat(existing.getSourceId()).isEqualTo("same-source");
		verify(articleRepository, never()).saveAll(anyIterable());
		verify(sourceRepository, never()).findAllById(anyIterable());
	}
}
