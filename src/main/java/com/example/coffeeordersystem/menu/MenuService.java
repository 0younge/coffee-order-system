package com.example.coffeeordersystem.menu;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MenuService {

  private final MenuRepository menuRepository;

  MenuService(MenuRepository menuRepository) {
    this.menuRepository = menuRepository;
  }

  @Transactional(readOnly = true)
  List<MenuResponse> findAll() {
    return menuRepository.findAllByOrderByIdAsc().stream().map(MenuResponse::from).toList();
  }
}
