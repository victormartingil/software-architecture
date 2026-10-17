package dev.reference.commerce.returns.infrastructure;

import dev.reference.commerce.returns.application.port.output.ReturnRepository;
import dev.reference.commerce.returns.domain.ReturnProposal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReturnRepositoryAdapter implements ReturnRepository {
  private final JdbcTemplate db;

  public JdbcReturnRepositoryAdapter(JdbcTemplate db) {
    this.db = db;
  }

  public void save(ReturnProposal p) {
    db.update(
        "INSERT INTO return_proposals(id,tenant,actor,order_id,reason,status,version) VALUES"
            + " (?,?,?,?,?,?,?)",
        p.id(),
        p.tenant(),
        p.actor(),
        p.orderId(),
        p.reason(),
        p.status(),
        p.version());
  }

  public Optional<ReturnProposal> find(UUID id, boolean lock) {
    return db
        .query(
            "SELECT * FROM return_proposals WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) ->
                new ReturnProposal(
                    r.getObject("id", UUID.class),
                    r.getString("tenant"),
                    r.getString("actor"),
                    r.getObject("order_id", UUID.class),
                    r.getString("reason"),
                    r.getString("status"),
                    r.getLong("version"),
                    r.getTimestamp("approved_until") == null
                        ? null
                        : r.getTimestamp("approved_until").toInstant()),
            id)
        .stream()
        .findFirst();
  }

  public void approve(UUID id, String a, Instant until) {
    db.update(
        "UPDATE return_proposals SET"
            + " status='APPROVED',version=version+1,approved_until=?,approved_by=? WHERE id=?",
        java.sql.Timestamp.from(until),
        a,
        id);
  }

  public void executed(UUID id) {
    db.update("UPDATE return_proposals SET status='EXECUTED',version=version+1 WHERE id=?", id);
  }
}
