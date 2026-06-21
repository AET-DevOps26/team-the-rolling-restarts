package rolling_restarts.user.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class User {

	@Id
	private String id;

	@Indexed(unique = true)
	private String username;

	@Indexed(unique = true)
	private String email;

	private String passwordHash;

	private String name;

	private String avatarInitials;

	private Instant createdAt = Instant.now();

	private UserSettings settings;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getAvatarInitials() { return avatarInitials; }
	public void setAvatarInitials(String avatarInitials) { this.avatarInitials = avatarInitials; }

	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

	public UserSettings getSettings() { return settings; }
	public void setSettings(UserSettings settings) { this.settings = settings; }
}
