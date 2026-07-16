package com.example.coffeeordersystem.menu;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
class PopularMenuService {

  private final PopularMenuRepository popularMenuRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  List<PopularMenuResponse> findPopular() {
    PopularMenuWindow window = PopularMenuWindow.endingAt(clock.instant());
    return PopularMenuRanking.rank(popularMenuRepository.findTopThree(window.from(), window.to()));
  }
}
