package dev.reference.commerce.returns.infrastructure.rest;

import dev.reference.commerce.returns.application.model.*;
import dev.reference.commerce.returns.application.port.input.ReturnUseCase;
import dev.reference.commerce.returns.domain.ReturnProposal;
import dev.reference.commerce.shared.infrastructure.security.JwtActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ReturnController {
  private final ReturnUseCase returns;

  public ReturnController(ReturnUseCase returns) {
    this.returns = returns;
  }

  @PostMapping("/returns")
  @ResponseStatus(HttpStatus.CREATED)
  ReturnProposal proposal(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
      @RequestBody @Valid CreateReturnCommand command) {
    return returns.create(JwtActor.from(jwt), idempotencyKey, command);
  }

  @GetMapping("/returns/{id}")
  ReturnProposal proposal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return returns.get(JwtActor.from(jwt), id);
  }

  @PostMapping("/returns/{id}/approve")
  ReturnProposal approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody @Valid VersionCommand command) {
    return returns.approve(JwtActor.from(jwt), id, command.version());
  }

  @PostMapping("/returns/{id}/execute")
  ReturnProposal execute(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
      @RequestBody @Valid VersionCommand command) {
    return returns.execute(JwtActor.from(jwt), id, idempotencyKey, command.version());
  }
}
