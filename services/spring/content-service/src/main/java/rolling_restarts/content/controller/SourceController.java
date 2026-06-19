package rolling_restarts.content.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import rolling_restarts.content.model.Source;
import rolling_restarts.content.repository.SourceRepository;

@RestController
@RequestMapping("/sources")
@Tag(name = "Sources", description = "RSS feed source management")
public class SourceController {

	private final SourceRepository sourceRepository;

	public SourceController(SourceRepository sourceRepository) {
		this.sourceRepository = sourceRepository;
	}

	@GetMapping
	@Operation(summary = "List all RSS sources")
	public List<Source> list() {
		return sourceRepository.findAll();
	}

	@GetMapping("/{id}")
	@Operation(
			summary = "Get source details",
			responses = {
					@ApiResponse(responseCode = "200", description = "Source found"),
					@ApiResponse(responseCode = "404", description = "Source not found")
			})
	public ResponseEntity<Source> get(@PathVariable String id) {
		return sourceRepository.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	@SecurityRequirement(name = "bearer-jwt")
	@Operation(
			summary = "Submit a new RSS feed URL",
			description = "Adds a new RSS source. If the URL already exists, returns the existing source.",
			responses = {
					@ApiResponse(responseCode = "201", description = "Source created"),
					@ApiResponse(responseCode = "200", description = "Source already exists"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public ResponseEntity<Source> create(@Valid @RequestBody CreateSourceRequest request) {
		return sourceRepository.findByRssUrl(request.rssUrl())
				.map(ResponseEntity::ok)
				.orElseGet(() -> {
					Source source = new Source();
					source.setName(request.name());
					source.setRssUrl(request.rssUrl());
					if (request.name() != null && request.name().length() >= 2) {
						source.setInitials(request.name().substring(0, 2).toUpperCase());
					}
					return ResponseEntity.status(HttpStatus.CREATED).body(sourceRepository.save(source));
				});
	}

	@DeleteMapping("/{id}")
	@SecurityRequirement(name = "bearer-jwt")
	@Operation(
			summary = "Remove a source",
			responses = {
					@ApiResponse(responseCode = "204", description = "Source deleted"),
					@ApiResponse(responseCode = "404", description = "Source not found"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public ResponseEntity<Void> delete(@PathVariable String id) {
		if (!sourceRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		sourceRepository.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	public record CreateSourceRequest(@NotBlank String name, @NotBlank String rssUrl) {}
}
