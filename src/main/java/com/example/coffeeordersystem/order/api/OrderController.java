package com.example.coffeeordersystem.order.api;

import com.example.coffeeordersystem.order.application.OrderCommand;
import com.example.coffeeordersystem.order.application.OrderFacade;
import com.example.coffeeordersystem.order.application.OrderResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

  private final OrderFacade orderFacade;

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<String> place(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody OrderRequest request) {
    OrderResult result =
        orderFacade.place(OrderCommand.from(request.userId(), request.menuId(), idempotencyKey));
    return ResponseEntity.status(result.httpStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.responseBody());
  }
}
