package rolling_restarts.user.model;

import java.util.List;

public class UserSettings {

	private List<String> selectedTopicIds;

	private List<String> enabledSourceIds;

	private List<String> savedArticleIds;

	public List<String> getSelectedTopicIds() { return selectedTopicIds; }
	public void setSelectedTopicIds(List<String> selectedTopicIds) { this.selectedTopicIds = selectedTopicIds; }

	public List<String> getEnabledSourceIds() { return enabledSourceIds; }
	public void setEnabledSourceIds(List<String> enabledSourceIds) { this.enabledSourceIds = enabledSourceIds; }

	public List<String> getSavedArticleIds() { return savedArticleIds; }
	public void setSavedArticleIds(List<String> savedArticleIds) { this.savedArticleIds = savedArticleIds; }
}
