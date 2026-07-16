package com.example.coffeeordersystem.order;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

  private final OrderService orderService;

  OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<JsonNode> place(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody OrderRequest request) {
    OrderResult result = orderService.place(OrderCommand.from(request, idempotencyKey));
    return ResponseEntity.status(result.httpStatus()).body(result.body());
  }
}
