package com.labresource.platform.sharing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sharing_requests")
public class SharingRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "requester_institution_id", nullable = false)
    private Long requesterInstitutionId;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "requester_reviewed_by")
    private Long requesterReviewedBy;

    @Column(name = "requester_reviewed_at")
    private LocalDateTime requesterReviewedAt;

    @Column(name = "requester_review_remarks", length = 500)
    private String requesterReviewRemarks;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_remarks", length = 500)
    private String reviewRemarks;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private String equipmentName;

    @Transient
    private String assetTag;

    @Transient
    private String partnerInstitution;

    @Transient
    private String requesterName;

    @Transient
    private String requesterEmail;

    @Transient
    private BigDecimal feePerHour;

    public String getRequester_review_remarks() {
        return requesterReviewRemarks;
    }
}
