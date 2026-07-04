package com.mirage.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket (STOMP) 配置
 *
 * <p>Endpoint: /ws (原生 WebSocket, 不使用 SockJS)
 * Broker: /topic (广播), /queue (点对点)
 * App 前缀: /app</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 广播目的地前缀
        config.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息目的地前缀
        config.setApplicationDestinationPrefixes("/app");
        // 点对点目的地前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 原生 WebSocket (不使用 SockJS)，前端直接用 WebSocket 连接 /ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
