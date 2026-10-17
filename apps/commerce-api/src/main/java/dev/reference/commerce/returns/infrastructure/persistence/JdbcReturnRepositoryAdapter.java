package dev.reference.commerce.returns.infrastructure.persistence;

import dev.reference.commerce.returns.application.port.output.ReturnRepository;
import dev.reference.commerce.returns.domain.ReturnProposal;
import dev.reference.commerce.returns.domain.ReturnStatus;
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

  public void save(ReturnProposal proposal) {
    db.update(
        "INSERT INTO return_proposals(id,tenant,actor,order_id,reason,status,version) VALUES"
            + " (?,?,?,?,?,?,?)",
        proposal.id(),
        proposal.tenant(),
        proposal.actor(),
        proposal.orderId(),
        proposal.reason(),
        proposal.status().name(),
        proposal.version());
  }

  public Optional<ReturnProposal> find(UUID id, boolean lock) {
    return db
        .query(
            "SELECT * FROM return_proposals WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (row, rowNumber) ->
                new ReturnProposal(
                    row.getObject("id", UUID.class),
                    row.getString("tenant"),
                    row.getString("actor"),
                    row.getObject("order_id", UUID.class),
                    row.getString("reason"),
                    ReturnStatus.valueOf(row.getString("status")),
                    row.getLong("version"),
                    row.getTimestamp("approved_until") == null
                        ? null
                        : row.getTimestamp("approved_until").toInstant()),
            id)
        .stream()
        .findFirst();
  }

  public void approve(UUID id, String approver, Instant until) {
    db.update(
        "UPDATE return_proposals SET"
            + " status='APPROVED',version=version+1,approved_until=?,approved_by=? WHERE id=?",
        java.sql.Timestamp.from(until),
        approver,
        id);
  }

  public void executed(UUID id) {
    db.update("UPDATE return_proposals SET status='EXECUTED',version=version+1 WHERE id=?", id);
  }
}
