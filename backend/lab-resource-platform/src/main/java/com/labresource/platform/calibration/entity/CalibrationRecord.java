package com.labresource.platform.calibration.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.labresource.platform.equipment.entity.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "calibration_records")
public class CalibrationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "calibration_date")
    private LocalDate calibrationDate;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(length = 30)
    private String result;

    @Column(length = 1000)
    private String notes;

    @Column(name = "status", length = 30)
    private String status = "COMPLETED";

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "certificate_number", length = 100)
    private String certificateNumber;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @JsonProperty(value = "equipmentId", access = JsonProperty.Access.READ_ONLY)
    public Long getEquipmentId() {
        return equipment == null ? null : equipment.getId();
    }

    @JsonProperty(value = "equipmentName", access = JsonProperty.Access.READ_ONLY)
    public String getEquipmentName() {
        return equipment == null ? null : equipment.getName();
    }
}
