package com.labresource.platform.sharing.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter; import java.math.BigDecimal; import java.time.LocalDateTime; import java.time.LocalTime;
@Getter @Setter @NoArgsConstructor @Entity @Table(name="sharing_agreements")
public class SharingAgreement {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="equipment_id",nullable=false) private Long equipmentId;
 @Column(name="owner_institution_id",nullable=false) private Long ownerInstitutionId;
 @Column(name="partner_institution_id",nullable=false) private Long partnerInstitutionId;
 @Column(name="request_id",nullable=false,unique=true) private Long requestId;
 @Column(name="start_date",nullable=false) private LocalDateTime startDate;
 @Column(name="end_date",nullable=false) private LocalDateTime endDate;
 @Column(name="allowed_start_time") private LocalTime allowedStartTime;
 @Column(name="allowed_end_time") private LocalTime allowedEndTime;
 @Column(name="fee_per_hour",nullable=false,precision=12,scale=2) private BigDecimal feePerHour;
 @Column(nullable=false,length=20) private String status="ACTIVE";
 @Column(name="approved_by",nullable=false) private Long approvedBy;
 @Column(name="approved_at",nullable=false) private LocalDateTime approvedAt=LocalDateTime.now();
 @Column(name="revoked_by") private Long revokedBy;
 @Column(name="revoked_at") private LocalDateTime revokedAt;
 @Column(name="revocation_reason",length=500) private String revocationReason;
}
