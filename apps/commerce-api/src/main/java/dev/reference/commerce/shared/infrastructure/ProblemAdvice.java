package dev.reference.commerce.shared.infrastructure;

import dev.reference.commerce.shared.domain.BusinessException;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ProblemAdvice {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> business(BusinessException e) {
    return response(e.status(), e.getMessage());
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
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
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return response(400, "Invalid request");
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ResponseEntity<ProblemDetail> unavailable(Exception e) {
    return response(503, "Persistence temporarily unavailable");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    return response(500, "Unexpected failure");
  }

  private ResponseEntity<ProblemDetail> response(int status, String detail) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    p.setProperty("correlationId", MDC.get("correlationId"));
    return ResponseEntity.status(status).body(p);
  }
}
