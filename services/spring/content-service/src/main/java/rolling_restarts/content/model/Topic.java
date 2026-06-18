package rolling_restarts.content.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "topics")
public class Topic {

	@Id
	private String id;

	private String name;

	private String color;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getColor() { return color; }
	public void setColor(String color) { this.color = color; }
}
