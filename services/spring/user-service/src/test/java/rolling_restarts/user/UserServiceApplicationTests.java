package rolling_restarts.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import rolling_restarts.user.repository.UserRepository;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.autoconfigure.exclude=org.springframework.boot.mongo.autoconfigure.MongoAutoConfiguration,org.springframework.boot.mongo.autoconfigure.MongoReactiveAutoConfiguration",
	"spring.mongodb.uri=mongodb://localhost:27017/test",
	"service.client.secret=test-secret"
})
class UserServiceApplicationTests {

	@MockitoBean
	UserRepository userRepository;

	@Test
	void contextLoads() {
	}
}
