package com.example.coffeeordersystem.point;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
public class PointPaymentService {

  private final PointAccountRepository pointAccountRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public LockedPointBalance lock(long userId) {
    return pointAccountRepository
        .findByIdForUpdate(userId)
        .map(LockedPointBalance::new)
        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
  }
}
