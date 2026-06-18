package rolling_restarts.user.model;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_settings")
public class UserSettings {

	@Id
	private UUID userId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<String> selectedTopicIds;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<String> enabledSourceIds;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<String> savedArticleIds;

	public UUID getUserId() { return userId; }
	public void setUserId(UUID userId) { this.userId = userId; }

	public List<String> getSelectedTopicIds() { return selectedTopicIds; }
	public void setSelectedTopicIds(List<String> selectedTopicIds) { this.selectedTopicIds = selectedTopicIds; }

	public List<String> getEnabledSourceIds() { return enabledSourceIds; }
	public void setEnabledSourceIds(List<String> enabledSourceIds) { this.enabledSourceIds = enabledSourceIds; }

	public List<String> getSavedArticleIds() { return savedArticleIds; }
	public void setSavedArticleIds(List<String> savedArticleIds) { this.savedArticleIds = savedArticleIds; }
}
