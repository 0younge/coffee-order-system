package com.example.coffeeordersystem.menu.infrastructure;

import com.example.coffeeordersystem.menu.domain.Menu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

  List<Menu> findAllByOrderByIdAsc();
}
