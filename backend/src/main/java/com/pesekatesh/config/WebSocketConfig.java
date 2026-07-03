package com.pesekatesh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Lexohet nga application.properties -> env var ALLOWED_ORIGINS pas deploy
    // p.sh. "https://pesekatesh-frontend.onrender.com,http://localhost:4200"
    @Value("${app.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Klientët abonohen te /topic/room/{roomId} për update-e publike (broadcast)
        // dhe /user/queue/... për mesazhe private (dora e secilit lojtari)
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-pesekatesh")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .setHandshakeHandler(new PlayerIdHandshakeHandler())
                .withSockJS();                   // fallback për browser pa WebSocket nativ
    }

    /**
     * Lexon ?playerId=xxx nga URL e lidhjes dhe e vendos si Principal, kështu që
     * SimpMessagingTemplate.convertAndSendToUser(playerId, ...) funksionon pa login.
     * Në prodhim zëvendësoje me Spring Security + JWT.
     */
    static class PlayerIdHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                           Map<String, Object> attributes) {
            String query = request.getURI().getQuery();
            String playerId = UriComponentsBuilder.newInstance()
                    .query(query).build().getQueryParams().getFirst("playerId");
            final String id = (playerId != null) ? playerId : java.util.UUID.randomUUID().toString();
            return () -> id; // Principal::getName
        }
    }
}
