package rolling_restarts.content.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import rolling_restarts.content.model.Article;
import rolling_restarts.content.repository.ArticleRepository;

@Service
public class ArticleService {

	private final ArticleRepository articleRepository;
	private final MongoTemplate mongoTemplate;

	public ArticleService(ArticleRepository articleRepository, MongoTemplate mongoTemplate) {
		this.articleRepository = articleRepository;
		this.mongoTemplate = mongoTemplate;
	}

	public Page<Article> findAll(
			String sourceId, List<String> sourceIds, String topicId, String query, Pageable pageable) {
		if (sourceIds != null && sourceIds.isEmpty()) {
			// Caller explicitly filtered to zero sources (e.g. a user subscribed to nothing) —
			// zero matches by definition, no need to query.
			return Page.empty(pageable);
		}
		if (!StringUtils.hasText(query)) {
			return findWithoutTextSearch(sourceId, sourceIds, topicId, pageable);
		}
		return findWithTextSearch(sourceId, sourceIds, topicId, query.trim(), pageable);
	}

	private Page<Article> findWithoutTextSearch(
			String sourceId, List<String> sourceIds, String topicId, Pageable pageable) {
		Query mongoQuery = new Query();
		if (sourceId != null) {
			mongoQuery.addCriteria(Criteria.where("sourceId").is(sourceId));
		}
		if (sourceIds != null) {
			mongoQuery.addCriteria(Criteria.where("sourceId").in(sourceIds));
		}
		if (topicId != null) {
			mongoQuery.addCriteria(Criteria.where("topicId").is(topicId));
		}
		long total = mongoTemplate.count(mongoQuery, Article.class);
		mongoQuery.with(pageable);
		List<Article> content = mongoTemplate.find(mongoQuery, Article.class);
		return new PageImpl<>(content, pageable, total);
	}

	private Page<Article> findWithTextSearch(
			String sourceId, List<String> sourceIds, String topicId, String query, Pageable pageable) {
		TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(query);
		Query mongoQuery = TextQuery.queryText(textCriteria);
		if (sourceId != null) {
			mongoQuery.addCriteria(Criteria.where("sourceId").is(sourceId));
		}
		if (sourceIds != null) {
			mongoQuery.addCriteria(Criteria.where("sourceId").in(sourceIds));
		}
		if (topicId != null) {
			mongoQuery.addCriteria(Criteria.where("topicId").is(topicId));
		}
		long total = mongoTemplate.count(mongoQuery, Article.class);
		mongoQuery.with(pageable);
		List<Article> content = mongoTemplate.find(mongoQuery, Article.class);
		return new PageImpl<>(content, pageable, total);
	}

	public Article findById(String id) {
		return articleRepository.findById(id).orElseThrow(() ->
				new IllegalArgumentException("Article not found: " + id));
	}

	public List<Article> findByIds(List<String> ids) {
		return articleRepository.findByIdIn(ids);
	}
}
