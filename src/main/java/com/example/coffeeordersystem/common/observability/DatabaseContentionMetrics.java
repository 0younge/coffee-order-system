package com.example.coffeeordersystem.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.SQLException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;

@Component
public class DatabaseContentionMetrics {

  private static final int MYSQL_LOCK_TIMEOUT = 1205;
  private static final int MYSQL_DEADLOCK = 1213;

  private final Counter lockTimeout;
  private final Counter deadlock;

  public DatabaseContentionMetrics(MeterRegistry meterRegistry) {
    lockTimeout = meterRegistry.counter("coffee.db.lock.timeout");
    deadlock = meterRegistry.counter("coffee.db.deadlock");
  }

  public String record(Throwable throwable) {
    int errorCode = mysqlErrorCode(throwable);
    if (errorCode == MYSQL_DEADLOCK) {
      deadlock.increment();
      return "deadlock";
    }
    if (errorCode == MYSQL_LOCK_TIMEOUT || throwable instanceof QueryTimeoutException) {
      lockTimeout.increment();
      return "lock_timeout";
    }
    lockTimeout.increment();
    return "lock_timeout";
  }

  private int mysqlErrorCode(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        return sqlException.getErrorCode();
      }
      if (current == current.getCause()) {
        return 0;
      }
      current = current.getCause();
    }
    return 0;
  }
}
