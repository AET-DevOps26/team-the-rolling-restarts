package rolling_restarts.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import rolling_restarts.gateway.config.SecurityConfig;

@WebMvcTest(RootController.class)
@Import(SecurityConfig.class)
class GenAiPublicAccessTest {

	@MockitoBean
	JwtDecoder jwtDecoder;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aiHealth_unauthenticated_isNotUnauthorized() throws Exception {
		int status = mockMvc.perform(get("/api/ai/health"))
				.andReturn().getResponse().getStatus();
		assertThat(status).as("Expected /api/ai/** to pass the security filter without JWT").isNotEqualTo(401);
	}

	@Test
	void aiSummarize_unauthenticated_isNotUnauthorized() throws Exception {
		int status = mockMvc.perform(post("/api/ai/summarize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"hello\"}")
				.with(csrf()))
				.andReturn().getResponse().getStatus();
		assertThat(status).as("Expected /api/ai/** to pass the security filter without JWT").isNotEqualTo(401);
	}
}
