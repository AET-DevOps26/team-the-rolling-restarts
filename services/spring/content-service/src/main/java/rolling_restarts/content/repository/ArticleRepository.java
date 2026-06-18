package rolling_restarts.content.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import rolling_restarts.content.model.Article;

public interface ArticleRepository extends MongoRepository<Article, String> {

	Page<Article> findBySourceId(String sourceId, Pageable pageable);

	Page<Article> findByTopicId(String topicId, Pageable pageable);

	Page<Article> findBySourceIdAndTopicId(String sourceId, String topicId, Pageable pageable);

	List<Article> findByIdIn(List<String> ids);

	boolean existsByExternalUrl(String externalUrl);
}
