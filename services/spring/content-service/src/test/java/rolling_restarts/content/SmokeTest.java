package rolling_restarts.content;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.MongoDBContainer;

@Tag("smoke")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ImportTestcontainers(SmokeTest.Containers.class)
class SmokeTest {

	interface Containers {
		@ServiceConnection
		MongoDBContainer MONGO = new MongoDBContainer("mongo:8");
	}

	@Autowired
	TestRestTemplate rest;

	@Test
	void healthEndpointReturnsUp() {
		var response = rest.getForEntity("/actuator/health", Map.class);
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
		var response = rest.getForEntity("/articles", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
				.containsKey("content")
				.containsEntry("totalElements", 0);
	}

	@Test
	void articlesEndpoint_nonExistentId_returns404() {
		var response = rest.getForEntity("/articles/nonexistent", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void sourcesEndpoint_nonExistentId_returns404() {
		var response = rest.getForEntity("/sources/nonexistent", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
