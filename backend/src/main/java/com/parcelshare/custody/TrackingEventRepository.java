package com.parcelshare.custody;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    List<TrackingEvent> findByBookingIdOrderByIdAsc(Long bookingId);

    Optional<TrackingEvent> findTopByBookingIdOrderByIdDesc(Long bookingId);

    boolean existsByBookingIdAndType(Long bookingId, String type);
}
