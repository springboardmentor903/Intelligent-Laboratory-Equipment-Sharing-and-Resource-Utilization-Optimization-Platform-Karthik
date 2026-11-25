package com.labresource.platform.maintenance.repository;

import com.labresource.platform.maintenance.entity.WorkOrderServiceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderServiceLogRepository extends JpaRepository<WorkOrderServiceLog, Long> {

    List<WorkOrderServiceLog> findByWorkOrderIdOrderByCreatedAtAsc(Long workOrderId);

    List<WorkOrderServiceLog> findByWorkOrderIdOrderByCreatedAtDesc(Long workOrderId);
}
