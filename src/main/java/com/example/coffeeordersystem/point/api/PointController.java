package com.example.coffeeordersystem.point.api;

import com.example.coffeeordersystem.point.application.ChargeCommand;
import com.example.coffeeordersystem.point.application.PointChargeResult;
import com.example.coffeeordersystem.point.application.PointFacade;
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
@RequestMapping("/api/v1/points")
class PointController {

  private final PointFacade pointFacade;

  @PostMapping(
      path = "/charge",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<String> charge(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ChargeRequest request) {
    PointChargeResult result =
        pointFacade.charge(ChargeCommand.from(request.userId(), request.amount(), idempotencyKey));
    return ResponseEntity.status(result.httpStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.responseBody());
  }
}
