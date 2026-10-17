package dev.reference.commerce.inventory.infrastructure.persistence;

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

  public Stock get(String tenant, String sku) {
    return db
        .query(
            "SELECT * FROM inventory WHERE tenant=? AND sku=?",
            (row, rowNumber) ->
                new Stock(row.getString("sku"), row.getInt("stock"), row.getLong("version")),
            tenant,
            sku)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new BusinessException(BusinessException.Kind.NOT_FOUND, "SKU not found"));
  }

  public int reserve(String tenant, String sku, int quantity) {
    var prices =
        db.query(
            "UPDATE inventory SET stock=stock-?,version=version+1 WHERE tenant=? AND sku=? AND"
                + " stock>=? RETURNING price",
            (row, rowNumber) -> row.getInt(1),
            quantity,
            tenant,
            sku,
            quantity);
    if (prices.isEmpty())
      throw new BusinessException(
          BusinessException.Kind.CONFLICT, "Insufficient stock or unavailable SKU");
    return prices.getFirst();
  }

  public void restore(String tenant, String sku, int quantity) {
    if (db.update(
            "UPDATE inventory SET stock=stock+?,version=version+1 WHERE tenant=? AND sku=?",
            quantity,
            tenant,
            sku)
        != 1) throw new BusinessException(BusinessException.Kind.CONFLICT, "Inventory missing");
  }
}
