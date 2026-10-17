package dev.reference.commerce.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;

class SecurityJwtTest {
  HttpServer server;
  RSAKey key;
  JwtDecoder decoder;
  String issuer = "https://identity.example/realms/reference";

  @BeforeEach
  void setup() throws Exception {
    key = new RSAKeyGenerator(2048).keyID("reference").generate();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/jwks",
        exchange -> {
          byte[] bytes =
              new JWKSet(key.toPublicJWK())
                  .toString()
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    decoder =
        new SecurityConfiguration()
            .decoder(
                issuer,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                "reference-api");
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  String token(String iss, String aud, Instant expiry, RSAKey signingKey) {
    var encoder =
        new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey)));
    var claims =
        JwtClaimsSet.builder()
            .issuer(iss)
            .subject("alice")
            .audience(List.of(aud))
            .issuedAt(Instant.now().minusSeconds(600))
            .expiresAt(expiry)
            .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
            .claim("tenant", "demo")
            .build();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                    .keyId("reference")
                    .build(),
                claims))
        .getTokenValue();
  }

  @Test
  void validatesSignatureIssuerAudienceAndExpiry() throws Exception {
    assertThat(
            decoder
                .decode(token(issuer, "reference-api", Instant.now().plusSeconds(300), key))
                .getSubject())
        .isEqualTo("alice");
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(
                        "https://wrong.example",
                        "reference-api",
                        Instant.now().plusSeconds(300),
                        key)))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(issuer, "wrong-audience", Instant.now().plusSeconds(300), key)))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(issuer, "reference-api", Instant.now().minusSeconds(300), key)))
        .isInstanceOf(JwtException.class);
    var other = new RSAKeyGenerator(2048).keyID("reference").generate();
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(issuer, "reference-api", Instant.now().plusSeconds(300), other)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsMissingExpiryBlankIdentityAndMalformedRoles() {
    for (String field : List.of("exp", "sub", "tenant", "realm_access", "roles")) {
      var claims =
          JwtClaimsSet.builder()
              .issuer(issuer)
              .subject("alice")
              .audience(List.of("reference-api"))
              .issuedAt(Instant.now().minusSeconds(10))
              .expiresAt(Instant.now().plusSeconds(300))
              .claim("tenant", "demo")
              .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
              .claims(
                  map -> {
                    switch (field) {
                      case "exp" -> map.remove("exp");
                      case "sub", "tenant" -> map.put(field, " ");
                      case "realm_access" -> map.put(field, "malformed");
                      case "roles" -> map.put("realm_access", Map.of("roles", "MANAGER"));
                    }
                  })
              .build();
      var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
      var token =
          encoder
              .encode(
                  JwtEncoderParameters.from(
                      JwsHeader.with(
                              org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                          .keyId("reference")
                          .build(),
                      claims))
              .getTokenValue();
      assertThatThrownBy(() -> decoder.decode(token)).as(field).isInstanceOf(JwtException.class);
    }
  }
}
