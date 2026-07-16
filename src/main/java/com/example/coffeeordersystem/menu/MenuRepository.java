package com.example.coffeeordersystem.menu;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MenuRepository extends JpaRepository<Menu, Long> {

  List<Menu> findAllByOrderByIdAsc();
}
