package com.parcelshare.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop30ByUserIdOrderByIdDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(Long userId, Instant now);
}
