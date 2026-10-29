package com.trading.marketdataservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * How it works :
 * 1. Clients Connect to /ws endpoints
 * 2. Clients Connect to /topic/prices{symbol}
 * 3. Server Publishes price update to that topic
 * 4. Client receives that updates in real-time no polling needed
 *
 * This is exactly how Zerodha/Robinhood/ Grow Stream real time prices
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {

    // Topics prefixed with /topic -> Broadcast to all Subscribers
        registry.enableSimpleBroker("/topic");

    // Application Destination Prefix
    // Client Messages to /app/** are routed to @MessageMapping methods
       registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // WebSocket endpoint - clients connect here
    // SockJs Fallback for Browser that don't support WebSocket

    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
  }




}
