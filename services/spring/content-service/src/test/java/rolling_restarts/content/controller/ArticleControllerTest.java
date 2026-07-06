package rolling_restarts.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import rolling_restarts.content.config.SecurityConfig;
import rolling_restarts.content.model.Article;
import rolling_restarts.content.service.ArticleService;

@WebMvcTest(ArticleController.class)
@Import(SecurityConfig.class)
class ArticleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ArticleService articleService;

	@Test
	void list_returnsPagedArticles() throws Exception {
		Article article = new Article();
		article.setId("a1");
		article.setHeadline("Test Article");
		article.setSnippet("A test snippet");
		article.setPublishedAt(Instant.now());
		Page<Article> page = new PageImpl<>(List.of(article));
		when(articleService.findAll(isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

		mockMvc.perform(get("/articles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].headline").value("Test Article"));
	}

	@Test
	void list_withSourceFilter_returnsFiltered() throws Exception {
		Page<Article> page = new PageImpl<>(List.of());
		when(articleService.findAll(eq("src1"), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

		mockMvc.perform(get("/articles").param("sourceId", "src1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void list_withSearchQuery_returnsFiltered() throws Exception {
		Article article = new Article();
		article.setId("a1");
		article.setHeadline("Climate update");
		Page<Article> page = new PageImpl<>(List.of(article));
		when(articleService.findAll(isNull(), isNull(), eq("climate"), any(Pageable.class))).thenReturn(page);

		mockMvc.perform(get("/articles").param("q", "climate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].headline").value("Climate update"));
	}

	@Test
	void get_existingArticle_returns200() throws Exception {
		Article article = new Article();
		article.setId("a1");
		article.setHeadline("Found Article");
		when(articleService.findById("a1")).thenReturn(article);

		mockMvc.perform(get("/articles/a1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.headline").value("Found Article"));
	}

	@Test
	void get_missingArticle_returns404() throws Exception {
		when(articleService.findById("missing")).thenThrow(new IllegalArgumentException("Article not found: missing"));

		mockMvc.perform(get("/articles/missing"))
				.andExpect(status().isNotFound());
	}
}
