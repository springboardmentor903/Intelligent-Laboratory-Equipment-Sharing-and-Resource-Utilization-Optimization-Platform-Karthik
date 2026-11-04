package com.labresource.platform.equipment.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

public class EquipmentWebSocketHandshakeHandler extends DefaultHandshakeHandler {
    public EquipmentWebSocketHandshakeHandler() {
        super();
        setSupportedProtocols("lab-equipment-v1");
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                     Map<String, Object> attributes) {
        return (Principal) attributes.get(EquipmentWebSocketPrincipal.ATTRIBUTE);
    }
}
