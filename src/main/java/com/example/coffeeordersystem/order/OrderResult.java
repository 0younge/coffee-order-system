package com.example.coffeeordersystem.order;

import tools.jackson.databind.JsonNode;

record OrderResult(int httpStatus, JsonNode body, String responseBody) {}
