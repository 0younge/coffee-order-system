package com.example.coffeeordersystem.menu;

import com.example.coffeeordersystem.common.api.ApiResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menus")
class MenuController {

  private final MenuService menuService;
  private final PopularMenuService popularMenuService;

  MenuController(MenuService menuService, PopularMenuService popularMenuService) {
    this.menuService = menuService;
    this.popularMenuService = popularMenuService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  ApiResponse<List<MenuResponse>> findAll() {
    return ApiResponse.success("MENUS_RETRIEVED", "메뉴 목록을 조회했습니다.", menuService.findAll());
  }

  @GetMapping(path = "/popular", produces = MediaType.APPLICATION_JSON_VALUE)
  ApiResponse<List<PopularMenuResponse>> findPopular() {
    return ApiResponse.success(
        "POPULAR_MENUS_RETRIEVED", "인기 메뉴를 조회했습니다.", popularMenuService.findPopular());
  }
}
