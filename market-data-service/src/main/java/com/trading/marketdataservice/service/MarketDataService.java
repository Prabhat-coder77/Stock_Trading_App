package com.trading.marketdataservice.service;

import com.trading.marketdataservice.dto.StockPrice;
import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataService {

    private final StockRepository stockRepository;
    private final Random random = new Random();
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PRICE_KEY_PREFIX = "stock:price";
    private static final String PRICE_UPDATED_TOPIC = "stock.price.updated";


    /**
     * ConcurrentHashMap- For Thread Safety
     */
    private final Map<String, StockPrice> stockPrices = new ConcurrentHashMap<>();


    /**
     * Initialize Stock prices
     * @PostConstruct -> it runs when context is ready
     */
    public void InitializePrices(){
        log.info("Loading Stock Data.....");
        List<Stock> stocks =stockRepository.findByActiveTrue();
        if(stocks.isEmpty()){
            log.warn("Stock Data not found");
            return;
        }
        stocks.forEach(stock -> {
            StockPrice stockPrice = new StockPrice();
            stockPrice.setSymbol(stock.getSymbol());
            stockPrice.setCompanyName(stock.getCompanyName());
            stockPrice.setPrice(stock.getInitialPrice());
            stockPrice.setOpen(stock.getInitialPrice());
            stockPrice.setHigh(stock.getInitialPrice());
            stockPrice.setLow(stock.getInitialPrice());
            stockPrice.setChange(BigDecimal.ZERO);
            stockPrice.setChangePercent(BigDecimal.ZERO);
            stockPrice.setVolume(0L);
            stockPrice.setTimestamp(LocalDateTime.now());
            stockPrices.put(stock.getSymbol(), stockPrice);
            log.info("Loaded Stock: {} at {}" + stock.getSymbol(), stock.getInitialPrice(), stock.getInitialPrice());
        });
        log.info("Initialized {} Stocks", stocks.size());
    }
    /**
     * Scheduled Price Update - Every 2 Second
     */
    @Scheduled(fixedRateString = "${market.price-update-interval}")
    public void updatePrices(){
        if(stockPrices.isEmpty()){
            log.warn("No stock loaded");
            return;
        }
        stockPrices.forEach((symbol, currentPrice) -> {
           BigDecimal oldPrice = currentPrice.getPrice();
           //0.5 price movement
            double priceMovement = (random.nextDouble() -0.5) * 0.5;

            BigDecimal priceChange =
                    oldPrice.multiply(BigDecimal.valueOf(priceMovement/100))
                            .setScale(2, RoundingMode.HALF_UP);
            BigDecimal newPrice = oldPrice
                    .add(priceChange)
                    .setScale(2, RoundingMode.HALF_UP);
            // Prevent Negative prices
            if(newPrice.compareTo(BigDecimal.valueOf(1)) <= 0){
                newPrice = BigDecimal.valueOf(1);
            }

            BigDecimal changeAmount = newPrice
                    .subtract(currentPrice.getOpen())
            .setScale(1, RoundingMode.HALF_UP);

            BigDecimal changePercent = changeAmount
                    .divide(currentPrice.getOpen(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // Update High / Low
            BigDecimal newHigh = newPrice
                    .compareTo(currentPrice.getHigh()) > 0
                    ? newPrice : currentPrice.getHigh();
            BigDecimal newLow = newPrice
                    .compareTo(currentPrice.getLow()) < 0
                    ? newPrice : currentPrice.getLow();

            currentPrice.setPrice(newPrice);
            currentPrice.setChange(changeAmount);
            currentPrice.setChangePercent(changePercent);
            currentPrice.setHigh(newHigh);
            currentPrice.setLow(newLow);
            currentPrice.setTimestamp(LocalDateTime.now());

            // 1. cache for redis
            redisTemplate.opsForValue().set(
                    PRICE_KEY_PREFIX + symbol,currentPrice );

            // Broadcaste Via WebSocket -> All Clients
            messagingTemplate.convertAndSend(
                    "/topic/prices" + symbol, currentPrice);
            //Publish To Kafka
            Map<String, Object> priceUpdatedEvent= new HashMap<>();
            priceUpdatedEvent.put("symbol", symbol);
            priceUpdatedEvent.put("price", newPrice);
            priceUpdatedEvent.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(PRICE_UPDATED_TOPIC, symbol, priceUpdatedEvent);

        });
        log.debug("Prices updated for {} stocks ", stockPrices.size());

    }
    /**
     * Get current Price from Redis
     */
     public StockPrice getPrice(String symbol){
        Object cached = redisTemplate.opsForValue().get(PRICE_KEY_PREFIX + symbol);
        if(cached != null){
            return (StockPrice) cached;
        }
        // FallBack
         StockPrice price = stockPrices.get(symbol.toUpperCase());

        if(price == null){
            throw new RuntimeException("No stock price found for symbol: " + symbol);
        }
        return price;
    }

    /**
     * Get All Active Stock Prices
     * @return
     */

    public List<StockPrice> getAllPrices(){
        return stockPrices.values().stream().toList();
    }
    /**
     *  Get All Stocks
     */
    public List<Stock> getAllStocks(){
           return stockRepository.findByActiveTrue();
    }






}
