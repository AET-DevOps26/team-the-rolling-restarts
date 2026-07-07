package rolling_restarts.content;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import rolling_restarts.content.repository.ArticleRepository;
import rolling_restarts.content.repository.SourceRepository;
import rolling_restarts.content.repository.TopicRepository;

/**
 * Exports the OpenAPI document that springdoc derives from this service's controllers to
 * build/openapi/content-service.json. Runs as part of the normal test task (repositories
 * mocked, no live MongoDB), so the spec implemented by the code is always regenerated and CI
 * can publish it as an artifact. Security filters are disabled so the api-docs endpoint is
 * reachable regardless of the resource-server config.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
	"spring.mongodb.uri=mongodb://localhost:27017/test",
	// Repositories are mocked and no MongoDB runs here; keep index auto-creation off so the
	// context doesn't eagerly connect to Mongo (which would time out) just to build indexes.
	"spring.data.mongodb.auto-index-creation=false"
})
class OpenApiDocGenerationTest {

	private static final String API_DOCS_PATH = "/v3/api-docs";
	private static final String OUTPUT_FILE = "content-service.json";

	@MockitoBean
	ArticleRepository articleRepository;

	@MockitoBean
	SourceRepository sourceRepository;

	@MockitoBean
	TopicRepository topicRepository;

	@Autowired
	MockMvc mockMvc;

	@Test
	void exportsOpenApiDoc() throws Exception {
		String json = mockMvc.perform(get(API_DOCS_PATH))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		ObjectMapper mapper = new ObjectMapper();
		Object tree = mapper.readValue(json, Object.class);
		Path output = Path.of("build", "openapi");
		Files.createDirectories(output);
		mapper.writerWithDefaultPrettyPrinter()
				.writeValue(output.resolve(OUTPUT_FILE).toFile(), tree);
	}
}
