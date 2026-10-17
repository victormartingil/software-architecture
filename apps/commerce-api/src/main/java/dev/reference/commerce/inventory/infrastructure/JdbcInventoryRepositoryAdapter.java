package dev.reference.commerce.inventory.infrastructure;

import dev.reference.commerce.inventory.application.port.output.InventoryRepository;
import dev.reference.commerce.inventory.domain.Stock;
import dev.reference.commerce.shared.domain.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInventoryRepositoryAdapter implements InventoryRepository {
  private final JdbcTemplate db;

  public JdbcInventoryRepositoryAdapter(JdbcTemplate db) {
    this.db = db;
  }

  public Stock get(String t, String s) {
    return db
        .query(
            "SELECT * FROM inventory WHERE tenant=? AND sku=?",
            (r, n) -> new Stock(r.getString("sku"), r.getInt("stock"), r.getLong("version")),
            t,
            s)
        .stream()
        .findFirst()
        .orElseThrow(() -> new BusinessException(404, "SKU not found"));
  }

  public int reserve(String t, String s, int q) {
    var prices =
        db.query(
            "UPDATE inventory SET stock=stock-?,version=version+1 WHERE tenant=? AND sku=? AND"
                + " stock>=? RETURNING price",
            (r, n) -> r.getInt(1),
            q,
            t,
            s,
            q);
    if (prices.isEmpty()) throw new BusinessException(409, "Insufficient stock or unavailable SKU");
    return prices.getFirst();
  }

  public void restore(String t, String s, int q) {
    if (db.update(
            "UPDATE inventory SET stock=stock+?,version=version+1 WHERE tenant=? AND sku=?",
            q,
            t,
            s)
        != 1) throw new BusinessException(409, "Inventory missing");
  }
}
