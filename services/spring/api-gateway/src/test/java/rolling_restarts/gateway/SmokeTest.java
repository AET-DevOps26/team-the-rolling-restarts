package rolling_restarts.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("smoke")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SmokeTest {

	@MockitoBean
	JwtDecoder jwtDecoder;

	@Autowired
	TestRestTemplate rest;

	@Test
	void healthEndpointReturnsUp() {
		var response = rest.getForEntity("/actuator/health", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("status", "UP");
	}

	@Test
	void rootEndpointReturnsHelloWorld() {
		var response = rest.getForEntity("/", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("message", "Hello, World!");
	}

	@Test
	void testEndpointReturnsMessage() {
		var response = rest.getForEntity("/test", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("message", "Hello, World!\nTest!");
	}

	@Test
	void corsHeadersPresentForAllowedOrigin() {
		var headers = new HttpHeaders();
		headers.set("Origin", "http://localhost:3000");
		headers.set("Access-Control-Request-Method", "GET");
		var request = new RequestEntity<>(headers, HttpMethod.OPTIONS, java.net.URI.create("/api/content/articles"));

		var response = rest.exchange(request, Void.class);
		assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
		assertThat(response.getHeaders().getAccessControlAllowMethods()).isNotEmpty();
	}

	@Test
	void unauthenticatedRequestToProtectedEndpoint_returns401() {
		var response = rest.getForEntity("/api/users/users/me", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
