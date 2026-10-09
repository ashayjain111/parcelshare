package com.parcelshare.booking;

import com.parcelshare.common.ApiException;
import com.parcelshare.custody.TrackingService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The single place where a booking changes state. Every change also writes a tracking event. */
@Component
@RequiredArgsConstructor
public class BookingStateMachine {

    private final TrackingService tracking;

    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(Booking b, BookingStatus to, Long actorId, String eventType, String payload) {
        if (!b.getStatus().canGoTo(to)) {
            throw ApiException.conflict("Cannot move booking from " + b.getStatus() + " to " + to);
        }
        b.setStatus(to);
        b.setUpdatedAt(Instant.now());
        tracking.record(b.getId(), eventType, actorId, payload);
    }
}
