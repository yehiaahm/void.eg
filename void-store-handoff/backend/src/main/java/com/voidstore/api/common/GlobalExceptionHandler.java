package com.voidstore.api.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	public record ErrorBody(String code, String message, Map<String, String> fields) {}

	@ExceptionHandler(com.voidstore.api.order.OrderService.StockProblem.class)
	ResponseEntity<Map<String, Object>> stock(com.voidstore.api.order.OrderService.StockProblem e) {
		return ResponseEntity.status(e.getStatus())
				.body(Map.of("code", e.getCode(), "message", e.getMessage(), "lines", e.getLines()));
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorBody> api(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorBody(e.getCode(), e.getMessage(), null));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
		Map<String, String> fields = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorBody("validation", "Invalid input", fields));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e) {
		log.warn("Unreadable request body: {}", e.getMostSpecificCause().getMessage());
		return ResponseEntity.badRequest().body(new ErrorBody("bad_request", "Malformed request body", null));
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ErrorBody> conflict(OptimisticLockingFailureException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorBody("conflict", "The data changed meanwhile, please retry", null));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorBody> tooLarge(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErrorBody("too_large", "File too large", null));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErrorBody> denied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody("forbidden", "Not allowed", null));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ErrorBody> unauthenticated(AuthenticationException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody("unauthorized", "Sign in required", null));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorBody> noResource(NoResourceFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("not_found", "Not found", null));
	}

	/** The client went away mid-response (closed tab, timeout) — nothing to answer, not an error. */
	@ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
	void clientGone() {
		// intentionally empty
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorBody> unexpected(Exception e) {
		log.error("Unhandled error", e);
		return ResponseEntity.internalServerError().body(new ErrorBody("server_error", "Something went wrong", null));
	}
}
