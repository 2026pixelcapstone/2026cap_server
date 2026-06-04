package com.expansion.server.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 네이티브 WebSocket 엔드포인트 (프론트 @stomp/stompjs). SockJS 미사용.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:5173",
                        "http://192.168.55.229",
                        "https://pixelpilot.art",
                        "https://www.pixelpilot.art");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 구독 토픽 prefix: /topic, 클라이언트 → 서버 전송 prefix: /app (현재 전송은 REST라 미사용)
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT 인증 + SUBSCRIBE 권한 검증
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
