package com.parcelshare.jobs;

import com.parcelshare.booking.Booking;
import com.parcelshare.booking.BookingRepository;
import com.parcelshare.booking.BookingService;
import com.parcelshare.booking.BookingStatus;
import com.parcelshare.common.AppProps;
import com.parcelshare.evidence.EvidenceRepository;
import com.parcelshare.parcel.Parcel;
import com.parcelshare.parcel.ParcelRepository;
import com.parcelshare.payment.Payment;
import com.parcelshare.payment.PaymentService;
import com.parcelshare.trip.Trip;
import com.parcelshare.trip.TripRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Background housekeeping. Runs in-process (no queue/Redis needed at this scale). */
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final PaymentService payments;
    private final BookingService bookingService;
    private final BookingRepository bookings;
    private final ParcelRepository parcels;
    private final TripRepository trips;
    private final AppProps props;
    private final EvidenceRepository evidence;

    /** Release money for delivered parcels that were not disputed within the window. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void releaseDuePayments() {
        for (Payment p : payments.dueForRelease(Instant.now())) {
            try {
                bookingService.autoRelease(p.getBookingId());
            } catch (Exception e) {
                log.warn("Auto-release failed for booking {}", p.getBookingId(), e);
            }
        }
    }

    /** Cancel + refund requests the traveller never answered. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    public void expireStaleRequests() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(props.requestExpiryHours()));
        for (Booking b : bookings.findByStatusAndCreatedAtBefore(BookingStatus.REQUESTED, cutoff)) {
            try {
                bookingService.expireRequest(b.getId());
            } catch (Exception e) {
                log.warn("Could not expire booking {}", b.getId(), e);
            }
        }
    }

    /** Daily: delete photos of long-finished bookings. Disputes are decided long before the retention window ends. */
    @Scheduled(fixedDelay = 86_400_000, initialDelay = 300_000)
    @Transactional
    public int purgeOldEvidence() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(props.evidenceRetentionDays()));
        int n = evidence.purgeFinished(cutoff, java.util.List.of(BookingStatus.DELIVERED, BookingStatus.RESOLVED,
                BookingStatus.CANCELLED, BookingStatus.REJECTED));
        if (n > 0) {
            log.info("Purged {} old evidence photos", n);
        }
        return n;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    @Transactional
    public void expireListings() {
        Instant now = Instant.now();
        int p = parcels.expireOlderThan(now, Parcel.Status.OPEN, Parcel.Status.EXPIRED);
        int t = trips.expireOlderThan(now.minus(Duration.ofDays(1)), Trip.Status.OPEN, Trip.Status.COMPLETED);
        if (p + t > 0) {
            log.info("Expired {} parcels and closed {} trips", p, t);
        }
    }
}
