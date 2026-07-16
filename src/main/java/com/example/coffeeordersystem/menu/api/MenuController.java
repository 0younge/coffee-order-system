package com.example.coffeeordersystem.menu.api;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.menu.application.MenuQueryFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/menus")
class MenuController {

  private final MenuQueryFacade menuQueryFacade;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  ApiResponse<List<MenuResponse>> findAll() {
    List<MenuResponse> responses =
        menuQueryFacade.findAll().stream().map(MenuResponse::from).toList();
    return ApiResponse.success("MENUS_RETRIEVED", "메뉴 목록을 조회했습니다.", responses);
  }

  @GetMapping(path = "/popular", produces = MediaType.APPLICATION_JSON_VALUE)
  ApiResponse<List<PopularMenuResponse>> findPopular() {
    List<PopularMenuResponse> responses =
        menuQueryFacade.findPopular().stream().map(PopularMenuResponse::from).toList();
    return ApiResponse.success("POPULAR_MENUS_RETRIEVED", "인기 메뉴를 조회했습니다.", responses);
  }
}
