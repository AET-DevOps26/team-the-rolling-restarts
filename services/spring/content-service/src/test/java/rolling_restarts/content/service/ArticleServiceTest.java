package rolling_restarts.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.Document;
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
	void findAll_withoutQuery_noFilters_usesMongoTemplate() {
		PageRequest pageable = PageRequest.of(0, 20);
		Article article = new Article();
		article.setId("a1");
		when(mongoTemplate.count(any(Query.class), eq(Article.class))).thenReturn(1L);
		when(mongoTemplate.find(any(Query.class), eq(Article.class))).thenReturn(List.of(article));

		Page<Article> result = articleService.findAll(null, null, null, null, pageable);

		assertThat(result.getContent()).containsExactly(article);
		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		verify(mongoTemplate).count(queryCaptor.capture(), eq(Article.class));
		assertThat(queryCaptor.getValue().getQueryObject()).isEmpty();
	}

	@Test
	void findAll_withBlankQuery_appliesSourceIdCriteria() {
		PageRequest pageable = PageRequest.of(0, 20);
		when(mongoTemplate.count(any(Query.class), eq(Article.class))).thenReturn(0L);
		when(mongoTemplate.find(any(Query.class), eq(Article.class))).thenReturn(List.of());

		articleService.findAll("src1", null, null, "   ", pageable);

		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		verify(mongoTemplate).count(queryCaptor.capture(), eq(Article.class));
		Document queryDoc = queryCaptor.getValue().getQueryObject();
		assertThat(queryDoc.getString("sourceId")).isEqualTo("src1");
	}

	@Test
	void findAll_withSourceIds_buildsInCriteria() {
		PageRequest pageable = PageRequest.of(0, 20);
		when(mongoTemplate.count(any(Query.class), eq(Article.class))).thenReturn(0L);
		when(mongoTemplate.find(any(Query.class), eq(Article.class))).thenReturn(List.of());

		articleService.findAll(null, List.of("src1", "src2"), null, null, pageable);

		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		verify(mongoTemplate).count(queryCaptor.capture(), eq(Article.class));
		Document queryDoc = queryCaptor.getValue().getQueryObject();
		Document sourceIdCriteria = queryDoc.get("sourceId", Document.class);
		assertThat(sourceIdCriteria).isNotNull();
		assertThat(sourceIdCriteria.getList("$in", String.class)).containsExactly("src1", "src2");
	}

	@Test
	void findAll_withEmptySourceIdsList_returnsEmptyPageWithoutQuerying() {
		PageRequest pageable = PageRequest.of(0, 20);

		Page<Article> result = articleService.findAll(null, List.of(), null, null, pageable);

		assertThat(result.getTotalElements()).isZero();
		assertThat(result.getContent()).isEmpty();
		verify(mongoTemplate, never()).count(any(Query.class), eq(Article.class));
		verify(mongoTemplate, never()).find(any(Query.class), eq(Article.class));
	}

	@Test
	void findAll_withQuery_searchesHeadlineAndSnippet() {
		PageRequest pageable = PageRequest.of(0, 20);
		Article article = new Article();
		article.setId("a1");
		when(mongoTemplate.count(any(Query.class), eq(Article.class))).thenReturn(45L);
		when(mongoTemplate.find(any(Query.class), eq(Article.class))).thenReturn(List.of(article));

		Page<Article> result = articleService.findAll("src1", null, "topic1", "climate", pageable);

		assertThat(result.getContent()).containsExactly(article);
		assertThat(result.getTotalElements()).isEqualTo(45L);
		assertThat(result.getTotalPages()).isEqualTo(3);
		assertThat(result.getNumber()).isZero();
		assertThat(result.getSize()).isEqualTo(20);

		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		verify(mongoTemplate).count(queryCaptor.capture(), eq(Article.class));
		Document queryDoc = queryCaptor.getValue().getQueryObject();
		assertThat(queryDoc.getString("sourceId")).isEqualTo("src1");
		assertThat(queryDoc.getString("topicId")).isEqualTo("topic1");
		Document text = queryDoc.get("$text", Document.class);
		assertThat(text).isNotNull();
		assertThat(text.getString("$search")).isEqualTo("climate");
	}

	@Test
	void findAll_withQueryAndSourceIds_combinesTextAndInCriteria() {
		PageRequest pageable = PageRequest.of(0, 20);
		when(mongoTemplate.count(any(Query.class), eq(Article.class))).thenReturn(2L);
		when(mongoTemplate.find(any(Query.class), eq(Article.class))).thenReturn(List.of());

		articleService.findAll(null, List.of("src1", "src2"), null, "climate", pageable);

		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		verify(mongoTemplate).count(queryCaptor.capture(), eq(Article.class));
		Document queryDoc = queryCaptor.getValue().getQueryObject();
		Document sourceIdCriteria = queryDoc.get("sourceId", Document.class);
		assertThat(sourceIdCriteria).isNotNull();
		assertThat(sourceIdCriteria.getList("$in", String.class)).containsExactly("src1", "src2");
		assertThat(queryDoc.get("$text", Document.class)).isNotNull();
	}
}
