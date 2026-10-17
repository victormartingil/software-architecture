package dev.reference.commerce.shared.infrastructure.rest;

import dev.reference.commerce.shared.domain.BusinessException;
import org.slf4j.MDC;
import org.springframework.http.*;
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
    return response(503, "Persistence temporarily unavailable");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception) {
    return response(500, "Unexpected failure");
  }

  private ResponseEntity<ProblemDetail> response(int status, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    problem.setProperty("correlationId", MDC.get("correlationId"));
    return ResponseEntity.status(status).body(problem);
  }
}
