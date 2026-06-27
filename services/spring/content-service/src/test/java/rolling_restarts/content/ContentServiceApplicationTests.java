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
	"spring.mongodb.uri=mongodb://localhost:27017/test"
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
