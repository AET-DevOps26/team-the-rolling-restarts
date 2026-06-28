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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("smoke")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SmokeTest {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

	@MockitoBean
	JwtDecoder jwtDecoder;

	@Autowired
	TestRestTemplate rest;

	@Test
	void healthEndpointReturnsUp() {
		var response = rest.exchange("/actuator/health", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("status", "UP");
	}

	@Test
	void rootEndpointReturnsHelloWorld() {
		var response = rest.exchange("/", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("message", "Hello, World!");
	}

	@Test
	void testEndpointReturnsMessage() {
		var response = rest.exchange("/test", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("message", "Hello, World!\nTest!");
	}

	@Test
	void unauthenticatedRequestToProtectedEndpoint_returns401() {
		var response = rest.exchange("/api/users/users/me", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
