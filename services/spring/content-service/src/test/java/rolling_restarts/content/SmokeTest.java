package rolling_restarts.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mongodb.MongoDBContainer;

import rolling_restarts.content.model.Source;
import rolling_restarts.content.repository.SourceRepository;
import rolling_restarts.content.service.RssFetcherService;

@Tag("smoke")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ImportTestcontainers(SmokeTest.Containers.class)
class SmokeTest {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

	interface Containers {
		@ServiceConnection
		MongoDBContainer MONGO = new MongoDBContainer("mongo:8");
	}

	@MockitoBean
	JwtDecoder jwtDecoder;

	@Autowired
	TestRestTemplate rest;

	@Autowired
	SourceRepository sourceRepository;

	@Autowired
	RssFetcherService rssFetcherService;

	@Test
	void healthEndpointReturnsUp() {
		var response = rest.exchange("/actuator/health", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("status", "UP");
	}

	@Test
	void sourcesEndpoint_returnsEmptyList() {
		var response = rest.getForEntity("/sources", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("[]");
	}

	@Test
	void topicsEndpoint_returnsEmptyList() {
		var response = rest.getForEntity("/topics", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("[]");
	}

	@Test
	void articlesEndpoint_returnsPagedResponse() {
		var response = rest.exchange("/articles", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
				.containsKey("content")
				.containsEntry("totalElements", 0);
	}

	@Test
	void articlesEndpoint_nonExistentId_returns404() {
		var response = rest.exchange("/articles/nonexistent", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void sourcesEndpoint_nonExistentId_returns404() {
		var response = rest.exchange("/sources/nonexistent", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@SuppressWarnings("unchecked")
	void rssSourceLifecycle_fetchAndListArticles() {
		Source source = new Source();
		source.setName("Süddeutsche Zeitung");
		source.setRssUrl("https://rss.sueddeutsche.de/alles");
		source.setActive(true);
		source = sourceRepository.save(source);
		String sourceId = source.getId();

		rssFetcherService.fetchAllActiveSources();

		var sourcesResponse = rest.getForEntity("/sources", String.class);
		assertThat(sourcesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(sourcesResponse.getBody()).contains("Süddeutsche Zeitung");

		var articlesResponse = rest.exchange("/articles", HttpMethod.GET, null, MAP_TYPE);
		assertThat(articlesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		int totalElements = (int) articlesResponse.getBody().get("totalElements");
		assertThat(totalElements).isGreaterThan(0);
		List<Map<String, Object>> content = (List<Map<String, Object>>) articlesResponse.getBody().get("content");
		assertThat(content).isNotEmpty();
		assertThat(content.get(0)).containsKey("headline");
		assertThat(content.get(0)).containsEntry("sourceId", sourceId);
	}
}
