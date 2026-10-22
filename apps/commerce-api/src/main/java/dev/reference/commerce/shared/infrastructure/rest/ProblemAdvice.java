package dev.reference.commerce.shared.infrastructure.rest;

import dev.reference.commerce.shared.domain.BusinessException;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ProblemAdvice {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> business(BusinessException exception) {
    return response(
        switch (exception.kind()) {
          case INVALID_INPUT -> 400;
          case NOT_FOUND -> 404;
          case CONFLICT -> 409;
          case FORBIDDEN -> 403;
        },
        exception.getMessage());
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception exception) {
    return response(409, "Conflicting operation");
  }

  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception exception) {
    return response(400, "Invalid request");
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ResponseEntity<ProblemDetail> unavailable(Exception exception) {
    failure(503, exception);
    return response(503, "Persistence temporarily unavailable");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception) {
    if (exception instanceof ErrorResponse error) {
      int status = error.getStatusCode().value();
      if (status >= 500) failure(status, exception);
      String detail =
          switch (status) {
            case 400 -> "Invalid request";
            case 404 -> "Resource not found";
            case 405 -> "Method not allowed";
            case 406 -> "Requested representation unavailable";
            case 415 -> "Unsupported request media type";
            default -> status >= 500 ? "Unexpected failure" : "Request rejected";
          };
      return response(status, detail, error.getHeaders());
    }
    failure(500, exception);
    return response(500, "Unexpected failure");
  }

  private void failure(int status, Exception exception) {
    Throwable cause = exception;
    var visited =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
    for (int depth = 0; depth < 8 && cause.getCause() != null && visited.add(cause); depth++) {
      if (visited.contains(cause.getCause())) break;
      cause = cause.getCause();
    }
    // Exception messages and attached throwables may contain SQL, credentials or request data.
    var logger = LoggerFactory.getLogger(ProblemAdvice.class);
    var event = status == 503 ? logger.atWarn() : logger.atError();
    event
        .addKeyValue("event", "http_failure")
        .addKeyValue("status", status)
        .addKeyValue("exceptionType", exception.getClass().getName())
        .addKeyValue("rootCauseType", cause.getClass().getName())
        .log("HTTP operation failed");
  }

  private ResponseEntity<ProblemDetail> response(int status, String detail) {
    return response(status, detail, HttpHeaders.EMPTY);
  }

  private ResponseEntity<ProblemDetail> response(int status, String detail, HttpHeaders headers) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    problem.setProperty("correlationId", MDC.get("correlationId"));
    return ResponseEntity.status(status)
        .headers(headers)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
