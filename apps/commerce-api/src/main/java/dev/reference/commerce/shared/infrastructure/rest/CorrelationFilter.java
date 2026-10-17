package dev.reference.commerce.shared.infrastructure.rest;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String value = request.getHeader("X-Correlation-Id");
    if (value == null || !value.matches("[A-Za-z0-9-]{1,80}")) value = UUID.randomUUID().toString();
    MDC.put("correlationId", value);
    response.setHeader("X-Correlation-Id", value);
    long started = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      // Route templates are bounded; never record raw URLs, query strings, bodies or credentials.
      if (!request.getRequestURI().startsWith("/actuator/")) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        LoggerFactory.getLogger(CorrelationFilter.class).atInfo()
            .addKeyValue("event", "http_request")
            .addKeyValue("route", route == null ? "unmatched" : route)
            .addKeyValue("method", request.getMethod())
            .addKeyValue("status", response.getStatus())
            .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000.0)
            .log("HTTP request completed");
      }
      MDC.remove("correlationId");
    }
  }
}
