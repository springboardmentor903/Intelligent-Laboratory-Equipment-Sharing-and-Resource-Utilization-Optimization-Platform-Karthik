package com.labresource.platform.equipment.websocket;

import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EquipmentWebSocketHandshakeInterceptor implements HandshakeInterceptor {
    private static final String PROTOCOL = "lab-equipment-v1";
    private static final String BEARER_PREFIX = "bearer.";

    private final JwtService jwtService;
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;

    public EquipmentWebSocketHandshakeInterceptor(
            JwtService jwtService, UserRepository users,
            UserRoleRepository userRoles, RoleRepository roles) {
        this.jwtService = jwtService;
        this.users = users;
        this.userRoles = userRoles;
        this.roles = roles;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        List<String> offeredHeaders = request.getHeaders().get("Sec-WebSocket-Protocol");
        if (offeredHeaders == null || offeredHeaders.isEmpty()) return false;

        List<String> protocols = offeredHeaders.stream()
                .filter(header -> header != null && !header.isBlank())
                .flatMap(header -> Arrays.stream(header.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

        boolean supportsProtocol = protocols.stream().anyMatch(PROTOCOL::equals);
        Optional<String> token = protocols.stream()
                .filter(value -> value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length()))
                .map(value -> value.substring(BEARER_PREFIX.length()).trim())
                .filter(value -> !value.isBlank())
                .findFirst();
        if (!supportsProtocol || token.isEmpty()) return false;

        try {
            String email = jwtService.parseClaims(token.get()).get("email", String.class);
            if (email == null || email.isBlank()) return false;
            var user = users.findByEmail(email.trim().toLowerCase()).orElse(null);
            if (user == null || Boolean.FALSE.equals(user.getMembershipApproved())) return false;
            Set<String> userRoleNames = userRoles.findByUserId(user.getId()).stream()
                    .map(userRole -> roles.findById(userRole.getRoleId()))
                    .flatMap(Optional::stream)
                    .map(role -> role.getName())
                    .collect(Collectors.toUnmodifiableSet());
            attributes.put(EquipmentWebSocketPrincipal.ATTRIBUTE,
                    new EquipmentWebSocketPrincipal(user.getId(), user.getEmail(),
                            user.getInstitutionId(), userRoleNames));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No temporary handshake resources are allocated.
    }
}
