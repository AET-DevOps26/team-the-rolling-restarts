package rolling_restarts.user.service;

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

	public UserService(UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
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
		if (name != null && name.length() >= 2) {
			String[] parts = name.split("\\s+", 2);
			String initials = parts.length > 1
					? ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase()
					: name.substring(0, 2).toUpperCase();
			user.setAvatarInitials(initials);
		}

		UserSettings settings = new UserSettings();
		settings.setSelectedTopicIds(List.of());
		settings.setEnabledSourceIds(List.of());
		settings.setSavedArticleIds(List.of());
		user.setSettings(settings);

		return userRepository.save(user);
	}

	public Optional<User> authenticate(String username, String password) {
		return userRepository.findByUsername(username)
				.filter(user -> passwordEncoder.matches(password, user.getPasswordHash()));
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
		}
		if (email != null) {
			user.setEmail(email);
		}
		return userRepository.save(user);
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
		settings.setEnabledSourceIds(updated.getEnabledSourceIds());
		settings.setSavedArticleIds(updated.getSavedArticleIds());
		user.setSettings(settings);
		userRepository.save(user);
		return settings;
	}
}
