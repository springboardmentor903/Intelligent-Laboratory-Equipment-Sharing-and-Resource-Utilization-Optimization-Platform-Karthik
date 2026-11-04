package com.labresource.platform.equipment.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class EquipmentWebSocketConfig implements WebSocketConfigurer {
    private final EquipmentWebSocketHandler handler;
    private final EquipmentWebSocketHandshakeInterceptor interceptor;

    public EquipmentWebSocketConfig(EquipmentWebSocketHandler handler,
                                    EquipmentWebSocketHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/equipment")
                .addInterceptors(interceptor)
                .setHandshakeHandler(new EquipmentWebSocketHandshakeHandler())
                .setAllowedOrigins("http://localhost:5173", "http://localhost:5174",
                        "http://127.0.0.1:5173", "http://127.0.0.1:5174");
    }
}
