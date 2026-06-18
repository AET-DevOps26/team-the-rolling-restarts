package rolling_restarts.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import rolling_restarts.user.model.UserSettings;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
