package rolling_restarts.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import rolling_restarts.content.config.SecurityConfig;
import rolling_restarts.content.model.Source;
import rolling_restarts.content.repository.SourceRepository;

@WebMvcTest(SourceController.class)
@Import(SecurityConfig.class)
class SourceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SourceRepository sourceRepository;

	@Test
	void list_returnsAllSources() throws Exception {
		Source source = new Source();
		source.setId("1");
		source.setName("TechCrunch");
		source.setRssUrl("https://techcrunch.com/feed/");
		when(sourceRepository.findAll()).thenReturn(List.of(source));

		mockMvc.perform(get("/sources"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("TechCrunch"));
	}

	@Test
	void get_existingSource_returns200() throws Exception {
		Source source = new Source();
		source.setId("1");
		source.setName("TechCrunch");
		when(sourceRepository.findById("1")).thenReturn(Optional.of(source));

		mockMvc.perform(get("/sources/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("TechCrunch"));
	}

	@Test
	void get_missingSource_returns404() throws Exception {
		when(sourceRepository.findById("999")).thenReturn(Optional.empty());

		mockMvc.perform(get("/sources/999"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	void create_validInput_returns201() throws Exception {
		when(sourceRepository.findByRssUrl("https://example.com/feed")).thenReturn(Optional.empty());
		when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> {
			Source s = invocation.getArgument(0);
			s.setId("new-id");
			return s;
		});

		mockMvc.perform(post("/sources")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Example","rssUrl":"https://example.com/feed"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Example"));
	}

	@Test
	@WithMockUser
	void create_blankName_returns400() throws Exception {
		mockMvc.perform(post("/sources")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"","rssUrl":"https://example.com/feed"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	@WithMockUser
	void create_blankUrl_returns400() throws Exception {
		mockMvc.perform(post("/sources")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Example","rssUrl":""}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	@WithMockUser
	void delete_existingSource_returns204() throws Exception {
		when(sourceRepository.existsById("1")).thenReturn(true);

		mockMvc.perform(delete("/sources/1").with(csrf()))
				.andExpect(status().isNoContent());
	}

	@Test
	@WithMockUser
	void delete_missingSource_returns404() throws Exception {
		when(sourceRepository.existsById("999")).thenReturn(false);

		mockMvc.perform(delete("/sources/999").with(csrf()))
				.andExpect(status().isNotFound());
	}
}
