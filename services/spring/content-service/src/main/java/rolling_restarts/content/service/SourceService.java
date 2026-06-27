package rolling_restarts.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import rolling_restarts.content.model.Source;

/**
 * Manages the shared subscriber count on {@link Source}. Sources are shared between users, so a
 * source is never deleted directly; instead its {@code subscriberCount} is incremented on subscribe
 * and decremented on unsubscribe, and the source is auto-removed once nobody is subscribed.
 *
 * <p>The counter is mutated with atomic {@code $inc} operations via {@link MongoTemplate} so
 * concurrent subscribe/unsubscribe calls cannot corrupt it.
 */
@Service
public class SourceService {

	private static final Logger log = LoggerFactory.getLogger(SourceService.class);

	private final MongoTemplate mongoTemplate;

	public SourceService(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/**
	 * Atomically increments the subscriber count.
	 *
	 * @return the updated source, or {@code null} if no source with {@code id} exists.
	 */
	public Source subscribe(String id) {
		return mongoTemplate.findAndModify(
				Query.query(Criteria.where("_id").is(id)),
				new Update().inc("subscriberCount", 1),
				FindAndModifyOptions.options().returnNew(true),
				Source.class);
	}

	/**
	 * Atomically decrements the subscriber count. If it reaches zero (or below) the source is
	 * removed entirely.
	 *
	 * @return {@code true} if a matching source existed, {@code false} otherwise.
	 */
	public boolean unsubscribe(String id) {
		Source updated = mongoTemplate.findAndModify(
				Query.query(Criteria.where("_id").is(id)),
				new Update().inc("subscriberCount", -1),
				FindAndModifyOptions.options().returnNew(true),
				Source.class);
		if (updated == null) {
			return false;
		}
		if (updated.getSubscriberCount() <= 0) {
			mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), Source.class);
			log.info("Removed source {} after its subscriber count reached zero", id);
		}
		return true;
	}
}
