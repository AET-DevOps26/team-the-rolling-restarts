package rolling_restarts.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import rolling_restarts.content.service.RssFetcherService;
import rolling_restarts.content.service.SourceService;

@WebMvcTest(SourceController.class)
@Import(SecurityConfig.class)
class SourceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SourceRepository sourceRepository;

	@MockitoBean
	private SourceService sourceService;

	@MockitoBean
	private RssFetcherService rssFetcherService;

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
	void create_bareDomainUrl_normalizesToHttpsAndReturns201() throws Exception {
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
								{"name":"Example","rssUrl":"example.com/feed"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rssUrl").value("https://example.com/feed"));
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
	void create_internalUrl_returns400() throws Exception {
		mockMvc.perform(post("/sources")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Internal","rssUrl":"http://localhost:8081/actuator/env"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser
	void create_nonHttpScheme_returns400() throws Exception {
		mockMvc.perform(post("/sources")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"FTP","rssUrl":"ftp://example.com/feed"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_unauthenticated_returns401() throws Exception {
		mockMvc.perform(post("/sources")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Example","rssUrl":"https://example.com/feed"}
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(authorities = "SCOPE_source.write")
	void subscribe_existingSource_returns200() throws Exception {
		Source source = new Source();
		source.setId("1");
		source.setName("TechCrunch");
		source.setSubscriberCount(1);
		when(sourceService.subscribe("1")).thenReturn(source);

		mockMvc.perform(post("/sources/1/subscribe").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subscriberCount").value(1));
	}

	@Test
	@WithMockUser(authorities = "SCOPE_source.write")
	void subscribe_missingSource_returns404() throws Exception {
		when(sourceService.subscribe("999")).thenReturn(null);

		mockMvc.perform(post("/sources/999/subscribe").with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	void subscribe_unauthenticated_returns401() throws Exception {
		mockMvc.perform(post("/sources/1/subscribe").with(csrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	void subscribe_withoutServiceScope_returns403() throws Exception {
		// An ordinary authenticated end user (no source.write scope) must not be able to mutate
		// the shared subscriber count — this is the IDOR fix.
		mockMvc.perform(post("/sources/1/subscribe").with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "SCOPE_source.write")
	void unsubscribe_existingSource_returns204() throws Exception {
		when(sourceService.unsubscribe("1")).thenReturn(true);

		mockMvc.perform(post("/sources/1/unsubscribe").with(csrf()))
				.andExpect(status().isNoContent());
	}

	@Test
	@WithMockUser(authorities = "SCOPE_source.write")
	void unsubscribe_missingSource_returns404() throws Exception {
		when(sourceService.unsubscribe("999")).thenReturn(false);

		mockMvc.perform(post("/sources/999/unsubscribe").with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	void unsubscribe_withoutServiceScope_returns403() throws Exception {
		mockMvc.perform(post("/sources/1/unsubscribe").with(csrf()))
				.andExpect(status().isForbidden());
	}
}
