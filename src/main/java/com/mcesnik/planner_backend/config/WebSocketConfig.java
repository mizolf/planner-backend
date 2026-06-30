package com.mcesnik.planner_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Raw WebSocket (no SockJS) — keeps the frontend dependency to @stomp/stompjs only.
        registry.addEndpoint("/ws")
                .setAllowedOrigins("https://app-backend.com", "http://localhost:8080", "http://localhost:4200");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker; clients subscribe to /topic/trips/{tripId}.
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authenticate CONNECT and authorize SUBSCRIBE frames.
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
