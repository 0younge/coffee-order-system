package com.example.coffeeordersystem.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RequestHasher {

  public String hash(IdempotencyOperation operation, long value) {
    String canonicalRequest = operation.name() + "|" + value;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK가 SHA-256을 지원하지 않습니다.", exception);
    }
  }
}
