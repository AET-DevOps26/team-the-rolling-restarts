package rolling_restarts.gateway.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import rolling_restarts.gateway.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RootController.class)
@Import(SecurityConfig.class)
public class RootControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rootReturnsHelloWorld() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.message").value("Hello, World!"));
	}

	@ParameterizedTest
	@MethodSource("messageEndpoints")
	void messageEndpointsReturnExpectedPayload(String path, String expectedMessage) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.message").value(expectedMessage));
	}

	static Stream<Arguments> messageEndpoints() {
		return Stream.of(
				Arguments.of("/test", "Hello, World!\nTest!"),
				Arguments.of("/dummy", "Dummy response"));
	}
}
