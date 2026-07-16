package com.example.coffeeordersystem.point;

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
@RequestMapping("/api/v1/points")
class PointController {

  private final PointService pointService;

  PointController(PointService pointService) {
    this.pointService = pointService;
  }

  @PostMapping(path = "/charge", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<JsonNode> charge(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ChargeRequest request) {
    PointChargeResult result = pointService.charge(ChargeCommand.from(request, idempotencyKey));
    return ResponseEntity.status(result.httpStatus()).body(result.body());
  }
}
