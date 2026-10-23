package com.trading.marketdataservice.repository;

import com.trading.marketdataservice.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, String> {
  List<Stock> findByActiveTrue();
}
