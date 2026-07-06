package rolling_restarts.content.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "articles")
public class Article {

	@Id
	private String id;

	private String headline;

	private String snippet;

	private String imageUrl;

	private List<String> body;

	@Indexed
	private String sourceId;

	@Indexed
	private String topicId;

	private String author;

	private Instant publishedAt;

	private int readingMinutes;

	private Instant fetchedAt = Instant.now();

	@Indexed(unique = true)
	private String externalUrl;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getHeadline() { return headline; }
	public void setHeadline(String headline) { this.headline = headline; }

	public String getSnippet() { return snippet; }
	public void setSnippet(String snippet) { this.snippet = snippet; }

	public String getImageUrl() { return imageUrl; }
	public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

	public List<String> getBody() { return body; }
	public void setBody(List<String> body) { this.body = body; }

	public String getSourceId() { return sourceId; }
	public void setSourceId(String sourceId) { this.sourceId = sourceId; }

	public String getTopicId() { return topicId; }
	public void setTopicId(String topicId) { this.topicId = topicId; }

	public String getAuthor() { return author; }
	public void setAuthor(String author) { this.author = author; }

	public Instant getPublishedAt() { return publishedAt; }
	public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

	public int getReadingMinutes() { return readingMinutes; }
	public void setReadingMinutes(int readingMinutes) { this.readingMinutes = readingMinutes; }

	public Instant getFetchedAt() { return fetchedAt; }
	public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

	public String getExternalUrl() { return externalUrl; }
	public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
}
