package com.labresource.platform.billing.entity;

import com.labresource.platform.billing.model.ChargeStatus;
import com.labresource.platform.billing.model.ChargeType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "usage_charges", uniqueConstraints = {
        @UniqueConstraint(name = "uk_usage_charges_booking", columnNames = {"booking_id"})
})
public class UsageCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(name = "user_institution_id")
    private Long userInstitutionId;

    @Column(name = "department_id")
    private Long departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 30)
    private ChargeType chargeType;

    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "usage_minutes", nullable = false)
    private Long usageMinutes;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ChargeStatus status = ChargeStatus.PENDING;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "sharing_agreement_id")
    private Long sharingAgreementId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
