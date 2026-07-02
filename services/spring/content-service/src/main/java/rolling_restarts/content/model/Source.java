package rolling_restarts.content.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

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

	// Number of users currently subscribed to this shared source. Adjusted via subscribe/
	// unsubscribe; when it drops to 0 the source is auto-removed (see SourceService).
	private int subscriberCount = 0;

	private Instant lastFetchedAt;

	// Status of the most recent fetch. Newly created sources are PENDING until their first
	// asynchronous fetch completes; fetchError carries a short message when it FAILED.
	private FetchStatus fetchStatus = FetchStatus.PENDING;

	private String fetchError;

	private Instant createdAt = Instant.now();

	/**
	 * Derives a stable id from the feed URL so the same feed always maps to the same source, even
	 * after the source is auto-deleted (last subscriber leaves) and later re-added. This keeps
	 * previously fetched articles — which pin this id — associated with the re-created source.
	 */
	public static String idForRssUrl(String rssUrl) {
		String normalized = rssUrl == null ? "" : rssUrl.trim();
		return UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8)).toString();
	}

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

	public int getSubscriberCount() { return subscriberCount; }
	public void setSubscriberCount(int subscriberCount) { this.subscriberCount = subscriberCount; }

	public Instant getLastFetchedAt() { return lastFetchedAt; }
	public void setLastFetchedAt(Instant lastFetchedAt) { this.lastFetchedAt = lastFetchedAt; }

	public FetchStatus getFetchStatus() { return fetchStatus; }
	public void setFetchStatus(FetchStatus fetchStatus) { this.fetchStatus = fetchStatus; }

	public String getFetchError() { return fetchError; }
	public void setFetchError(String fetchError) { this.fetchError = fetchError; }

	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
