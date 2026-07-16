package com.example.coffeeordersystem.order;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.common.api.ApiResponseJsonCodec;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.application.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.application.IdempotencyFacade;
import com.example.coffeeordersystem.idempotency.application.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.application.RequestHasher;
import com.example.coffeeordersystem.menu.application.MenuQueryFacade;
import com.example.coffeeordersystem.menu.application.MenuSnapshot;
import com.example.coffeeordersystem.outbox.OutboxEventWriter;
import com.example.coffeeordersystem.point.LockedPointBalance;
import com.example.coffeeordersystem.point.PointPaymentService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
class OrderService {

  private final PointPaymentService pointPaymentService;
  private final IdempotencyFacade idempotencyFacade;
  private final RequestHasher requestHasher;
  private final MenuQueryFacade menuQueryFacade;
  private final OrderRepository orderRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final ApiResponseJsonCodec responseJsonCodec;
  private final Clock clock;
  private final BusinessEventLogger businessEventLogger;

  @Transactional
  OrderResult place(OrderCommand command) {
    LockedPointBalance pointBalance = pointPaymentService.lock(command.userId());
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
      return new OrderResult(
          claim.httpStatus(), responseJsonCodec.read(claim.responseBody()), claim.responseBody());
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
        outboxEventWriter.appendOrderPaid(
            order.id(), command.userId(), menu.menuId(), menu.price(), now);
    OrderResult result =
        success(
            new OrderResponse(
                order.id(), menu.menuId(), menu.name(), menu.price(), pointBalance.balance(), now));
    complete(claim, result, "ORDER_PAID", now);
    businessEventLogger.orderPaid(command.userId(), order.id(), eventId);
    return result;
  }

  private OrderResult success(OrderResponse data) {
    return response(
        HttpStatus.CREATED.value(), ApiResponse.success("ORDER_PAID", "주문과 결제가 완료되었습니다.", data));
  }

  private OrderResult completeFailure(
      IdempotencyClaim claim, ErrorCode errorCode, Instant completedAt) {
    OrderResult result = failure(errorCode);
    complete(claim, result, errorCode.name(), completedAt);
    return result;
  }

  private OrderResult failure(ErrorCode errorCode) {
    return response(
        errorCode.httpStatus().value(), ApiResponse.failure(errorCode.name(), errorCode.message()));
  }

  private OrderResult response(int httpStatus, Object response) {
    String responseBody = responseJsonCodec.write(response);
    return new OrderResult(httpStatus, responseJsonCodec.read(responseBody), responseBody);
  }

  private void complete(
      IdempotencyClaim claim, OrderResult result, String resultCode, Instant completedAt) {
    idempotencyFacade.complete(
        claim.recordId(), result.httpStatus(), resultCode, result.responseBody(), completedAt);
  }
}
