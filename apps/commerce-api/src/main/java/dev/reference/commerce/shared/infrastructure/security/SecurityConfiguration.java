package dev.reference.commerce.shared.infrastructure.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
  @Bean
  JwtDecoder decoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks,
      @Value("${app.audience}") String audience) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    OAuth2TokenValidator<Jwt> aud =
        token -> {
          Object subject = token.getClaims().get("sub");
          Object tenant = token.getClaims().get("tenant");
          Object realm = token.getClaims().get("realm_access");
          boolean validRealm =
              realm instanceof Map<?, ?> map
                  && map.get("roles") instanceof Collection<?> roles
                  && roles.stream().allMatch(String.class::isInstance);
          boolean valid =
              token.getAudience().contains(audience)
                  && token.getExpiresAt() != null
                  && subject instanceof String user
                  && !user.isBlank()
                  && tenant instanceof String tenantId
                  && !tenantId.isBlank()
                  && tenantId.length() <= 200
                  && validRealm;
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Required identity claims invalid", null));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), aud));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Object realm = jwt.getClaim("realm_access");
          Object roles = realm instanceof Map<?, ?> map ? map.get("roles") : null;
          if (!(roles instanceof Collection<?> values)) return List.of();
          return values.stream()
              .filter(String.class::isInstance)
              .map(x -> new SimpleGrantedAuthority("ROLE_" + x))
              .map(x -> (org.springframework.security.core.GrantedAuthority) x)
              .toList();
        });
    http.csrf(c -> c.disable())
        .sessionManagement(
            s ->
                s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .hasAnyRole("CUSTOMER", "MANAGER")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(
                        (r, s, e) -> problem(s, 401, "Authentication required")))
        .exceptionHandling(
            e -> e.accessDeniedHandler((r, s, x) -> problem(s, 403, "Insufficient permissions")));
    return http.build();
  }

  private void problem(jakarta.servlet.http.HttpServletResponse response, int status, String detail)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"status\":"
                + status
                + ",\"title\":\"Request rejected\",\"detail\":\""
                + detail
                + "\",\"correlationId\":\""
                + Objects.toString(org.slf4j.MDC.get("correlationId"), UUID.randomUUID().toString())
                + "\"}");
  }
}
