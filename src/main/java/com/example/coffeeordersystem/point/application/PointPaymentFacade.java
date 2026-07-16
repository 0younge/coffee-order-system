package com.example.coffeeordersystem.point.application;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.point.infrastructure.PointAccountRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
public class PointPaymentFacade {

  private final PointAccountRepository pointAccountRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public LockedPointBalance lock(long userId) {
    return pointAccountRepository
        .findByIdForUpdate(userId)
        .map(LockedPointBalance::new)
        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
  }
}
