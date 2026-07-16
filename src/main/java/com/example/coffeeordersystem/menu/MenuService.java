package com.example.coffeeordersystem.menu;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
public class MenuService {

  private final MenuRepository menuRepository;

  @Transactional(readOnly = true)
  List<MenuResponse> findAll() {
    return menuRepository.findAllByOrderByIdAsc().stream().map(MenuResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public Optional<MenuResponse> findById(long menuId) {
    return menuRepository.findById(menuId).map(MenuResponse::from);
  }
}
