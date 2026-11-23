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
@Table(name = "sharing_listings")
public class SharingListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false, unique = true)
    private Long equipmentId;

    @Column(name = "fee_per_hour", nullable = false, precision = 12, scale = 2)
    private BigDecimal feePerHour;

    @Column(name = "external_share_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal externalSharePercentage;

    @Column(name = "minimum_notice_hours", nullable = false)
    private Integer minimumNoticeHours = 0;

    @Column(name = "maximum_booking_hours", nullable = false)
    private Integer maximumBookingHours;

    @Column(nullable = false, length = 2000)
    private String terms = "";

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Specification alias getters and setters
    public Integer getMinimumBookingNoticeHours() {
        return minimumNoticeHours;
    }

    public void setMinimumBookingNoticeHours(Integer minimumBookingNoticeHours) {
        this.minimumNoticeHours = minimumBookingNoticeHours;
    }

    public Integer getMaximumBookingDuration() {
        return maximumBookingHours;
    }

    public void setMaximumBookingDuration(Integer maximumBookingDuration) {
        this.maximumBookingHours = maximumBookingDuration;
    }

    public String getSharingTerms() {
        return terms;
    }

    public void setSharingTerms(String sharingTerms) {
        this.terms = sharingTerms;
    }
}
