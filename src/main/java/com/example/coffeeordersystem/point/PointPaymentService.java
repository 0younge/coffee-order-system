package com.example.coffeeordersystem.point;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointPaymentService {

  private final PointAccountRepository pointAccountRepository;

  PointPaymentService(PointAccountRepository pointAccountRepository) {
    this.pointAccountRepository = pointAccountRepository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public LockedPointBalance lock(long userId) {
    return pointAccountRepository
        .findByIdForUpdate(userId)
        .map(LockedPointBalance::new)
        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
  }
}
