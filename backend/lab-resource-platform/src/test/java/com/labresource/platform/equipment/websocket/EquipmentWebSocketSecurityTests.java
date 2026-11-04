package com.labresource.platform.equipment.websocket;

import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.equipment.api.EquipmentLiveStatus;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.event.EquipmentStatusChangedEvent;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.equipment.service.EquipmentLiveStatusService;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class EquipmentWebSocketSecurityTests {
    private static final String JWT_SECRET = "VGhpcyBpcyBhIHRlc3Qgc2VjcmV0IGtleSBmb3IgSldU";
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final JwtService jwtService = new JwtService(JWT_SECRET, 60_000);
    private final EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
    private final EquipmentLiveStatusService liveStatusService = mock(EquipmentLiveStatusService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EquipmentWebSocketHandshakeInterceptor interceptor =
            new EquipmentWebSocketHandshakeInterceptor(jwtService, users, userRoles, roles);
    private final EquipmentWebSocketHandler handler =
            new EquipmentWebSocketHandler(equipmentRepository, liveStatusService, objectMapper);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail("researcher@example.test");
        user.setInstitutionId(4L);
        user.setMembershipApproved(true);
    }

    @Test
    void websocketHandshakeRejectsMissingAndInvalidAuthenticationProtocols() {
        ServerHttpRequest request = requestWithProtocols(null);
        assertThat(interceptor.beforeHandshake(request, mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), new java.util.HashMap<>())).isFalse();

        request = requestWithProtocols("lab-equipment-v1, bearer.invalid-token");
        assertThat(interceptor.beforeHandshake(request, mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), new java.util.HashMap<>())).isFalse();
    }

    @Test
    void websocketHandshakeAuthenticatesTokenAndResolvesRolesFromTheDatabase() {
        Role role = new Role("RESEARCHER_STUDENT", "Researcher");
        role.setId((short) 2);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRoles.findByUserId(user.getId())).thenReturn(List.of(new UserRole(user.getId(), (short) 2)));
        when(roles.findById((short) 2)).thenReturn(Optional.of(role));

        Map<String, Object> attributes = new java.util.HashMap<>();
        String protocols = "lab-equipment-v1, bearer." + jwtService.generateToken(user);
        boolean accepted = interceptor.beforeHandshake(requestWithProtocols(protocols),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(EquipmentWebSocketPrincipal.ATTRIBUTE))
                .isInstanceOf(EquipmentWebSocketPrincipal.class);
        EquipmentWebSocketPrincipal principal =
                (EquipmentWebSocketPrincipal) attributes.get(EquipmentWebSocketPrincipal.ATTRIBUTE);
        assertThat(principal.getName()).isEqualTo(user.getEmail());
        assertThat(principal.roles()).containsExactly("RESEARCHER_STUDENT");
    }

    @Test
    void websocketHandshakeRejectsPendingMembershipEvenWithAValidToken() {
        user.setMembershipApproved(false);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        String protocols = "lab-equipment-v1, bearer." + jwtService.generateToken(user);

        boolean accepted = interceptor.beforeHandshake(requestWithProtocols(protocols),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new java.util.HashMap<>());

        assertThat(accepted).isFalse();
    }

    @Test
    void publicEquipmentEventsAreScopedAndDoNotIncludeManagerUsageDetails() throws Exception {
        Equipment equipment = equipment(15L, 4L);
        EquipmentLiveStatus snapshot = snapshot(new EquipmentLiveStatus.ManagerUsageDetails(
                91L, "BOOKING", "private manager note"));
        when(equipmentRepository.findById(15L)).thenReturn(Optional.of(equipment));
        when(liveStatusService.getOne(15L, true)).thenReturn(snapshot);

        WebSocketSession session = connectedSession("student", 4L, Set.of("RESEARCHER_STUDENT"));
        handler.afterConnectionEstablished(session);
        handler.onEquipmentStatusChanged(new EquipmentStatusChangedEvent(15L));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        var message = objectMapper.readTree(sent.getValue().getPayload());
        assertThat(message.get("type").asString()).isEqualTo("EQUIPMENT_STATUS_UPDATED");
        assertThat(message.get("equipmentId").asLong()).isEqualTo(15L);
        assertThat(message.get("equipment").get("managerUsageDetails").isNull()).isTrue();
        assertThat(sent.getValue().getPayload()).doesNotContain("researcher@example.test");
        assertThat(sent.getValue().getPayload()).doesNotContain("private manager note");
    }

    @Test
    void managerUsageDetailsAreSentOnlyToManagersInTheEquipmentInstitution() throws Exception {
        Equipment equipment = equipment(15L, 4L);
        EquipmentLiveStatus snapshot = snapshot(new EquipmentLiveStatus.ManagerUsageDetails(
                91L, "BOOKING", "manager note"));
        when(equipmentRepository.findById(15L)).thenReturn(Optional.of(equipment));
        when(liveStatusService.getOne(15L, true)).thenReturn(snapshot);

        WebSocketSession manager = connectedSession("manager", 4L, Set.of("LAB_MANAGER"));
        handler.afterConnectionEstablished(manager);
        handler.onEquipmentStatusChanged(new EquipmentStatusChangedEvent(15L));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(manager).sendMessage(sent.capture());
        assertThat(objectMapper.readTree(sent.getValue().getPayload())
                .get("equipment").get("managerUsageDetails").get("remarks").asString())
                .isEqualTo("manager note");

        WebSocketSession otherInstitution = connectedSession("other-manager", 99L, Set.of("LAB_MANAGER"));
        handler.afterConnectionEstablished(otherInstitution);
        handler.onEquipmentStatusChanged(new EquipmentStatusChangedEvent(15L));
        verify(otherInstitution, never()).sendMessage(any());
    }

    private ServerHttpRequest requestWithProtocols(String protocols) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        if (protocols != null) headers.set("Sec-WebSocket-Protocol", protocols);
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    private WebSocketSession connectedSession(String id, Long institutionId, Set<String> roleNames) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getPrincipal()).thenReturn(new EquipmentWebSocketPrincipal(
                17L, id + "@example.test", institutionId, roleNames));
        return session;
    }

    private Equipment equipment(Long id, Long institutionId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setName("Microscope");
        equipment.setInstitutionId(institutionId);
        equipment.setStatus("BOOKED");
        return equipment;
    }

    private EquipmentLiveStatus snapshot(EquipmentLiveStatus.ManagerUsageDetails details) {
        return new EquipmentLiveStatus(15L, "Microscope", "MIC-015", "BOOKED", true,
                java.time.LocalDateTime.of(2026, 11, 1, 8, 0),
                java.time.LocalDateTime.of(2026, 11, 1, 9, 0), 600L,
                new EquipmentLiveStatus.BookingWindow(22L,
                        java.time.LocalDateTime.of(2026, 11, 1, 10, 0),
                        java.time.LocalDateTime.of(2026, 11, 1, 11, 0)), details);
    }

    @Test
    void websocketHandshakeHandlerConfiguresAndSelectsProtocol() {
        EquipmentWebSocketHandshakeHandler handshakeHandler = new EquipmentWebSocketHandshakeHandler();
        assertThat(handshakeHandler.getSupportedProtocols()).containsExactly("lab-equipment-v1");
    }

    @Test
    void websocketHandshakeAuthenticatesTokenAcrossMultipleProtocolHeaders() {
        Role role = new Role("RESEARCHER_STUDENT", "Researcher");
        role.setId((short) 2);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRoles.findByUserId(user.getId())).thenReturn(List.of(new UserRole(user.getId(), (short) 2)));
        when(roles.findById((short) 2)).thenReturn(Optional.of(role));

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "lab-equipment-v1");
        headers.add("Sec-WebSocket-Protocol", "bearer." + jwtService.generateToken(user));
        when(request.getHeaders()).thenReturn(headers);

        Map<String, Object> attributes = new java.util.HashMap<>();
        boolean accepted = interceptor.beforeHandshake(request,
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(EquipmentWebSocketPrincipal.ATTRIBUTE))
                .isInstanceOf(EquipmentWebSocketPrincipal.class);
    }

    @Test
    void websocketHandshakeAuthenticatesTokenWithCaseInsensitivePrefixAndWhitespace() {
        Role role = new Role("LAB_MANAGER", "Lab Manager");
        role.setId((short) 3);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRoles.findByUserId(user.getId())).thenReturn(List.of(new UserRole(user.getId(), (short) 3)));
        when(roles.findById((short) 3)).thenReturn(Optional.of(role));

        String protocols = "  lab-equipment-v1  ,   BEARER." + jwtService.generateToken(user) + "  ";
        Map<String, Object> attributes = new java.util.HashMap<>();
        boolean accepted = interceptor.beforeHandshake(requestWithProtocols(protocols),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        EquipmentWebSocketPrincipal principal =
                (EquipmentWebSocketPrincipal) attributes.get(EquipmentWebSocketPrincipal.ATTRIBUTE);
        assertThat(principal.canViewManagerUsageDetails()).isTrue();
    }

    @Test
    void websocketHandshakeRejectsWhenRequiredProtocolMissingEvenIfTokenPresent() {
        String protocols = "bearer." + jwtService.generateToken(user);
        boolean accepted = interceptor.beforeHandshake(requestWithProtocols(protocols),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new java.util.HashMap<>());
        assertThat(accepted).isFalse();
    }

    @Test
    void websocketHandshakeRejectsBlankToken() {
        String protocols = "lab-equipment-v1, bearer.   ";
        boolean accepted = interceptor.beforeHandshake(requestWithProtocols(protocols),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new java.util.HashMap<>());
        assertThat(accepted).isFalse();
    }
}
