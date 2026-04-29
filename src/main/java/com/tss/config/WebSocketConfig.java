package com.tss.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.tss.websocket.WebSocketEndpointJSON;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registery) {
        registery.addHandler(webSocketEndpointJSON(), "/webSocketEndpointJSON")
        .setAllowedOrigins("*")
        .setAllowedOriginPatterns("*");
    }

    @Bean
    public AbstractWebSocketHandler webSocketEndpointJSON() {
        return new WebSocketEndpointJSON();
    }
}
