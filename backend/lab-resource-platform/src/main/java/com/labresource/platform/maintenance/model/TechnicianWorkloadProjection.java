package com.labresource.platform.maintenance.model;

import java.time.LocalDateTime;

public interface TechnicianWorkloadProjection {

    Long getTechnicianId();

    Long getTotalTasks();

    Long getActiveTasks();

    Long getInProgressTasks();

    Long getOverdueTasks();

    LocalDateTime getNextScheduledDate();
}
