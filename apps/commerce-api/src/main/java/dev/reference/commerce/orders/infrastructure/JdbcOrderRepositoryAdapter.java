package dev.reference.commerce.orders.infrastructure;

import dev.reference.commerce.orders.application.port.output.OrderRepository;
import dev.reference.commerce.orders.domain.Order;
import dev.reference.commerce.shared.domain.BusinessException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderRepositoryAdapter implements OrderRepository {
  private final JdbcTemplate db;

  public JdbcOrderRepositoryAdapter(JdbcTemplate db) {
    this.db = db;
  }

  public void save(Order o) {
    db.update(
        "INSERT INTO orders VALUES (?,?,?,?,?,?,?,?)",
        o.id(),
        o.tenant(),
        o.actor(),
        o.sku(),
        o.quantity(),
        o.totalCents(),
        o.status(),
        o.version());
  }

  public Optional<Order> find(UUID id, boolean lock) {
    return db
        .query(
            "SELECT * FROM orders WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) ->
                new Order(
                    r.getObject("id", UUID.class),
                    r.getString("tenant"),
                    r.getString("actor"),
                    r.getString("sku"),
                    r.getInt("quantity"),
                    r.getInt("total_cents"),
                    r.getString("status"),
                    r.getLong("version")),
            id)
        .stream()
        .findFirst();
  }

  public void refund(UUID id) {
    if (db.update(
            "UPDATE orders SET status='REFUNDED',version=version+1 WHERE id=? AND status='PLACED'",
            id)
        != 1) throw new BusinessException(409, "Order already refunded");
  }
}
