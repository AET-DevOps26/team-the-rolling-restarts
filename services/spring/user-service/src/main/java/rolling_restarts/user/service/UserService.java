package rolling_restarts.user.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import rolling_restarts.user.model.User;
import rolling_restarts.user.model.UserSettings;
import rolling_restarts.user.repository.UserRepository;
import rolling_restarts.user.repository.UserSettingsRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final UserSettingsRepository settingsRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository,
			UserSettingsRepository settingsRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.settingsRepository = settingsRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
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
		user = userRepository.save(user);

		UserSettings settings = new UserSettings();
		settings.setUserId(user.getId());
		settings.setSelectedTopicIds(List.of());
		settings.setEnabledSourceIds(List.of());
		settings.setSavedArticleIds(List.of());
		settingsRepository.save(settings);

		return user;
	}

	public User findById(UUID id) {
		return userRepository.findById(id).orElseThrow(() ->
				new IllegalArgumentException("User not found"));
	}

	public User findByUsername(String username) {
		return userRepository.findByUsername(username).orElseThrow(() ->
				new IllegalArgumentException("User not found"));
	}

	@Transactional
	public User updateProfile(UUID userId, String name, String email) {
		User user = findById(userId);
		if (name != null) {
			user.setName(name);
		}
		if (email != null) {
			user.setEmail(email);
		}
		return userRepository.save(user);
	}

	public UserSettings getSettings(UUID userId) {
		return settingsRepository.findById(userId).orElseGet(() -> {
			UserSettings settings = new UserSettings();
			settings.setUserId(userId);
			settings.setSelectedTopicIds(List.of());
			settings.setEnabledSourceIds(List.of());
			settings.setSavedArticleIds(List.of());
			return settings;
		});
	}

	@Transactional
	public UserSettings updateSettings(UUID userId, UserSettings updated) {
		UserSettings settings = settingsRepository.findById(userId).orElseGet(() -> {
			UserSettings s = new UserSettings();
			s.setUserId(userId);
			return s;
		});
		settings.setSelectedTopicIds(updated.getSelectedTopicIds());
		settings.setEnabledSourceIds(updated.getEnabledSourceIds());
		settings.setSavedArticleIds(updated.getSavedArticleIds());
		return settingsRepository.save(settings);
	}
}
