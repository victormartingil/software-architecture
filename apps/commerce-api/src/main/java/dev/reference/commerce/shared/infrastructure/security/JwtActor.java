package dev.reference.commerce.shared.infrastructure.security;

import dev.reference.commerce.shared.domain.Actor;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtActor {
  private JwtActor() {}

  public static Actor from(Jwt jwt) {
    Object realm = jwt.getClaim("realm_access");
    Object roles = realm instanceof java.util.Map<?, ?> map ? map.get("roles") : null;
    return new Actor(
        jwt.getClaimAsString("tenant"),
        jwt.getSubject(),
        roles instanceof java.util.Collection<?> rolesList && rolesList.contains("MANAGER"));
  }
}
