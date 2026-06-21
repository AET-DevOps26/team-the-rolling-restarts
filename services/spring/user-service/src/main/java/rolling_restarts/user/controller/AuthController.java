package rolling_restarts.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import rolling_restarts.user.model.User;
import rolling_restarts.user.service.UserService;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User registration and OAuth2 token endpoints")
public class AuthController {

	private final UserService userService;

	public AuthController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping("/register")
	@Operation(
			summary = "Register a new user",
			description = "Creates a new user account. The OAuth2 token endpoint at /oauth2/token handles login.",
			responses = {
					@ApiResponse(responseCode = "201", description = "User created"),
					@ApiResponse(responseCode = "400", description = "Validation error or duplicate username/email")
			})
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = userService.register(
				request.username(),
				request.email(),
				request.password(),
				request.name());
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
	}

	public record RegisterRequest(
			@NotBlank @Size(min = 3, max = 50) String username,
			@NotBlank @Email String email,
			@NotBlank @Size(min = 8) String password,
			String name) {}

	public record UserResponse(String id, String username, String email, String name, String avatarInitials) {
		static UserResponse from(User user) {
			return new UserResponse(
					user.getId(),
					user.getUsername(),
					user.getEmail(),
					user.getName(),
					user.getAvatarInitials());
		}
	}
}
