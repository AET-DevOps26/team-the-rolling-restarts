package rolling_restarts.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;

import rolling_restarts.user.model.User;
import rolling_restarts.user.model.UserSettings;
import rolling_restarts.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	@Test
	void register_createsUserWithEmbeddedSettings() {
		when(userRepository.existsByUsername("alice")).thenReturn(false);
		when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("encoded");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User u = invocation.getArgument(0);
			u.setId("507f1f77bcf86cd799439011");
			return u;
		});

		User result = userService.register("alice", "alice@example.com", "password123", "Alice Smith");

		assertEquals("alice", result.getUsername());
		assertEquals("alice@example.com", result.getEmail());
		assertEquals("AS", result.getAvatarInitials());
		assertNotNull(result.getSettings());
		assertEquals(List.of(), result.getSettings().getSelectedTopicIds());
		verify(userRepository).save(any(User.class));
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
		String id = "507f1f77bcf86cd799439011";
		User user = new User();
		user.setId(id);
		user.setUsername("alice");
		when(userRepository.findById(id)).thenReturn(Optional.of(user));

		User result = userService.findById(id);
		assertEquals("alice", result.getUsername());
	}

	@Test
	void findById_missingUser_throws() {
		String id = "507f1f77bcf86cd799439011";
		when(userRepository.findById(id)).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class, () -> userService.findById(id));
	}

	@Test
	void updateProfile_updatesNameAndEmail() {
		String id = "507f1f77bcf86cd799439011";
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
		String id = "507f1f77bcf86cd799439011";
		User user = new User();
		user.setId(id);
		when(userRepository.findById(id)).thenReturn(Optional.of(user));

		UserSettings result = userService.getSettings(id);
		assertNotNull(result);
		assertEquals(List.of(), result.getSelectedTopicIds());
	}

	@Test
	void addSubscription_newSource_addsAndReturnsTrue() {
		String id = "507f1f77bcf86cd799439011";
		User user = userWithEnabledSources(id, List.of("existing"));
		when(userRepository.findById(id)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		boolean added = userService.addSubscription(id, "src-1");

		assertEquals(true, added);
		assertEquals(List.of("existing", "src-1"), user.getSettings().getEnabledSourceIds());
	}

	@Test
	void addSubscription_alreadySubscribed_returnsFalse() {
		String id = "507f1f77bcf86cd799439011";
		User user = userWithEnabledSources(id, List.of("src-1"));
		when(userRepository.findById(id)).thenReturn(Optional.of(user));

		boolean added = userService.addSubscription(id, "src-1");

		assertEquals(false, added);
		verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
	}

	@Test
	void removeSubscription_subscribed_removesAndReturnsTrue() {
		String id = "507f1f77bcf86cd799439011";
		User user = userWithEnabledSources(id, List.of("src-1", "src-2"));
		when(userRepository.findById(id)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		boolean removed = userService.removeSubscription(id, "src-1");

		assertEquals(true, removed);
		assertEquals(List.of("src-2"), user.getSettings().getEnabledSourceIds());
	}

	@Test
	void removeSubscription_notSubscribed_returnsFalse() {
		String id = "507f1f77bcf86cd799439011";
		User user = userWithEnabledSources(id, List.of("src-2"));
		when(userRepository.findById(id)).thenReturn(Optional.of(user));

		boolean removed = userService.removeSubscription(id, "src-1");

		assertEquals(false, removed);
		verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
	}

	@Test
	void addSavedArticle_addsAtomicallyAndReturnsSettings() {
		String id = "507f1f77bcf86cd799439011";
		User user = userWithSavedArticles(id, List.of("article-1"));
		when(mongoTemplate.findAndModify(
				any(Query.class),
				any(Update.class),
				any(FindAndModifyOptions.class),
				eq(User.class))).thenReturn(user);

		UserSettings result = userService.addSavedArticle(id, "article-1");

		assertEquals(List.of("article-1"), result.getSavedArticleIds());
		ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
		verify(mongoTemplate).findAndModify(
				any(Query.class),
				updateCaptor.capture(),
				any(FindAndModifyOptions.class),
				eq(User.class));
		assertEquals(
				"{ \"$addToSet\" : { \"settings.savedArticleIds\" : \"article-1\"}}",
				updateCaptor.getValue().getUpdateObject().toString());
	}

	@Test
	void removeSavedArticle_removesAtomicallyAndReturnsSettings() {
		String id = "507f1f77bcf86cd799439011";
		User user = userWithSavedArticles(id, List.of());
		when(mongoTemplate.findAndModify(
				any(Query.class),
				any(Update.class),
				any(FindAndModifyOptions.class),
				eq(User.class))).thenReturn(user);

		UserSettings result = userService.removeSavedArticle(id, "article-1");

		assertEquals(List.of(), result.getSavedArticleIds());
		ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
		verify(mongoTemplate).findAndModify(
				any(Query.class),
				updateCaptor.capture(),
				any(FindAndModifyOptions.class),
				eq(User.class));
		assertEquals(
				"{ \"$pull\" : { \"settings.savedArticleIds\" : \"article-1\"}}",
				updateCaptor.getValue().getUpdateObject().toString());
	}

	@Test
	void addSavedArticle_missingUser_throws() {
		String id = "507f1f77bcf86cd799439011";
		when(mongoTemplate.findAndModify(
				any(Query.class),
				any(Update.class),
				any(FindAndModifyOptions.class),
				eq(User.class))).thenReturn(null);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> userService.addSavedArticle(id, "article-1"));
		assertEquals("User not found", ex.getMessage());
	}

	private static User userWithEnabledSources(String id, List<String> enabledSourceIds) {
		User user = new User();
		user.setId(id);
		UserSettings settings = new UserSettings();
		settings.setSelectedTopicIds(List.of());
		settings.setEnabledSourceIds(enabledSourceIds);
		settings.setSavedArticleIds(List.of());
		user.setSettings(settings);
		return user;
	}

	private static User userWithSavedArticles(String id, List<String> savedArticleIds) {
		User user = new User();
		user.setId(id);
		UserSettings settings = new UserSettings();
		settings.setSelectedTopicIds(List.of());
		settings.setEnabledSourceIds(List.of());
		settings.setSavedArticleIds(savedArticleIds);
		user.setSettings(settings);
		return user;
	}
}
