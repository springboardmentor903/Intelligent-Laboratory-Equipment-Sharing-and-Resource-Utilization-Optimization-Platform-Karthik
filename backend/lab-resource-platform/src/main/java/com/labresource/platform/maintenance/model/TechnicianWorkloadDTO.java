package com.labresource.platform.maintenance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicianWorkloadDTO {

    private Long id;
    private String fullName;
    private String email;
    private Long institutionId;
    private Long departmentId;
    private String departmentName;

    private long totalTasks;
    private long activeTasks;
    private long inProgressTasks;
    private long overdueTasks;
    private LocalDateTime nextScheduledDate;
}
