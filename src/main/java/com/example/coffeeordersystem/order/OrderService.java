package com.example.coffeeordersystem.order;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.idempotency.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.IdempotencyService;
import com.example.coffeeordersystem.idempotency.RequestHasher;
import com.example.coffeeordersystem.menu.Menu;
import com.example.coffeeordersystem.menu.MenuRepository;
import com.example.coffeeordersystem.outbox.OutboxEventWriter;
import com.example.coffeeordersystem.point.PointAccount;
import com.example.coffeeordersystem.point.PointAccountRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class OrderService {

  private final PointAccountRepository pointAccountRepository;
  private final IdempotencyService idempotencyService;
  private final RequestHasher requestHasher;
  private final MenuRepository menuRepository;
  private final OrderRepository orderRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  OrderService(
      PointAccountRepository pointAccountRepository,
      IdempotencyService idempotencyService,
      RequestHasher requestHasher,
      MenuRepository menuRepository,
      OrderRepository orderRepository,
      OutboxEventWriter outboxEventWriter,
      ObjectMapper objectMapper,
      Clock clock) {
    this.pointAccountRepository = pointAccountRepository;
    this.idempotencyService = idempotencyService;
    this.requestHasher = requestHasher;
    this.menuRepository = menuRepository;
    this.orderRepository = orderRepository;
    this.outboxEventWriter = outboxEventWriter;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  OrderResult place(OrderCommand command) {
    Instant now = clock.instant();
    PointAccount account =
        pointAccountRepository
            .findByIdForUpdate(command.userId())
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

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

    Menu menu = menuRepository.findById(command.menuId()).orElse(null);
    if (menu == null) {
      return completeFailure(claim, ErrorCode.MENU_NOT_FOUND, now);
    }
    if (!account.pay(menu.price(), now)) {
      return completeFailure(claim, ErrorCode.INSUFFICIENT_POINT, now);
    }

    Order order =
        orderRepository.save(
            Order.paid(command.userId(), menu.id(), menu.name(), menu.price(), now));
    outboxEventWriter.appendOrderPaid(order.id(), command.userId(), menu.id(), menu.price(), now);
    OrderResult result =
        success(
            new OrderResponse(
                order.id(), menu.id(), menu.name(), menu.price(), account.pointBalance(), now));
    complete(claim, result, "ORDER_PAID", now);
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
