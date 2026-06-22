package rolling_restarts.user.controller;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
	private final JwtEncoder jwtEncoder;
	private final String jwtIssuer;

	public AuthController(UserService userService, JwtEncoder jwtEncoder,
			@Value("${jwt.issuer}") String jwtIssuer) {
		this.userService = userService;
		this.jwtEncoder = jwtEncoder;
		this.jwtIssuer = jwtIssuer;
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

	@PostMapping("/login")
	@Operation(
			summary = "Authenticate and obtain a JWT",
			responses = {
					@ApiResponse(responseCode = "200", description = "JWT token"),
					@ApiResponse(responseCode = "401", description = "Invalid credentials")
			})
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return userService.authenticate(request.username(), request.password())
				.map(user -> {
					Instant now = Instant.now();
					JwtClaimsSet claims = JwtClaimsSet.builder()
							.issuer(jwtIssuer)
							.subject(user.getId())
							.claim("username", user.getUsername())
							.issuedAt(now)
							.expiresAt(now.plusSeconds(3600))
							.build();
					String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
					return ResponseEntity.ok(new TokenResponse(token));
				})
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	public record LoginRequest(
			@NotBlank String username,
			@NotBlank String password) {}

	public record TokenResponse(String token) {}

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
