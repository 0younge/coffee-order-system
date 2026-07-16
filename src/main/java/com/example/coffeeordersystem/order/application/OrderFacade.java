package com.example.coffeeordersystem.order.application;

import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.application.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.application.IdempotencyFacade;
import com.example.coffeeordersystem.idempotency.application.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.application.IdempotencyResponseCodec;
import com.example.coffeeordersystem.idempotency.application.RequestHasher;
import com.example.coffeeordersystem.menu.application.MenuQueryFacade;
import com.example.coffeeordersystem.menu.application.MenuSnapshot;
import com.example.coffeeordersystem.order.domain.Order;
import com.example.coffeeordersystem.order.infrastructure.OrderRepository;
import com.example.coffeeordersystem.outbox.application.OutboxEventAppender;
import com.example.coffeeordersystem.point.application.LockedPointBalance;
import com.example.coffeeordersystem.point.application.PointPaymentFacade;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
public class OrderFacade {

  private final PointPaymentFacade pointPaymentFacade;
  private final IdempotencyFacade idempotencyFacade;
  private final RequestHasher requestHasher;
  private final MenuQueryFacade menuQueryFacade;
  private final OrderRepository orderRepository;
  private final OutboxEventAppender outboxEventAppender;
  private final IdempotencyResponseCodec responseCodec;
  private final Clock clock;
  private final BusinessEventLogger businessEventLogger;

  @Transactional
  public OrderResult place(OrderCommand command) {
    LockedPointBalance pointBalance = pointPaymentFacade.lock(command.userId());
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

    String requestHash = requestHasher.hash(IdempotencyOperation.ORDER, command.menuId());
    IdempotencyClaim claim =
        idempotencyFacade.claim(
            command.userId(),
            IdempotencyOperation.ORDER,
            command.idempotencyKey(),
            requestHash,
            now);
    if (claim.completed()) {
      if (!claim.requestHash().equals(requestHash)) {
        return failure(ErrorCode.IDEMPOTENCY_KEY_REUSED);
      }
      return new OrderResult(claim.httpStatus(), claim.responseBody());
    }

    MenuSnapshot menu = menuQueryFacade.findById(command.menuId()).orElse(null);
    if (menu == null) {
      return completeFailure(claim, ErrorCode.MENU_NOT_FOUND, now);
    }
    if (!pointBalance.pay(menu.price(), now)) {
      return completeFailure(claim, ErrorCode.INSUFFICIENT_POINT, now);
    }

    Order order =
        orderRepository.save(
            Order.paid(command.userId(), menu.menuId(), menu.name(), menu.price(), now));
    String eventId =
        outboxEventAppender.appendOrderPaid(
            order.id(), command.userId(), menu.menuId(), menu.price(), now);
    OrderResult result =
        success(
            new OrderData(
                order.id(), menu.menuId(), menu.name(), menu.price(), pointBalance.balance(), now));
    complete(claim, result, "ORDER_PAID", now);
    businessEventLogger.orderPaid(command.userId(), order.id(), eventId);
    return result;
  }

  private OrderResult success(OrderData data) {
    return new OrderResult(
        201, responseCodec.encodeSuccess("ORDER_PAID", "주문과 결제가 완료되었습니다.", data));
  }

  private OrderResult completeFailure(
      IdempotencyClaim claim, ErrorCode errorCode, Instant completedAt) {
    OrderResult result = failure(errorCode);
    complete(claim, result, errorCode.name(), completedAt);
    return result;
  }

  private OrderResult failure(ErrorCode errorCode) {
    return new OrderResult(
        errorCode.httpStatus().value(),
        responseCodec.encodeFailure(errorCode.name(), errorCode.message()));
  }

  private void complete(
      IdempotencyClaim claim, OrderResult result, String resultCode, Instant completedAt) {
    idempotencyFacade.complete(
        claim.recordId(), result.httpStatus(), resultCode, result.responseBody(), completedAt);
  }
}
