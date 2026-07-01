package rolling_restarts.user.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls content-service to keep a source's shared subscriber count in sync when a user subscribes
 * or unsubscribes.
 *
 * <p>These calls authenticate with a dedicated {@code client_credentials} service token (scope
 * {@code source.write}), NOT the end user's forwarded JWT. content-service gates the
 * subscribe/unsubscribe endpoints on that service scope, so an ordinary user token cannot mutate
 * the shared subscriber count (or delete a source) by hitting content-service directly.
 *
 * <p>Best-effort: a failure here is logged but does not fail the user's settings change. The
 * subscriber count is an operational cleanup signal, not a correctness-critical value.
 */
@Component
public class ContentServiceClient {

	private static final Logger log = LoggerFactory.getLogger(ContentServiceClient.class);

	/** Must match the registration id in spring.security.oauth2.client.registration.*. */
	private static final String REGISTRATION_ID = "content-service";

	private final RestClient restClient;
	private final OAuth2AuthorizedClientManager authorizedClientManager;

	public ContentServiceClient(
			@Value("${content-service.base-url}") String baseUrl,
			OAuth2AuthorizedClientManager authorizedClientManager) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
		this.authorizedClientManager = authorizedClientManager;
	}

	public void subscribe(String sourceId) {
		call(sourceId, "subscribe");
	}

	public void unsubscribe(String sourceId) {
		call(sourceId, "unsubscribe");
	}

	private void call(String sourceId, String action) {
		try {
			restClient.post()
					.uri("/sources/{id}/{action}", sourceId, action)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + fetchServiceToken())
					.retrieve()
					.toBodilessEntity();
		} catch (Exception e) {
			log.warn("Failed to {} source {} in content-service: {}", action, sourceId, e.getMessage());
		}
	}

	private String fetchServiceToken() {
		OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
				.withClientRegistrationId(REGISTRATION_ID)
				.principal(REGISTRATION_ID)
				.build();
		OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
		if (client == null) {
			throw new IllegalStateException("Could not obtain a service access token for content-service");
		}
		return client.getAccessToken().getTokenValue();
	}
}
