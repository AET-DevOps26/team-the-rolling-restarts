package rolling_restarts.content.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import rolling_restarts.content.model.Source;
import rolling_restarts.content.repository.SourceRepository;
import rolling_restarts.content.service.SourceService;
import rolling_restarts.content.util.UrlSafetyValidator;

@RestController
@RequestMapping("/sources")
@Tag(name = "Sources", description = "RSS feed source management")
public class SourceController {

	private final SourceRepository sourceRepository;
	private final SourceService sourceService;

	public SourceController(SourceRepository sourceRepository, SourceService sourceService) {
		this.sourceRepository = sourceRepository;
		this.sourceService = sourceService;
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
		UrlSafetyValidator.validatePublicUrl(request.rssUrl());
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

	@PostMapping("/{id}/subscribe")
	@SecurityRequirement(name = "bearer-jwt")
	@Operation(
			operationId = "incrementSourceSubscribers",
			summary = "Register a subscription to a source",
			description = "Increments the source's subscriber count. Intended for service-to-service "
					+ "use by user-service when a user subscribes.",
			responses = {
					@ApiResponse(responseCode = "200", description = "Subscription registered"),
					@ApiResponse(responseCode = "404", description = "Source not found"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public ResponseEntity<Source> subscribe(@PathVariable String id) {
		Source updated = sourceService.subscribe(id);
		return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
	}

	@PostMapping("/{id}/unsubscribe")
	@SecurityRequirement(name = "bearer-jwt")
	@Operation(
			operationId = "decrementSourceSubscribers",
			summary = "Remove a subscription from a source",
			description = "Decrements the source's subscriber count; the source is deleted once the "
					+ "count reaches zero. Intended for service-to-service use by user-service.",
			responses = {
					@ApiResponse(responseCode = "204", description = "Subscription removed"),
					@ApiResponse(responseCode = "404", description = "Source not found"),
					@ApiResponse(responseCode = "401", description = "Not authenticated")
			})
	public ResponseEntity<Void> unsubscribe(@PathVariable String id) {
		boolean existed = sourceService.unsubscribe(id);
		return existed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	public record CreateSourceRequest(
			@NotBlank String name,
			@NotBlank @Pattern(regexp = "^https?://.*", message = "must be an http or https URL") String rssUrl) {}
}
