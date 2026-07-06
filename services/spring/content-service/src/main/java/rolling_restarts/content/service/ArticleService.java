package rolling_restarts.content.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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

	public Page<Article> findAll(String sourceId, String topicId, String query, Pageable pageable) {
		if (!StringUtils.hasText(query)) {
			return findWithoutTextSearch(sourceId, topicId, pageable);
		}
		return findWithTextSearch(sourceId, topicId, query.trim(), pageable);
	}

	private Page<Article> findWithoutTextSearch(String sourceId, String topicId, Pageable pageable) {
		if (sourceId != null && topicId != null) {
			return articleRepository.findBySourceIdAndTopicId(sourceId, topicId, pageable);
		} else if (sourceId != null) {
			return articleRepository.findBySourceId(sourceId, pageable);
		} else if (topicId != null) {
			return articleRepository.findByTopicId(topicId, pageable);
		}
		return articleRepository.findAll(pageable);
	}

	private Page<Article> findWithTextSearch(
			String sourceId, String topicId, String query, Pageable pageable) {
		List<Criteria> filters = new ArrayList<>();
		if (sourceId != null) {
			filters.add(Criteria.where("sourceId").is(sourceId));
		}
		if (topicId != null) {
			filters.add(Criteria.where("topicId").is(topicId));
		}
		String pattern = Pattern.quote(query);
		filters.add(new Criteria().orOperator(
				Criteria.where("headline").regex(pattern, "i"),
				Criteria.where("snippet").regex(pattern, "i")));

		Query mongoQuery = Query.query(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
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
