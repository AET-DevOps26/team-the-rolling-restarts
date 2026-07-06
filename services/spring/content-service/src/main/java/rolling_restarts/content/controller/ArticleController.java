package rolling_restarts.content.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import rolling_restarts.content.model.Article;
import rolling_restarts.content.service.ArticleService;

@RestController
@RequestMapping("/articles")
@Tag(name = "Articles", description = "News article retrieval")
public class ArticleController {

	private final ArticleService articleService;

	public ArticleController(ArticleService articleService) {
		this.articleService = articleService;
	}

	@GetMapping
	@Operation(
			summary = "List articles",
			description = "Returns a paginated list of articles, optionally filtered by source, topic, and/or search query")
	public Page<Article> list(
			@Parameter(description = "Filter by source ID") @RequestParam(required = false) String sourceId,
			@Parameter(description = "Filter by topic ID") @RequestParam(required = false) String topicId,
			@Parameter(description = "Case-insensitive search in headline and snippet") @RequestParam(required = false) String q,
			Pageable pageable) {
		return articleService.findAll(sourceId, topicId, q, pageable);
	}

	@GetMapping("/{id}")
	@Operation(
			summary = "Get full article",
			responses = {
					@ApiResponse(responseCode = "200", description = "Article found"),
					@ApiResponse(responseCode = "404", description = "Article not found")
			})
	public Article get(@PathVariable String id) {
		return articleService.findById(id);
	}

	@PostMapping("/saved")
	@SecurityRequirement(name = "bearer-jwt")
	@Operation(
			summary = "Batch-get articles by IDs",
			description = "Returns articles matching the provided list of IDs, useful for fetching a user's saved articles",
			responses = {
					@ApiResponse(responseCode = "200", description = "Matching articles"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public List<Article> getSaved(@RequestBody List<String> articleIds) {
		return articleService.findByIds(articleIds);
	}
}
