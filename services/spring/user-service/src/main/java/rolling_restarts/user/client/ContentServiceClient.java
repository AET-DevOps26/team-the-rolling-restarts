package rolling_restarts.user.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls content-service to keep a source's shared subscriber count in sync when a user subscribes
 * or unsubscribes. The caller's JWT is forwarded so content-service authorises the request with
 * its existing resource-server configuration (no separate service credential needed).
 *
 * <p>Best-effort: a failure here is logged but does not fail the user's settings change. The
 * subscriber count is an operational cleanup signal, not a correctness-critical value.
 */
@Component
public class ContentServiceClient {

	private static final Logger log = LoggerFactory.getLogger(ContentServiceClient.class);

	private final RestClient restClient;

	public ContentServiceClient(@Value("${content-service.base-url}") String baseUrl) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	public void subscribe(String sourceId, String bearerToken) {
		call(sourceId, "subscribe", bearerToken);
	}

	public void unsubscribe(String sourceId, String bearerToken) {
		call(sourceId, "unsubscribe", bearerToken);
	}

	private void call(String sourceId, String action, String bearerToken) {
		try {
			restClient.post()
					.uri("/sources/{id}/{action}", sourceId, action)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
					.retrieve()
					.toBodilessEntity();
		} catch (Exception e) {
			log.warn("Failed to {} source {} in content-service: {}", action, sourceId, e.getMessage());
		}
	}
}
