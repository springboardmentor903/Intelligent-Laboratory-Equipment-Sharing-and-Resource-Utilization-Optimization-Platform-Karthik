package com.labresource.platform.notification.repository;

import com.labresource.platform.notification.entity.UserNotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserNotificationPreferencesRepository extends JpaRepository<UserNotificationPreferences, Long> {

    Optional<UserNotificationPreferences> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
