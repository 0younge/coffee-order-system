package com.example.coffeeordersystem.point;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from PointAccount account where account.id = :id")
  Optional<PointAccount> findByIdForUpdate(@Param("id") long id);
}
