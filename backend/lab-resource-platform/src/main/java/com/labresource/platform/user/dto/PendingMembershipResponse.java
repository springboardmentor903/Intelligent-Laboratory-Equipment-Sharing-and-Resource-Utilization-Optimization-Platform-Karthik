package com.labresource.platform.user.dto;

import java.time.LocalDateTime;

public record PendingMembershipResponse(
        Long id,
        String email,
        String fullName,
        String phoneNumber,
        Long institutionId,
        String institutionName,
        Long departmentId,
        String departmentName,
        String requestedRole,
        String membershipStatus,
        LocalDateTime createdAt,
        String rejectionReason,
        Long reviewedBy,
        LocalDateTime reviewedAt
) {
    public PendingMembershipResponse(Long id, String email, Long institutionId) {
        this(id, email, null, null, institutionId, null, null, null, "RESEARCHER_STUDENT", "PENDING", null, null, null, null);
    }
}
