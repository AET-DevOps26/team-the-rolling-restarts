package rolling_restarts.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import rolling_restarts.user.repository.UserRepository;
import rolling_restarts.user.repository.UserSettingsRepository;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
class UserServiceApplicationTests {

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	UserSettingsRepository userSettingsRepository;

	@Test
	void contextLoads() {
	}
}
