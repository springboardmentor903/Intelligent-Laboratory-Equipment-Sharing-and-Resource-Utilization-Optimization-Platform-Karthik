package com.labresource.platform.notification.repository;

import com.labresource.platform.notification.entity.ReminderDeduplicationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderDeduplicationLogRepository extends JpaRepository<ReminderDeduplicationLog, Long> {

    boolean existsByDedupKey(String dedupKey);
}
