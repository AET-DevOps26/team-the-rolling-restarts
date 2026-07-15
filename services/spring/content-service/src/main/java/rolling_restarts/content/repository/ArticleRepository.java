package rolling_restarts.content.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import rolling_restarts.content.model.Article;

public interface ArticleRepository extends MongoRepository<Article, String> {

	List<Article> findByIdIn(List<String> ids);

	boolean existsByExternalUrl(String externalUrl);

	List<Article> findByExternalUrlIn(java.util.Collection<String> urls);
}
