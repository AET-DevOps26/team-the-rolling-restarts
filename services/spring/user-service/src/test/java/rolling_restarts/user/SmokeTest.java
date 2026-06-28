package rolling_restarts.user;

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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;

@Tag("smoke")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ImportTestcontainers(SmokeTest.Containers.class)
@TestPropertySource(properties = "service.client.secret=test-secret")
class SmokeTest {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

	interface Containers {
		@ServiceConnection
		MongoDBContainer MONGO = new MongoDBContainer("mongo:8");
	}

	@Autowired
	TestRestTemplate rest;

	@Test
	void healthEndpointReturnsUp() {
		var response = rest.exchange("/actuator/health", HttpMethod.GET, null, MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("status", "UP");
	}

	@Test
	void registerWithValidBody_returns201() {
		var body = Map.of(
				"username", "smokeuser",
				"email", "smoke@example.com",
				"password", "password123",
				"name", "Smoke User");

		var response = rest.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody())
				.containsEntry("username", "smokeuser")
				.containsEntry("email", "smoke@example.com")
				.containsKey("id");
	}

	@Test
	void registerDuplicateUsername_returns409() {
		var body = Map.of(
				"username", "dupuser",
				"email", "dup1@example.com",
				"password", "password123",
				"name", "Dup User");
		rest.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE);

		var duplicate = Map.of(
				"username", "dupuser",
				"email", "dup2@example.com",
				"password", "password123",
				"name", "Dup User 2");
		var response = rest.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(duplicate), MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).containsEntry("code", 409);
	}

	@Test
	void registerWithInvalidBody_returns400() {
		var body = Map.of(
				"username", "",
				"email", "bad",
				"password", "short",
				"name", "X");

		var response = rest.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody())
				.containsEntry("code", 400)
				.containsEntry("message", "Validation failed");
	}

	@Test
	void registerWithMissingFields_returns400() {
		var body = Map.of("username", "onlyuser");

		var response = rest.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void loginAfterRegister_returns200WithToken() {
		var registerBody = Map.of(
				"username", "loginuser",
				"email", "login@example.com",
				"password", "password123",
				"name", "Login User");
		rest.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(registerBody), MAP_TYPE);

		var loginBody = Map.of("username", "loginuser", "password", "password123");
		var response = rest.exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(loginBody), MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsKey("token");
	}

	@Test
	void loginWithWrongPassword_returns401() {
		var registerBody = Map.of(
				"username", "badpwuser",
				"email", "badpw@example.com",
				"password", "password123",
				"name", "Bad PW User");
		rest.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(registerBody), MAP_TYPE);

		var loginBody = Map.of("username", "badpwuser", "password", "wrongpassword");
		var response = rest.exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(loginBody), MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginWithNonexistentUser_returns401() {
		var loginBody = Map.of("username", "noonehere", "password", "password123");
		var response = rest.exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(loginBody), MAP_TYPE);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
