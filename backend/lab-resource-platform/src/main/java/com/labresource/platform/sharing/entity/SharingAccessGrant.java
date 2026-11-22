package com.labresource.platform.sharing.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @NoArgsConstructor @Entity @Table(name="sharing_access_grants",uniqueConstraints=@UniqueConstraint(columnNames={"agreement_id","user_id"}))
public class SharingAccessGrant {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="agreement_id",nullable=false) private Long agreementId;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(nullable=false,length=20) private String status="ACTIVE";
 @Column(name="revoked_by") private Long revokedBy;
 @Column(name="revoked_at") private LocalDateTime revokedAt;
 @Column(name="revocation_reason",length=500) private String revocationReason;
}
