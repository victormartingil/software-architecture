package dev.reference.commerce.shared.infrastructure.rest;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.reference.commerce.shared.domain.BusinessException;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;

class ProblemAdviceTest {
  private static final String PRIVATE = "private-token-body-sql-marker";
  private static final String CORRELATION = "failure-test-correlation";
  private Logger logger;
  private ListAppender<ILoggingEvent> captured;
  private MockMvc http;

  @BeforeEach
  void setup() {
    logger = (Logger) LoggerFactory.getLogger(ProblemAdvice.class);
    captured =
        new ListAppender<>() {
          @Override
          protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
          }
        };
    captured.setContext(logger.getLoggerContext());
    captured.start();
    logger.addAppender(captured);
    http =
        MockMvcBuilders.standaloneSetup(new FailureController())
            .setControllerAdvice(new ProblemAdvice())
            .addFilters(new CorrelationFilter())
            .build();
  }

  @AfterEach
  void cleanup() {
    logger.detachAppender(captured);
    captured.stop();
  }

  @Test
  void unexpectedAndPersistenceFailuresHaveOneSafeDiagnosticEach() throws Exception {
    for (int status : new int[] {500, 503}) {
      captured.list.clear();
      var response =
          http.perform(get("/failure/" + status).header("X-Correlation-Id", CORRELATION))
              .andExpect(status().is(status))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.status").value(status))
              .andExpect(jsonPath("$.correlationId").value(CORRELATION))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(response).doesNotContain(PRIVATE, "SQLException");
      assertThat(captured.list).hasSize(1);
      var event = captured.list.getFirst();
      assertThat(event.getLevel().toString()).isEqualTo(status == 503 ? "WARN" : "ERROR");
      assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", CORRELATION);
      assertThat(event.getThrowableProxy()).isNull();
      assertThat(event.getFormattedMessage()).doesNotContain(PRIVATE);
      assertThat(event.getArgumentArray()).isNullOrEmpty();
      assertThat(event.getKeyValuePairs())
          .extracting(pair -> pair.key)
          .containsExactly("event", "status", "exceptionType", "rootCauseType");
      assertThat(event.getKeyValuePairs())
          .extracting(pair -> pair.value)
          .contains("http_failure", status, SQLException.class.getName());
      assertThat(event.getKeyValuePairs().toString()).doesNotContain(PRIVATE);
    }
  }

  @Test
  void frameworkFailuresPreserveHeadersWithoutTrustingExceptionDetails() throws Exception {
    http.perform(get("/framework-failure").header("X-Correlation-Id", CORRELATION))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"))
        .andExpect(jsonPath("$.detail").value("Unexpected failure"))
        .andExpect(jsonPath("$.correlationId").value(CORRELATION))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PRIVATE))));
    assertThat(captured.list).hasSize(1);
    assertThat(captured.list.getFirst().getThrowableProxy()).isNull();
    assertThat(captured.list.getFirst().getKeyValuePairs().toString()).doesNotContain(PRIVATE);
  }

  @Test
  void mvcErrorsKeepTheirStatusEvenWhenTheHandlerOrRepresentationIsMissing() throws Exception {
    http.perform(delete("/payload").header("X-Correlation-Id", CORRELATION))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.correlationId").value(CORRELATION));
    http.perform(post("/payload").contentType("text/plain").content(PRIVATE))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.status").value(415));
    for (String accept : java.util.List.of("text/plain", "invalid media type")) {
      http.perform(get("/payload").header("Accept", accept))
          .andExpect(status().isNotAcceptable())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(406));
    }
    http.perform(get("/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
    assertThat(captured.list).isEmpty();
  }

  @Test
  void expectedBusinessFailuresDoNotProduceFailureDiagnostics() throws Exception {
    http.perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Controlled conflict"));
    assertThat(captured.list).isEmpty();
  }

  @RestController
  static class FailureController {
    @GetMapping("/payload")
    java.util.Map<String, String> payload() {
      return java.util.Map.of("result", "ok");
    }

    @PostMapping("/payload")
    java.util.Map<String, String> payload(@RequestBody java.util.Map<String, String> value) {
      return value;
    }

    @GetMapping("/failure/{status}")
    String fail(@PathVariable int status) {
      var cause = new SQLException(PRIVATE);
      if (status == 503) throw new DataAccessResourceFailureException(PRIVATE, cause);
      throw new IllegalStateException(PRIVATE, cause);
    }

    @GetMapping("/framework-failure")
    String frameworkFailure() {
      var exception =
          new ErrorResponseException(
              HttpStatus.SERVICE_UNAVAILABLE,
              ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, PRIVATE),
              null);
      exception.getHeaders().set("Retry-After", "5");
      throw exception;
    }

    @GetMapping("/conflict")
    String conflict() {
      throw new BusinessException(BusinessException.Kind.CONFLICT, "Controlled conflict");
    }
  }
}
