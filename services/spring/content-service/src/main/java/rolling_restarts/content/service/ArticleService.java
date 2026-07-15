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
		if (sourceId != null && sourceIds != null && !sourceIds.contains(sourceId)) {
			// The one specific source requested isn't in the caller's allowed set (e.g. a user
			// browsing a source they aren't subscribed to) — zero matches, not an error.
			return Page.empty(pageable);
		}
		Query mongoQuery = StringUtils.hasText(query)
				? TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(query.trim()))
				: new Query();
		applyFilters(mongoQuery, sourceId, sourceIds, topicId);
		return queryAndCount(mongoQuery, pageable);
	}

	/**
	 * Adds sourceId/topicId criteria. sourceId and sourceIds both constrain the same "sourceId"
	 * field, so at most one criterion is added for it — Spring Data MongoDB's Query rejects a
	 * second criterion for a field name already present. Both having already survived the
	 * sourceId-not-in-sourceIds check in {@link #findAll}, filtering on sourceId alone (the more
	 * specific of the two) is equivalent to filtering on both.
	 */
	private void applyFilters(Query mongoQuery, String sourceId, List<String> sourceIds, String topicId) {
		if (sourceId != null) {
			mongoQuery.addCriteria(Criteria.where("sourceId").is(sourceId));
		} else if (sourceIds != null) {
			mongoQuery.addCriteria(Criteria.where("sourceId").in(sourceIds));
		}
		if (topicId != null) {
			mongoQuery.addCriteria(Criteria.where("topicId").is(topicId));
		}
	}

	private Page<Article> queryAndCount(Query mongoQuery, Pageable pageable) {
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
