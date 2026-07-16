package com.example.coffeeordersystem.order;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.IdempotencyService;
import com.example.coffeeordersystem.idempotency.RequestHasher;
import com.example.coffeeordersystem.menu.MenuResponse;
import com.example.coffeeordersystem.menu.MenuService;
import com.example.coffeeordersystem.outbox.OutboxEventWriter;
import com.example.coffeeordersystem.point.LockedPointBalance;
import com.example.coffeeordersystem.point.PointPaymentService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class OrderService {

  private final PointPaymentService pointPaymentService;
  private final IdempotencyService idempotencyService;
  private final RequestHasher requestHasher;
  private final MenuService menuService;
  private final OrderRepository orderRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final BusinessEventLogger businessEventLogger;

  OrderService(
      PointPaymentService pointPaymentService,
      IdempotencyService idempotencyService,
      RequestHasher requestHasher,
      MenuService menuService,
      OrderRepository orderRepository,
      OutboxEventWriter outboxEventWriter,
      ObjectMapper objectMapper,
      Clock clock,
      BusinessEventLogger businessEventLogger) {
    this.pointPaymentService = pointPaymentService;
    this.idempotencyService = idempotencyService;
    this.requestHasher = requestHasher;
    this.menuService = menuService;
    this.orderRepository = orderRepository;
    this.outboxEventWriter = outboxEventWriter;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.businessEventLogger = businessEventLogger;
  }

  @Transactional
  OrderResult place(OrderCommand command) {
    Instant now = clock.instant();
    LockedPointBalance pointBalance = pointPaymentService.lock(command.userId());

    String requestHash = requestHasher.hash(IdempotencyOperation.ORDER, command.menuId());
    IdempotencyClaim claim =
        idempotencyService.claim(
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

    MenuResponse menu = menuService.findById(command.menuId()).orElse(null);
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
    JsonNode body =
        objectMapper.valueToTree(ApiResponse.success("ORDER_PAID", "주문과 결제가 완료되었습니다.", data));
    return new OrderResult(HttpStatus.CREATED.value(), body);
  }

  private OrderResult completeFailure(
      IdempotencyClaim claim, ErrorCode errorCode, Instant completedAt) {
    OrderResult result = failure(errorCode);
    complete(claim, result, errorCode.name(), completedAt);
    return result;
  }

  private OrderResult failure(ErrorCode errorCode) {
    JsonNode body =
        objectMapper.valueToTree(ApiResponse.failure(errorCode.name(), errorCode.message()));
    return new OrderResult(errorCode.httpStatus().value(), body);
  }

  private void complete(
      IdempotencyClaim claim, OrderResult result, String resultCode, Instant completedAt) {
    idempotencyService.complete(
        claim.recordId(), result.httpStatus(), resultCode, result.body(), completedAt);
  }
}
