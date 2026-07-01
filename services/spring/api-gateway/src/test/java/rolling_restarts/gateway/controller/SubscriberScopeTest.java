package rolling_restarts.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import rolling_restarts.gateway.config.SecurityConfig;

@WebMvcTest(RootController.class)
@Import(SecurityConfig.class)
class SubscriberScopeTest {

	@MockitoBean
	JwtDecoder jwtDecoder;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void subscribe_unauthenticated_returns401() throws Exception {
		mockMvc.perform(post("/api/content/sources/abc123/subscribe").with(csrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	void subscribe_withoutServiceScope_returns403() throws Exception {
		mockMvc.perform(post("/api/content/sources/abc123/subscribe").with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "SCOPE_source.write")
	void subscribe_withServiceScope_isNotForbidden() throws Exception {
		// With the correct scope the gateway should not block — it may 404 (no route handler in
		// test context) but must not 403.
		int status = mockMvc.perform(post("/api/content/sources/abc123/subscribe").with(csrf()))
				.andReturn().getResponse().getStatus();
		assertThat(status).as("Expected the request to pass the scope gate").isNotEqualTo(403);
	}

	@Test
	void unsubscribe_unauthenticated_returns401() throws Exception {
		mockMvc.perform(post("/api/content/sources/abc123/unsubscribe").with(csrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	void unsubscribe_withoutServiceScope_returns403() throws Exception {
		mockMvc.perform(post("/api/content/sources/abc123/unsubscribe").with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "SCOPE_source.write")
	void unsubscribe_withServiceScope_isNotForbidden() throws Exception {
		int status = mockMvc.perform(post("/api/content/sources/abc123/unsubscribe").with(csrf()))
				.andReturn().getResponse().getStatus();
		assertThat(status).as("Expected the request to pass the scope gate").isNotEqualTo(403);
	}
}
