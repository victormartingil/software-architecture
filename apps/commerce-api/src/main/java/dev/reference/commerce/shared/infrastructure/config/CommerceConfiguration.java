package dev.reference.commerce.shared.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class CommerceConfiguration {
  @Bean
  com.fasterxml.jackson.databind.ObjectMapper eventObjectMapper() {
    return com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
  }

  /** HTTP Jackson 3 only; the Jackson 2 event/idempotency codec keeps its own configuration. */
  @Bean
  org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer strictHttpNumbers() {
    return builder ->
        builder
            .disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(tools.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
