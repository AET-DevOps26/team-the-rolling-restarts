package rolling_restarts.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import rolling_restarts.user.model.User;
import rolling_restarts.user.model.UserSettings;
import rolling_restarts.user.repository.UserRepository;
import rolling_restarts.user.repository.UserSettingsRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserSettingsRepository settingsRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	@Test
	void register_createsUserAndSettings() {
		when(userRepository.existsByUsername("alice")).thenReturn(false);
		when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("encoded");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User u = invocation.getArgument(0);
			u.setId(UUID.randomUUID());
			return u;
		});
		when(settingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User result = userService.register("alice", "alice@example.com", "password123", "Alice Smith");

		assertEquals("alice", result.getUsername());
		assertEquals("alice@example.com", result.getEmail());
		assertEquals("AS", result.getAvatarInitials());
		verify(settingsRepository).save(any(UserSettings.class));
	}

	@Test
	void register_duplicateUsername_throws() {
		when(userRepository.existsByUsername("taken")).thenReturn(true);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> userService.register("taken", "new@example.com", "password123", "Name"));
		assertEquals("Username already taken", ex.getMessage());
	}

	@Test
	void register_duplicateEmail_throws() {
		when(userRepository.existsByUsername("newuser")).thenReturn(false);
		when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> userService.register("newuser", "taken@example.com", "password123", "Name"));
		assertEquals("Email already registered", ex.getMessage());
	}

	@Test
	void findById_existingUser_returnsUser() {
		UUID id = UUID.randomUUID();
		User user = new User();
		user.setId(id);
		user.setUsername("alice");
		when(userRepository.findById(id)).thenReturn(Optional.of(user));

		User result = userService.findById(id);
		assertEquals("alice", result.getUsername());
	}

	@Test
	void findById_missingUser_throws() {
		UUID id = UUID.randomUUID();
		when(userRepository.findById(id)).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class, () -> userService.findById(id));
	}

	@Test
	void updateProfile_updatesNameAndEmail() {
		UUID id = UUID.randomUUID();
		User user = new User();
		user.setId(id);
		user.setUsername("alice");
		user.setName("Old Name");
		user.setEmail("old@example.com");
		when(userRepository.findById(id)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User result = userService.updateProfile(id, "New Name", "new@example.com");
		assertEquals("New Name", result.getName());
		assertEquals("new@example.com", result.getEmail());
	}

	@Test
	void getSettings_noExisting_returnsEmpty() {
		UUID id = UUID.randomUUID();
		when(settingsRepository.findById(id)).thenReturn(Optional.empty());

		UserSettings result = userService.getSettings(id);
		assertNotNull(result);
		assertEquals(List.of(), result.getSelectedTopicIds());
	}
}
