package com.example.coffeeordersystem.point;

import tools.jackson.databind.JsonNode;

record PointChargeResult(int httpStatus, JsonNode body, String responseBody) {}
