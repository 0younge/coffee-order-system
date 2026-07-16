package com.example.coffeeordersystem.menu.application;

import com.example.coffeeordersystem.menu.domain.PopularMenuWindow;
import com.example.coffeeordersystem.menu.infrastructure.MenuRepository;
import com.example.coffeeordersystem.menu.infrastructure.PopularMenuRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
public class MenuQueryFacade {

  private final MenuRepository menuRepository;
  private final PopularMenuRepository popularMenuRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<MenuItemResult> findAll() {
    return menuRepository.findAllByOrderByIdAsc().stream().map(MenuItemResult::from).toList();
  }

  @Transactional(readOnly = true)
  public Optional<MenuSnapshot> findById(long menuId) {
    return menuRepository.findById(menuId).map(MenuSnapshot::from);
  }

  @Transactional(readOnly = true)
  public List<PopularMenuResult> findPopular() {
    PopularMenuWindow window = PopularMenuWindow.endingAt(clock.instant());
    return PopularMenuRanking.rank(popularMenuRepository.findTopThree(window.from(), window.to()));
  }
}
