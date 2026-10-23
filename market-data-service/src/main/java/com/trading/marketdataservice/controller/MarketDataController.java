package com.trading.marketdataservice.controller;

import com.trading.marketdataservice.dto.StockPrice;
import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/market")
@RequiredArgsConstructor
@Slf4j
public class MarketDataController {

    private final MarketDataService marketDataService;

    /**
     * Get All Stocks with current price
     */

    @GetMapping("/stocks")
    public ResponseEntity<List<StockPrice>> getAllStocks(){
        return ResponseEntity.ok(marketDataService.getAllPrices());
    }
    /**
     * Get Current Price for Specific Stocks
     */
    @GetMapping("/stocks/{symbol}")
    public ResponseEntity<StockPrice> getStockPrice(@PathVariable String symbol){
           return ResponseEntity.ok(marketDataService.getPrice(symbol.toUpperCase()));
    }
    /**
     * Get All Stocks
     * Shows which stocks are available for trading
     * @return
     */
    @GetMapping("/stocks/list")
    public ResponseEntity<List<Stock>> getStockList(){
        return ResponseEntity.ok(marketDataService.getAllStocks());
    }









}
