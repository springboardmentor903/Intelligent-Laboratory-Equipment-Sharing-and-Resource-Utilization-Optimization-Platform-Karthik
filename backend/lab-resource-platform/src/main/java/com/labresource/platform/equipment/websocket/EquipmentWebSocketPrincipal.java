package com.labresource.platform.equipment.websocket;

import java.security.Principal;
import java.util.Set;

public record EquipmentWebSocketPrincipal(
        Long userId, String email, Long institutionId, Set<String> roles) implements Principal {
    public static final String ATTRIBUTE = "equipmentWebSocketPrincipal";

    @Override
    public String getName() {
        return email;
    }

    public boolean isSystemAdmin() {
        return roles.contains("SYSTEM_ADMIN");
    }

    public boolean canViewManagerUsageDetails() {
        return roles.stream().anyMatch(Set.of(
                "LAB_MANAGER", "INSTITUTION_ADMIN", "SYSTEM_ADMIN")::contains);
    }
}
