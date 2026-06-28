package rolling_restarts.gateway.exception;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	public record ApiError(
			Instant timestamp,
			int code,
			String message,
			List<String> details,
			String path) {}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.toList();
		ApiError error = new ApiError(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"Validation failed",
				details,
				request.getDescription(false).replace("uri=", ""));
		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
		ApiError error = new ApiError(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				List.of(),
				request.getDescription(false).replace("uri=", ""));
		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest request) {
		String path = request.getDescription(false).replace("uri=", "");
		log.error("Unhandled exception at {}", path, ex);
		ApiError error = new ApiError(
				Instant.now(),
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"Internal server error",
				List.of(),
				path);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
	}
}
