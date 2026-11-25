package com.labresource.platform.maintenance.model;

import com.labresource.platform.maintenance.entity.WorkOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicianTasksResponse {

    private List<WorkOrder> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private Map<String, Long> counts;
    private Map<String, List<WorkOrder>> taskGroups;
}
