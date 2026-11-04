package com.labresource.platform.equipment.websocket;

import tools.jackson.databind.ObjectMapper;
import com.labresource.platform.equipment.api.EquipmentLiveStatus;
import com.labresource.platform.equipment.event.EquipmentStatusChangedEvent;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.equipment.service.EquipmentLiveStatusService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EquipmentWebSocketHandler extends TextWebSocketHandler {
    private static final String MESSAGE_TYPE = "EQUIPMENT_STATUS_UPDATED";

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final EquipmentRepository equipmentRepository;
    private final EquipmentLiveStatusService liveStatusService;
    private final ObjectMapper objectMapper;

    public EquipmentWebSocketHandler(EquipmentRepository equipmentRepository,
                                     EquipmentLiveStatusService liveStatusService,
                                     ObjectMapper objectMapper) {
        this.equipmentRepository = equipmentRepository;
        this.liveStatusService = liveStatusService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (!(session.getPrincipal() instanceof EquipmentWebSocketPrincipal)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
            return;
        }
        sessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // This socket is server-push only; clients cannot request or mutate records over it.
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEquipmentStatusChanged(EquipmentStatusChangedEvent event) {
        var equipment = equipmentRepository.findById(event.equipmentId()).orElse(null);
        if (equipment == null) return;
        EquipmentLiveStatus snapshot;
        try {
            snapshot = liveStatusService.getOne(equipment.getId(), true);
        } catch (RuntimeException exception) {
            return;
        }
        for (WebSocketSession session : sessions.values()) {
            Principal principal = session.getPrincipal();
            if (!(principal instanceof EquipmentWebSocketPrincipal user)
                    || !canAccessEquipment(user, equipment.getInstitutionId())) continue;
            EquipmentLiveStatus visible = user.canViewManagerUsageDetails()
                    ? snapshot : snapshot.withoutManagerUsageDetails();
            var message = Map.of(
                "type", MESSAGE_TYPE,
                "equipmentId", equipment.getId(),
                "equipment", visible);
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                    }
                }
            } catch (IOException exception) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                    // Closed sessions are removed by afterConnectionClosed.
                }
            }
        }
    }

    private boolean canAccessEquipment(EquipmentWebSocketPrincipal user, Long institutionId) {
        if (user.isSystemAdmin()) return true;
        if (user.canViewManagerUsageDetails() && user.institutionId() == null) return false;
        return user.institutionId() == null || user.institutionId().equals(institutionId);
    }
}
