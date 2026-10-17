package dev.reference.commerce.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reference.commerce.shared.application.Idempotency;
import dev.reference.commerce.shared.domain.BusinessException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcIdempotencyAdapter implements Idempotency {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public JdbcIdempotencyAdapter(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public <T> T execute(Scope scope, Object request, Class<T> type, Supplier<T> action) {
    try {
      // A JSON array preserves component boundaries, including quotes, colons and Unicode.
      String storageScope =
          json.writeValueAsString(
              java.util.List.of(
                  scope.tenant(), scope.subject(), scope.operation(), scope.resource(), scope.key()));
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));
      // A transaction-scoped lock serializes retries before testing the persistent key.
      db.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", r -> {}, storageScope);
      var rows =
          db.query(
              "SELECT request_hash,result::text FROM idempotency WHERE scope=? AND scope_version=2",
              (r, n) -> new String[] {r.getString(1), r.getString(2)},
              storageScope);
      if (!rows.isEmpty()) {
        if (!rows.getFirst()[0].equals(hash))
          throw new BusinessException(409, "Idempotency key reused with different request");
        return json.readValue(rows.getFirst()[1], type);
      }
      T result = action.get();
      db.update(
          "INSERT INTO idempotency(scope,request_hash,result,scope_version) VALUES (?,?,?::jsonb,2)",
          storageScope,
          hash,
          json.writeValueAsString(result));
      return result;
    } catch (BusinessException e) {
      throw e;
    } catch (org.springframework.dao.DataAccessException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize idempotent operation", e);
    }
  }
}
