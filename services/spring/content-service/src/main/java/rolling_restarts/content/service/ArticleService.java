package rolling_restarts.content.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import rolling_restarts.content.model.Article;
import rolling_restarts.content.repository.ArticleRepository;

@Service
public class ArticleService {

	private final ArticleRepository articleRepository;

	public ArticleService(ArticleRepository articleRepository) {
		this.articleRepository = articleRepository;
	}

	public Page<Article> findAll(String sourceId, String topicId, Pageable pageable) {
		if (sourceId != null && topicId != null) {
			return articleRepository.findBySourceIdAndTopicId(sourceId, topicId, pageable);
		} else if (sourceId != null) {
			return articleRepository.findBySourceId(sourceId, pageable);
		} else if (topicId != null) {
			return articleRepository.findByTopicId(topicId, pageable);
		}
		return articleRepository.findAll(pageable);
	}

	public Article findById(String id) {
		return articleRepository.findById(id).orElseThrow(() ->
				new IllegalArgumentException("Article not found: " + id));
	}

	public List<Article> findByIds(List<String> ids) {
		return articleRepository.findByIdIn(ids);
	}
}
