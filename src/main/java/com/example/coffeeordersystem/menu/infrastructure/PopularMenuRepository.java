package com.example.coffeeordersystem.menu.infrastructure;

import com.example.coffeeordersystem.menu.domain.PopularMenuAggregate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class PopularMenuRepository {

  private final JdbcTemplate jdbcTemplate;

  public List<PopularMenuAggregate> findTopThree(Instant from, Instant to) {
    return jdbcTemplate.query(
        "SELECT m.id AS menu_id, m.name, m.price, COUNT(*) AS order_count "
            + "FROM orders o JOIN menus m ON m.id = o.menu_id "
            + "WHERE o.status = 'PAID' AND o.paid_at >= ? AND o.paid_at < ? "
            + "GROUP BY m.id, m.name, m.price "
            + "ORDER BY order_count DESC, m.id ASC LIMIT 3",
        (resultSet, rowNumber) ->
            new PopularMenuAggregate(
                resultSet.getLong("menu_id"),
                resultSet.getString("name"),
                resultSet.getLong("price"),
                resultSet.getLong("order_count")),
        Timestamp.from(from),
        Timestamp.from(to));
  }
}
