package rolling_restarts.user.exception;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
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
				extractPath(request));
		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
		String msg = ex.getMessage();
		if (msg != null && msg.contains("already")) {
			ApiError error = new ApiError(
					Instant.now(),
					HttpStatus.CONFLICT.value(),
					msg,
					List.of(),
					extractPath(request));
			return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
		}
		if (msg != null && msg.contains("not found")) {
			ApiError error = new ApiError(
					Instant.now(),
					HttpStatus.NOT_FOUND.value(),
					msg,
					List.of(),
					extractPath(request));
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
		}
		ApiError error = new ApiError(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				msg,
				List.of(),
				extractPath(request));
		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
		// Deliberately generic: do not reveal whether the username or the password was wrong.
		ApiError error = new ApiError(
				Instant.now(),
				HttpStatus.UNAUTHORIZED.value(),
				"Invalid username or password",
				List.of(),
				extractPath(request));
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}

	@ExceptionHandler(DuplicateKeyException.class)
	public ResponseEntity<ApiError> handleDuplicateKey(DuplicateKeyException ex, WebRequest request) {
		ApiError error = new ApiError(
				Instant.now(),
				HttpStatus.CONFLICT.value(),
				"Duplicate value",
				List.of(),
				extractPath(request));
		return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest request) {
		log.error("Unhandled exception at {}", extractPath(request), ex);
		ApiError error = new ApiError(
				Instant.now(),
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"Internal server error",
				List.of(),
				extractPath(request));
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
	}

	private static String extractPath(WebRequest request) {
		return request.getDescription(false).replace("uri=", "");
	}
}
