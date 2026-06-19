package rolling_restarts.user.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import rolling_restarts.user.model.User;
import rolling_restarts.user.model.UserSettings;
import rolling_restarts.user.service.UserService;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User profile and settings management")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	@Operation(
			summary = "Get current user profile",
			responses = {
					@ApiResponse(responseCode = "200", description = "User profile"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		User user = userService.findById(userId);
		return ResponseEntity.ok(UserProfileResponse.from(user));
	}

	@PutMapping("/me")
	@Operation(
			summary = "Update current user profile",
			responses = {
					@ApiResponse(responseCode = "200", description = "Updated profile"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public ResponseEntity<UserProfileResponse> updateProfile(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateProfileRequest request) {
		UUID userId = UUID.fromString(jwt.getSubject());
		User user = userService.updateProfile(userId, request.name(), request.email());
		return ResponseEntity.ok(UserProfileResponse.from(user));
	}

	@GetMapping("/me/settings")
	@Operation(
			summary = "Get current user settings",
			description = "Returns user preferences: selected topics, enabled sources, and saved article IDs",
			responses = {
					@ApiResponse(responseCode = "200", description = "User settings"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public ResponseEntity<UserSettingsResponse> getSettings(@AuthenticationPrincipal Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		UserSettings settings = userService.getSettings(userId);
		return ResponseEntity.ok(UserSettingsResponse.from(settings));
	}

	@PutMapping("/me/settings")
	@Operation(
			summary = "Update current user settings",
			responses = {
					@ApiResponse(responseCode = "200", description = "Updated settings"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public ResponseEntity<UserSettingsResponse> updateSettings(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UserSettingsRequest request) {
		UUID userId = UUID.fromString(jwt.getSubject());
		UserSettings settings = new UserSettings();
		settings.setUserId(userId);
		settings.setSelectedTopicIds(request.selectedTopicIds());
		settings.setEnabledSourceIds(request.enabledSourceIds());
		settings.setSavedArticleIds(request.savedArticleIds());
		UserSettings updated = userService.updateSettings(userId, settings);
		return ResponseEntity.ok(UserSettingsResponse.from(updated));
	}

	public record UpdateProfileRequest(String name, @Email String email) {}

	public record UserProfileResponse(String id, String username, String email, String name, String avatarInitials) {
		static UserProfileResponse from(User user) {
			return new UserProfileResponse(
					user.getId().toString(),
					user.getUsername(),
					user.getEmail(),
					user.getName(),
					user.getAvatarInitials());
		}
	}

	public record UserSettingsRequest(
			List<String> selectedTopicIds,
			List<String> enabledSourceIds,
			List<String> savedArticleIds) {}

	public record UserSettingsResponse(
			List<String> selectedTopicIds,
			List<String> enabledSourceIds,
			List<String> savedArticleIds) {
		static UserSettingsResponse from(UserSettings settings) {
			return new UserSettingsResponse(
					settings.getSelectedTopicIds(),
					settings.getEnabledSourceIds(),
					settings.getSavedArticleIds());
		}
	}
}
