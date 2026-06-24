package rolling_restarts.user;

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

import rolling_restarts.user.repository.UserRepository;

/**
 * Exports the OpenAPI document that springdoc derives from this service's controllers to
 * build/openapi/user-service.json. Runs as part of the normal test task with MongoDB
 * auto-configuration excluded (no live database), so the spec implemented by the code is
 * always regenerated and CI can publish it as an artifact. Security filters are disabled so
 * the api-docs endpoint is reachable regardless of the resource-server config.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
	"spring.autoconfigure.exclude=org.springframework.boot.mongo.autoconfigure.MongoAutoConfiguration,org.springframework.boot.mongo.autoconfigure.MongoReactiveAutoConfiguration",
	"spring.mongodb.uri=mongodb://localhost:27017/test"
})
class OpenApiDocGenerationTest {

	private static final String API_DOCS_PATH = "/v3/api-docs";
	private static final String OUTPUT_FILE = "user-service.json";

	@MockitoBean
	UserRepository userRepository;

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
