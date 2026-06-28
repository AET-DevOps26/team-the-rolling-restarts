package rolling_restarts.gateway.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway liveness / demo endpoints. Hand-written (code-first): the response shape is a plain
 * record and springdoc derives the OpenAPI document from it.
 */
@RestController
@Tag(name = "root", description = "Gateway liveness and demo endpoints")
public class RootController {

	@GetMapping("/")
	public ResponseEntity<Message> root() {
		return ResponseEntity.ok(new Message("Hello, World!"));
	}

	@GetMapping("/test")
	public ResponseEntity<Message> test() {
		return ResponseEntity.ok(new Message("Hello, World!\nTest!"));
	}

	public record Message(String message) {}
}
