package rolling_restarts.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import rolling_restarts.content.model.Source;

public interface SourceRepository extends MongoRepository<Source, String> {

	Optional<Source> findByRssUrl(String rssUrl);

	List<Source> findByActiveTrue();

	boolean existsByRssUrl(String rssUrl);
}
