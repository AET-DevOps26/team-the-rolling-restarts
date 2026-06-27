package rolling_restarts.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exports the gateway's own OpenAPI document (springdoc serves it at the default path) to
 * build/openapi/api-gateway.json as part of the normal test task. Security filters are
 * disabled so the api-docs endpoint is reachable. The aggregated Swagger UI that fans out to
 * the user/content service specs is configured separately in application.properties.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiDocGenerationTest {

	private static final String API_DOCS_PATH = "/v3/api-docs";
	private static final String OUTPUT_FILE = "api-gateway.json";

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
