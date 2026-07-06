package rolling_restarts.content.model;

/**
 * Lifecycle of a source's most recent RSS fetch. A newly created source starts as
 * {@link #PENDING} while its first fetch runs asynchronously, then transitions to
 * {@link #SUCCESS} or {@link #FAILED}.
 */
public enum FetchStatus {
	PENDING,
	SUCCESS,
	FAILED
}
