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

  public <T> T execute(String scope, Object request, Class<T> type, Supplier<T> action) {
    try {
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));
      // A transaction-scoped lock serializes retries before testing the persistent key.
      db.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", r -> {}, scope);
      var rows =
          db.query(
              "SELECT request_hash,result::text FROM idempotency WHERE scope=?",
              (r, n) -> new String[] {r.getString(1), r.getString(2)},
              scope);
      if (!rows.isEmpty()) {
        if (!rows.getFirst()[0].equals(hash))
          throw new BusinessException(409, "Idempotency key reused with different request");
        return json.readValue(rows.getFirst()[1], type);
      }
      T result = action.get();
      db.update(
          "INSERT INTO idempotency VALUES (?,?,?::jsonb)",
          scope,
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
