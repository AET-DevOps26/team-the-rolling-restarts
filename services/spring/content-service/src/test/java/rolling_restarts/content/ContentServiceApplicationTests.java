package rolling_restarts.content;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import rolling_restarts.content.repository.ArticleRepository;
import rolling_restarts.content.repository.SourceRepository;
import rolling_restarts.content.repository.TopicRepository;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.mongodb.uri=mongodb://localhost:27017/test",
	// Repositories are mocked and no MongoDB runs here; keep index auto-creation off so the
	// context doesn't eagerly connect to Mongo (which would time out) just to build indexes.
	"spring.data.mongodb.auto-index-creation=false"
})
class ContentServiceApplicationTests {

	@MockitoBean
	ArticleRepository articleRepository;

	@MockitoBean
	SourceRepository sourceRepository;

	@MockitoBean
	TopicRepository topicRepository;

	@Test
	void contextLoads() {
	}
}
