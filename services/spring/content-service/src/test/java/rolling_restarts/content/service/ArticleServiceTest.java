package rolling_restarts.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import rolling_restarts.content.model.Article;
import rolling_restarts.content.repository.ArticleRepository;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

	@Mock
	private ArticleRepository articleRepository;

	@Mock
	private MongoTemplate mongoTemplate;

	@InjectMocks
	private ArticleService articleService;

	@Test
	void findAll_withoutQuery_usesRepository() {
		PageRequest pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(List.of());
		when(articleRepository.findAll(pageable)).thenReturn(page);

		Page<Article> result = articleService.findAll(null, null, null, pageable);

		assertThat(result).isSameAs(page);
		verify(mongoTemplate, never()).find(any(Query.class), eq(Article.class));
	}

	@Test
	void findAll_withBlankQuery_usesRepository() {
		PageRequest pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(List.of());
		when(articleRepository.findBySourceId("src1", pageable)).thenReturn(page);

		Page<Article> result = articleService.findAll("src1", null, "   ", pageable);

		assertThat(result).isSameAs(page);
		verify(mongoTemplate, never()).find(any(Query.class), eq(Article.class));
	}

	@Test
	void findAll_withQuery_searchesHeadlineAndSnippet() {
		PageRequest pageable = PageRequest.of(0, 20);
		Article article = new Article();
		article.setId("a1");
		when(mongoTemplate.count(any(Query.class), eq(Article.class))).thenReturn(1L);
		when(mongoTemplate.find(any(Query.class), eq(Article.class))).thenReturn(List.of(article));

		Page<Article> result = articleService.findAll("src1", "topic1", "climate", pageable);

		assertThat(result.getContent()).containsExactly(article);
		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		verify(mongoTemplate).count(queryCaptor.capture(), eq(Article.class));
		assertThat(queryCaptor.getValue().getQueryObject().toString())
				.contains("sourceId")
				.contains("topicId")
				.contains("headline")
				.contains("snippet");
	}
}
