package rolling_restarts.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import rolling_restarts.user.model.User;
import rolling_restarts.user.service.UserService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private JwtEncoder jwtEncoder;

	@Test
	void register_validInput_returns201() throws Exception {
		User user = new User();
		user.setId("507f1f77bcf86cd799439011");
		user.setUsername("testuser");
		user.setEmail("test@example.com");
		user.setName("Test User");
		user.setAvatarInitials("TU");

		when(userService.register(anyString(), anyString(), anyString(), anyString())).thenReturn(user);

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"testuser","email":"test@example.com","password":"password123","name":"Test User"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("testuser"))
				.andExpect(jsonPath("$.email").value("test@example.com"));
	}

	@Test
	void register_missingUsername_returns400() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"","email":"test@example.com","password":"password123","name":"Test"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.message").value("Validation failed"));
	}

	@Test
	void register_invalidEmail_returns400() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"testuser","email":"not-an-email","password":"password123","name":"Test"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void register_shortPassword_returns400() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"testuser","email":"test@example.com","password":"short","name":"Test"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void register_duplicateUsername_returns409() throws Exception {
		when(userService.register(anyString(), anyString(), anyString(), anyString()))
				.thenThrow(new IllegalArgumentException("Username already taken"));

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"taken","email":"new@example.com","password":"password123","name":"Test"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(409))
				.andExpect(jsonPath("$.message").value("Username already taken"));
	}
}
