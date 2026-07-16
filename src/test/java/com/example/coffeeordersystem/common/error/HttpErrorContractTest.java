package com.example.coffeeordersystem.common.error;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HttpErrorContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("AT-CONTRACT-004 라우팅 404와 405는 JSON 봉투이고 406만 빈 본문이다")
  void returnsDocumentedRoutingAndNegotiationErrors() throws Exception {
    mockMvc
        .perform(get("/api/v1/not-defined"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("요청한 경로를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.data").value(nullValue()));

    mockMvc
        .perform(post("/api/v1/menus"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.message").value("지원하지 않는 HTTP 메서드입니다."))
        .andExpect(jsonPath("$.data").value(nullValue()));

    mockMvc
        .perform(get("/api/v1/menus").accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable())
        .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
        .andExpect(content().string(""));
  }
}
