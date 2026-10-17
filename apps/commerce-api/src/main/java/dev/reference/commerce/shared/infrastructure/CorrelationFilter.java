package dev.reference.commerce.shared.infrastructure;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    String value = req.getHeader("X-Correlation-Id");
    if (value == null || !value.matches("[A-Za-z0-9-]{1,80}")) value = UUID.randomUUID().toString();
    MDC.put("correlationId", value);
    res.setHeader("X-Correlation-Id", value);
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
