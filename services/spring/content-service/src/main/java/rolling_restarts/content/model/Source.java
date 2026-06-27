package rolling_restarts.content.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sources")
public class Source {

	@Id
	private String id;

	private String name;

	@Indexed(unique = true)
	private String rssUrl;

	private String initials;

	private boolean active = true;

	private Instant lastFetchedAt;

	private Instant createdAt = Instant.now();

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getRssUrl() { return rssUrl; }
	public void setRssUrl(String rssUrl) { this.rssUrl = rssUrl; }

	public String getInitials() { return initials; }
	public void setInitials(String initials) { this.initials = initials; }

	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }

	public Instant getLastFetchedAt() { return lastFetchedAt; }
	public void setLastFetchedAt(Instant lastFetchedAt) { this.lastFetchedAt = lastFetchedAt; }

	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
