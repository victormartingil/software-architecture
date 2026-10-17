package dev.reference.commerce.orders.infrastructure.persistence;

import dev.reference.commerce.orders.application.model.OrderQuery;
import dev.reference.commerce.orders.application.port.output.OrderRepository;
import dev.reference.commerce.orders.domain.Order;
import dev.reference.commerce.orders.domain.OrderStatus;
import dev.reference.commerce.shared.domain.Actor;
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

  public void save(Order order) {
    db.update(
        "INSERT INTO orders VALUES (?,?,?,?,?,?,?,?)",
        order.id(),
        order.tenant(),
        order.actor(),
        order.sku(),
        order.quantity(),
        order.totalCents(),
        order.status().name(),
        order.version());
  }

  public Optional<Order> find(UUID id, boolean lock) {
    return db
        .query(
            "SELECT * FROM orders WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (row, rowNumber) ->
                new Order(
                    row.getObject("id", UUID.class),
                    row.getString("tenant"),
                    row.getString("actor"),
                    row.getString("sku"),
                    row.getInt("quantity"),
                    row.getInt("total_cents"),
                    OrderStatus.valueOf(row.getString("status")),
                    row.getLong("version")),
            id)
        .stream()
        .findFirst();
  }

  public List<Order> list(Actor actor, OrderQuery query) {
    String sql = "SELECT * FROM orders WHERE tenant=?";
    var parameters = new ArrayList<Object>();
    parameters.add(actor.tenant());
    if (!actor.manager()) {
      sql += " AND actor=?";
      parameters.add(actor.subject());
    }
    if (query.after() != null) {
      sql += " AND id>?";
      parameters.add(query.after());
    }
    if (query.status() != null) {
      sql += " AND status=?";
      parameters.add(query.status().name());
    }
    sql += " ORDER BY id ASC LIMIT ?";
    parameters.add(query.limit() + 1);
    return db.query(
        sql,
        (row, rowNumber) ->
            new Order(
                row.getObject("id", UUID.class),
                row.getString("tenant"),
                row.getString("actor"),
                row.getString("sku"),
                row.getInt("quantity"),
                row.getInt("total_cents"),
                OrderStatus.valueOf(row.getString("status")),
                row.getLong("version")),
        parameters.toArray());
  }

  public void refund(UUID id) {
    if (db.update(
            "UPDATE orders SET status='REFUNDED',version=version+1 WHERE id=? AND status='PLACED'",
            id)
        != 1)
      throw new BusinessException(BusinessException.Kind.CONFLICT, "Order already refunded");
  }
}
