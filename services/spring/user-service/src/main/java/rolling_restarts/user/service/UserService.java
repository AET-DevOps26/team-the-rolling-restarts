package rolling_restarts.user.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import rolling_restarts.user.model.User;
import rolling_restarts.user.model.UserSettings;
import rolling_restarts.user.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	// Pre-computed hash of a throwaway password. When a username does not exist we still run a
	// password comparison against this hash so authentication takes a similar amount of time
	// whether or not the user exists, avoiding a username-enumeration timing oracle.
	private final String dummyPasswordHash;

	public UserService(UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-constant-time-auth");
	}

	public User register(String username, String email, String password, String name) {
		if (userRepository.existsByUsername(username)) {
			throw new IllegalArgumentException("Username already taken");
		}
		if (userRepository.existsByEmail(email)) {
			throw new IllegalArgumentException("Email already registered");
		}

		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setName(name);
		user.setAvatarInitials(computeInitials(name));

		UserSettings settings = new UserSettings();
		settings.setSelectedTopicIds(List.of());
		settings.setEnabledSourceIds(List.of());
		settings.setSavedArticleIds(List.of());
		user.setSettings(settings);

		return userRepository.save(user);
	}

	public Optional<User> authenticate(String username, String password) {
		Optional<User> user = userRepository.findByUsername(username);
		if (user.isEmpty()) {
			// Run a comparison against a dummy hash so timing does not reveal whether the
			// username exists, then fail.
			passwordEncoder.matches(password, dummyPasswordHash);
			return Optional.empty();
		}
		return user.filter(u -> passwordEncoder.matches(password, u.getPasswordHash()));
	}

	public User findById(String id) {
		return userRepository.findById(id).orElseThrow(() ->
				new IllegalArgumentException("User not found"));
	}

	public User findByUsername(String username) {
		return userRepository.findByUsername(username).orElseThrow(() ->
				new IllegalArgumentException("User not found"));
	}

	public User updateProfile(String userId, String name, String email) {
		User user = findById(userId);
		if (name != null) {
			user.setName(name);
			user.setAvatarInitials(computeInitials(name));
		}
		if (email != null && !email.equals(user.getEmail())) {
			if (userRepository.existsByEmail(email)) {
				throw new IllegalArgumentException("Email already registered");
			}
			user.setEmail(email);
		}
		return userRepository.save(user);
	}

	private static String computeInitials(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		if (trimmed.length() < 2) {
			return null;
		}
		String[] parts = trimmed.split("\\s+", 2);
		return parts.length > 1
				? ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase()
				: trimmed.substring(0, 2).toUpperCase();
	}

	public UserSettings getSettings(String userId) {
		User user = findById(userId);
		UserSettings settings = user.getSettings();
		if (settings == null) {
			settings = new UserSettings();
			settings.setSelectedTopicIds(List.of());
			settings.setEnabledSourceIds(List.of());
			settings.setSavedArticleIds(List.of());
		}
		return settings;
	}

	public UserSettings updateSettings(String userId, UserSettings updated) {
		User user = findById(userId);
		UserSettings settings = user.getSettings();
		if (settings == null) {
			settings = new UserSettings();
		}
		settings.setSelectedTopicIds(updated.getSelectedTopicIds());
		// enabledSourceIds is intentionally NOT updated here: subscriptions are managed only via
		// subscribe/unsubscribe so the shared subscriber count in content-service stays authoritative.
		settings.setSavedArticleIds(updated.getSavedArticleIds());
		user.setSettings(settings);
		userRepository.save(user);
		return settings;
	}

	/**
	 * Adds {@code sourceId} to the user's enabled sources.
	 *
	 * @return {@code true} if it was newly added, {@code false} if already subscribed.
	 */
	public boolean addSubscription(String userId, String sourceId) {
		User user = findById(userId);
		UserSettings settings = ensureSettings(user);
		List<String> ids = new ArrayList<>(settings.getEnabledSourceIds());
		if (ids.contains(sourceId)) {
			return false;
		}
		ids.add(sourceId);
		settings.setEnabledSourceIds(ids);
		user.setSettings(settings);
		userRepository.save(user);
		return true;
	}

	/**
	 * Removes {@code sourceId} from the user's enabled sources.
	 *
	 * @return {@code true} if it was present and removed, {@code false} otherwise.
	 */
	public boolean removeSubscription(String userId, String sourceId) {
		User user = findById(userId);
		UserSettings settings = ensureSettings(user);
		List<String> ids = new ArrayList<>(settings.getEnabledSourceIds());
		if (!ids.remove(sourceId)) {
			return false;
		}
		settings.setEnabledSourceIds(ids);
		user.setSettings(settings);
		userRepository.save(user);
		return true;
	}

	private static UserSettings ensureSettings(User user) {
		UserSettings settings = user.getSettings();
		if (settings == null) {
			settings = new UserSettings();
			settings.setSelectedTopicIds(List.of());
			settings.setEnabledSourceIds(List.of());
			settings.setSavedArticleIds(List.of());
			user.setSettings(settings);
		}
		if (settings.getEnabledSourceIds() == null) {
			settings.setEnabledSourceIds(List.of());
		}
		return settings;
	}
}
