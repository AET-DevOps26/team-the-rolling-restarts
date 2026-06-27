package rolling_restarts.content.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rolling_restarts.content.model.Topic;

public interface TopicRepository extends MongoRepository<Topic, String> {
}
