package com.parcelshare.booking;

import com.parcelshare.common.ApiException;
import com.parcelshare.common.AppProps;
import com.parcelshare.custody.CodeService;
import com.parcelshare.custody.TrackingService;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.identity.UserRepository;
import com.parcelshare.identity.UserSummary;
import com.parcelshare.parcel.Parcel;
import com.parcelshare.parcel.ParcelRepository;
import com.parcelshare.parcel.ParcelView;
import com.parcelshare.payment.Payment;
import com.parcelshare.payment.PaymentService;
import com.parcelshare.payment.Pricing;
import com.parcelshare.rating.RatingRepository;
import com.parcelshare.trip.Trip;
import com.parcelshare.trip.TripRepository;
import com.parcelshare.trip.TripView;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the booking lifecycle. Every mutating method first takes a row lock on the booking, then validates
 * who is acting and what state it is in, then changes state through {@link BookingStateMachine}.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    static final int MAX_CODE_ATTEMPTS = 5;
    static final Duration HANDOVER_CODE_TTL = Duration.ofMinutes(15);

    private final BookingRepository bookings;
    private final TripRepository trips;
    private final ParcelRepository parcels;
    private final UserRepository users;
    private final PaymentService payments;
    private final BookingStateMachine machine;
    private final TrackingService tracking;
    private final CodeService codes;
    private final RatingRepository ratings;
    private final AppProps props;
    private final EntityManager em;

    // ------------------------------------------------------------------ create

    @Transactional
    public BookingView create(AuthUser me, Long tripId, Long parcelId) {
        Parcel parcel = parcels.findById(parcelId).orElseThrow(() -> ApiException.notFound("Parcel not found"));
        if (!parcel.getSender().getId().equals(me.id())) {
            throw ApiException.forbidden("This is not your parcel");
        }
        if (parcel.getStatus() == Parcel.Status.PENDING_REVIEW) {
            throw ApiException.conflict("This parcel is waiting for admin review and cannot be booked yet");
        }
        if (parcel.getStatus() != Parcel.Status.OPEN) {
            throw ApiException.conflict("This parcel is not open for booking");
        }
        Trip trip = trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip not found"));
        AppUser traveller = trip.getTraveller();
        if (traveller.getId().equals(me.id())) {
            throw ApiException.bad("You cannot book your own trip");
        }
        if (trip.getStatus() != Trip.Status.OPEN || !trip.getDepartsAt().isAfter(Instant.now())) {
            throw ApiException.conflict("This trip is no longer available");
        }
        if (traveller.getStatus() != AppUser.Status.ACTIVE
                || traveller.getTravellerVerification() != AppUser.Verification.APPROVED) {
            throw ApiException.conflict("This traveller is not available");
        }
        boolean sameRoute = trip.getOrigin().getRegion().getId().equals(parcel.getOrigin().getRegion().getId())
                && trip.getDestination().getRegion().getId().equals(parcel.getDestination().getRegion().getId());
        boolean inWindow = !trip.getDepartsAt().isBefore(parcel.getWindowStart())
                && !trip.getDepartsAt().isAfter(parcel.getWindowEnd());
        if (!sameRoute || !inWindow) {
            throw ApiException.bad("This trip does not match your parcel's route and date window");
        }

        // Overbooking guard 1: atomic capacity reservation. Guard 2: compare-and-set on the parcel status.
        // If either fails we throw, and the transaction rolls back any reservation already made.
        if (trips.reserve(tripId, parcel.getWeightGrams(), Trip.Status.OPEN) == 0) {
            throw ApiException.conflict("This trip does not have enough spare capacity for your parcel");
        }
        if (parcels.transition(parcelId, Parcel.Status.OPEN, Parcel.Status.BOOKED) == 0) {
            throw ApiException.conflict("This parcel was just booked elsewhere");
        }
        em.refresh(trip);
        em.refresh(parcel);

        long price = Pricing.quote(trip.getPricePerKgPaise(), parcel.getWeightGrams());
        Booking b = new Booking();
        b.setTrip(trip);
        b.setParcel(parcel);
        b.setPricePaise(price);
        b.setCommissionPaise(Pricing.commission(price, props.commissionPercent()));
        b.setRecipientToken(UUID.randomUUID().toString().replace("-", ""));
        b = bookings.save(b);

        payments.hold(b.getId(), price, b.getCommissionPaise());
        tracking.record(b.getId(), "BOOKING_REQUESTED", me.id(),
                "parcel=" + parcel.getPublicCode() + " pricePaise=" + price);
        return view(b, me);
    }

    // ------------------------------------------------------------------ traveller decisions

    @Transactional
    public BookingView accept(AuthUser me, Long id) {
        Booking b = lock(id);
        requireTraveller(b, me);
        machine.transition(b, BookingStatus.ACCEPTED, me.id(), "BOOKING_ACCEPTED", "");
        return view(b, me);
    }

    /** Traveller declines a request (REQUESTED), or either party cancels an accepted booking. */
    @Transactional
    public BookingView cancel(AuthUser me, Long id) {
        Booking b = lock(id);
        boolean isSender = b.senderId().equals(me.id());
        boolean isTraveller = b.travellerId().equals(me.id());
        if (!isSender && !isTraveller) {
            throw ApiException.forbidden("Not your booking");
        }
        Payment pay = payments.forBooking(b.getId());
        switch (b.getStatus()) {
            case REQUESTED -> {
                if (isTraveller) {
                    machine.transition(b, BookingStatus.REJECTED, me.id(), "BOOKING_REJECTED", "");
                } else {
                    machine.transition(b, BookingStatus.CANCELLED, me.id(), "CANCELLED_BY_SENDER", "before acceptance");
                }
                payments.settle(pay, 0, me.id(), "Full refund: booking not accepted");
            }
            case ACCEPTED -> {
                if (isSender) {
                    long fee = b.getPricePaise() * props.cancellationFeePercent() / 100;
                    machine.transition(b, BookingStatus.CANCELLED, me.id(), "CANCELLED_BY_SENDER",
                            "cancellation feePaise=" + fee);
                    payments.settle(pay, fee, me.id(), "Sender cancelled after acceptance (fee to traveller)");
                } else {
                    AppUser t = b.getTrip().getTraveller();
                    t.setCancelledCount(t.getCancelledCount() + 1);
                    machine.transition(b, BookingStatus.CANCELLED, me.id(), "CANCELLED_BY_TRAVELLER", "");
                    payments.settle(pay, 0, me.id(), "Full refund: traveller cancelled");
                }
            }
            default -> throw ApiException.conflict(
                    "A booking cannot be cancelled once the parcel has been handed over. Raise a dispute instead.");
        }
        releaseCapacityAndReopen(b);
        return view(b, me);
    }

    // ------------------------------------------------------------------ handover

    public record HandoverCode(String code, Instant expiresAt) {
    }

    /** Sender generates a short-lived single-use code (shown as text and QR) to give to the traveller in person. */
    @Transactional
    public HandoverCode issueHandoverCode(AuthUser me, Long id) {
        Booking b = lock(id);
        requireSender(b, me);
        if (b.getStatus() != BookingStatus.ACCEPTED) {
            throw ApiException.conflict("The traveller must accept the booking before handover");
        }
        String code = codes.newHandoverCode();
        Instant expires = Instant.now().plus(HANDOVER_CODE_TTL);
        b.setHandoverHash(codes.hashHandover(b.getId(), code));
        b.setHandoverExpiresAt(expires);
        b.setHandoverAttempts(0);
        tracking.record(b.getId(), "HANDOVER_CODE_ISSUED", me.id(), "");
        return new HandoverCode(code, expires);
    }

    /**
     * Traveller confirms the physical handover. They must attest that they have looked at the contents; this
     * replaces the physical seal we deliberately do not have. noRollbackFor keeps the failed-attempt counter.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public BookingView confirmHandover(AuthUser me, Long id, String code, boolean contentsChecked, String note) {
        Booking b = lock(id);
        requireTraveller(b, me);
        if (b.getStatus() != BookingStatus.ACCEPTED) {
            throw ApiException.conflict("Handover is not possible in status " + b.getStatus());
        }
        if (!contentsChecked) {
            throw ApiException.bad("You must inspect the contents and confirm they match the description before accepting the parcel");
        }
        if (b.getHandoverHash() == null || b.getHandoverExpiresAt() == null
                || b.getHandoverExpiresAt().isBefore(Instant.now())) {
            throw ApiException.bad("No valid handover code. Ask the sender to generate a new one.");
        }
        if (b.getHandoverAttempts() >= MAX_CODE_ATTEMPTS) {
            throw ApiException.conflict("Too many wrong attempts. Ask the sender to generate a new code.");
        }
        if (!codes.constantTimeEquals(b.getHandoverHash(), codes.hashHandover(b.getId(), code == null ? "" : code.trim()))) {
            b.setHandoverAttempts(b.getHandoverAttempts() + 1);
            throw ApiException.bad("Incorrect code");
        }
        b.setHandoverHash(null);
        b.setHandoverExpiresAt(null);
        machine.transition(b, BookingStatus.HANDED_OVER, me.id(), "HANDOVER_CONFIRMED",
                "contentsChecked=true" + (note == null || note.isBlank() ? "" : "; note=" + note.trim()));
        return view(b, me);
    }

    // ------------------------------------------------------------------ journey & delivery

    @Transactional
    public BookingView startJourney(AuthUser me, Long id) {
        Booking b = lock(id);
        requireTraveller(b, me);
        machine.transition(b, BookingStatus.IN_TRANSIT, me.id(), "JOURNEY_STARTED", "");
        return view(b, me);
    }

    @Transactional
    public BookingView markArrived(AuthUser me, Long id) {
        Booking b = lock(id);
        requireTraveller(b, me);
        if (b.getStatus() != BookingStatus.IN_TRANSIT) {
            throw ApiException.conflict("Arrival can only be reported while in transit");
        }
        if (!tracking.has(b.getId(), "ARRIVED")) {
            tracking.record(b.getId(), "ARRIVED", me.id(), "");
        }
        return view(b, me);
    }

    /** Recipient tells the traveller the delivery code; the traveller types it in. */
    @Transactional(noRollbackFor = ApiException.class)
    public BookingView completeDelivery(AuthUser me, Long id, String code, String note) {
        Booking b = lock(id);
        requireTraveller(b, me);
        if (b.getStatus() != BookingStatus.IN_TRANSIT) {
            throw ApiException.conflict("Delivery can only be completed while in transit");
        }
        if (b.getDeliveryAttempts() >= MAX_CODE_ATTEMPTS) {
            throw ApiException.conflict("Too many wrong attempts. Delivery is locked; use 'Recipient unavailable' or contact support.");
        }
        if (!codes.constantTimeEquals(codes.deliveryCode(b), code == null ? "" : code.trim())) {
            b.setDeliveryAttempts(b.getDeliveryAttempts() + 1);
            throw ApiException.bad("Incorrect delivery code");
        }
        machine.transition(b, BookingStatus.DELIVERED, me.id(), "DELIVERY_CONFIRMED",
                note == null ? "" : "note=" + note.trim());
        parcels.transition(b.getParcel().getId(), Parcel.Status.BOOKED, Parcel.Status.CLOSED);
        AppUser t = b.getTrip().getTraveller();
        t.setCompletedDeliveries(t.getCompletedDeliveries() + 1);

        Payment pay = payments.forBooking(b.getId());
        if (props.releaseWindowHours() <= 0) {
            payments.settle(pay, pay.getAmountPaise(), me.id(), "Delivered: released to traveller");
            tracking.record(b.getId(), "PAYMENT_RELEASED", null, "automatic");
        } else {
            // Money stays held for a dispute window; the scheduler (or sender confirmation) releases it.
            payments.scheduleRelease(pay, Instant.now().plus(Duration.ofHours(props.releaseWindowHours())));
        }
        return view(b, me);
    }

    @Transactional
    public BookingView reportDeliveryFailed(AuthUser me, Long id, String note) {
        Booking b = lock(id);
        requireTraveller(b, me);
        machine.transition(b, BookingStatus.DELIVERY_FAILED, me.id(), "DELIVERY_FAILED",
                note == null ? "recipient unavailable" : "note=" + note.trim());
        return view(b, me);
    }

    @Transactional
    public BookingView retryDelivery(AuthUser me, Long id) {
        Booking b = lock(id);
        requireTraveller(b, me);
        if (b.getStatus() != BookingStatus.DELIVERY_FAILED) {
            throw ApiException.conflict("Nothing to retry");
        }
        b.setDeliveryAttempts(0);
        machine.transition(b, BookingStatus.IN_TRANSIT, me.id(), "DELIVERY_RETRY", "");
        return view(b, me);
    }

    /** Sender confirms they (or the recipient) are happy: release the money now instead of waiting. */
    @Transactional
    public BookingView confirmReceipt(AuthUser me, Long id) {
        Booking b = lock(id);
        requireSender(b, me);
        if (b.getStatus() != BookingStatus.DELIVERED) {
            throw ApiException.conflict("The parcel has not been delivered yet");
        }
        Payment pay = payments.forBooking(b.getId());
        payments.settle(pay, pay.getAmountPaise(), me.id(), "Sender confirmed receipt");
        tracking.record(b.getId(), "PAYMENT_RELEASED", me.id(), "sender confirmed receipt");
        return view(b, me);
    }

    // ------------------------------------------------------------------ system jobs

    /** Called by the scheduler for deliveries nobody disputed within the window. */
    @Transactional
    public void autoRelease(Long bookingId) {
        Booking b = lock(bookingId);
        Payment pay = payments.forBooking(bookingId);
        if (b.getStatus() == BookingStatus.DELIVERED && pay.getStatus() == Payment.Status.HELD) {
            payments.settle(pay, pay.getAmountPaise(), null, "Dispute window elapsed: auto-release");
            tracking.record(bookingId, "PAYMENT_RELEASED", null, "dispute window elapsed");
        }
    }

    /** Called by the scheduler when a traveller never answered a request. */
    @Transactional
    public void expireRequest(Long bookingId) {
        Booking b = lock(bookingId);
        if (b.getStatus() != BookingStatus.REQUESTED) {
            return;
        }
        machine.transition(b, BookingStatus.CANCELLED, null, "REQUEST_EXPIRED", "traveller did not respond in time");
        payments.settle(payments.forBooking(b.getId()), 0, null, "Full refund: request expired");
        releaseCapacityAndReopen(b);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<BookingView> mine(AuthUser me) {
        return bookings.forUser(me.id()).stream().map(b -> view(b, me)).toList();
    }

    @Transactional(readOnly = true)
    public BookingView get(AuthUser me, Long id) {
        Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
        requireParty(b, me);
        return view(b, me);
    }

    @Transactional(readOnly = true)
    public List<TrackingService.EventView> events(AuthUser me, Long id) {
        Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
        requireParty(b, me);
        return tracking.timeline(id);
    }

    @Transactional(readOnly = true)
    public TrackingService.Verification verifyChain(AuthUser me, Long id) {
        Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
        requireParty(b, me);
        return tracking.verify(id);
    }

    // ------------------------------------------------------------------ helpers

    Booking lock(Long id) {
        return bookings.lockById(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
    }

    private void releaseCapacityAndReopen(Booking b) {
        trips.release(b.getTrip().getId(), b.getParcel().getWeightGrams());
        parcels.transition(b.getParcel().getId(), Parcel.Status.BOOKED, Parcel.Status.OPEN);
    }

    private void requireSender(Booking b, AuthUser me) {
        if (!b.senderId().equals(me.id())) {
            throw ApiException.forbidden("Only the sender can do this");
        }
    }

    private void requireTraveller(Booking b, AuthUser me) {
        if (!b.travellerId().equals(me.id())) {
            throw ApiException.forbidden("Only the traveller can do this");
        }
    }

    private void requireParty(Booking b, AuthUser me) {
        if (!me.isAdmin() && !b.senderId().equals(me.id()) && !b.travellerId().equals(me.id())) {
            throw ApiException.forbidden("Not your booking");
        }
    }

    public BookingView view(Booking b, AuthUser viewer) {
        boolean isSender = b.senderId().equals(viewer.id());
        boolean isTraveller = b.travellerId().equals(viewer.id());
        String role = isSender ? "SENDER" : isTraveller ? "TRAVELLER" : "ADMIN";
        Payment pay = payments.forBooking(b.getId());
        BookingStatus s = b.getStatus();
        boolean contactVisible = s != BookingStatus.REQUESTED && s != BookingStatus.REJECTED;
        AppUser counterpart = isSender ? b.getTrip().getTraveller() : b.getParcel().getSender();

        String deliveryCode = null;
        String recipientLink = null;
        if (isSender) {
            recipientLink = "/receive/" + b.getRecipientToken();
            if (s == BookingStatus.ACCEPTED || s == BookingStatus.HANDED_OVER || s == BookingStatus.IN_TRANSIT
                    || s == BookingStatus.DELIVERY_FAILED) {
                deliveryCode = codes.deliveryCode(b);
            }
        }
        long payout = b.getPricePaise() - b.getCommissionPaise();
        return new BookingView(b.getId(), ParcelView.of(b.getParcel(), isSender || isTraveller),
                TripView.of(b.getTrip()), UserSummary.of(b.getParcel().getSender()),
                UserSummary.of(b.getTrip().getTraveller()), s.name(), b.getPricePaise(), b.getCommissionPaise(), payout,
                pay.getStatus().name(), pay.getReleaseAt(), role, actions(b, pay, isSender, isTraveller, viewer),
                deliveryCode, recipientLink,
                contactVisible && !"ADMIN".equals(role) ? counterpart.getPhone() : null,
                isSender && s == BookingStatus.ACCEPTED ? b.getHandoverExpiresAt() : null, b.getCreatedAt());
    }

    private List<String> actions(Booking b, Payment pay, boolean isSender, boolean isTraveller, AuthUser viewer) {
        List<String> a = new ArrayList<>();
        BookingStatus s = b.getStatus();
        boolean held = pay.getStatus() == Payment.Status.HELD;
        boolean canRate = (s == BookingStatus.DELIVERED || s == BookingStatus.RESOLVED)
                && (isSender || isTraveller) && !ratings.existsByBookingIdAndRaterId(b.getId(), viewer.id());
        if (isSender) {
            switch (s) {
                case REQUESTED -> a.add("CANCEL");
                case ACCEPTED -> {
                    a.add("GENERATE_HANDOVER_CODE");
                    a.add("CANCEL");
                }
                case HANDED_OVER, IN_TRANSIT, DELIVERY_FAILED -> a.add("RAISE_DISPUTE");
                case DELIVERED -> {
                    if (held) {
                        a.add("CONFIRM_RECEIPT");
                        a.add("RAISE_DISPUTE");
                    }
                }
                default -> { }
            }
        }
        if (isTraveller) {
            switch (s) {
                case REQUESTED -> {
                    a.add("ACCEPT");
                    a.add("REJECT");
                }
                case ACCEPTED -> {
                    a.add("CONFIRM_HANDOVER");
                    a.add("CANCEL");
                }
                case HANDED_OVER -> {
                    a.add("START_JOURNEY");
                    a.add("RAISE_DISPUTE");
                }
                case IN_TRANSIT -> {
                    if (!tracking.has(b.getId(), "ARRIVED")) {
                        a.add("MARK_ARRIVED");
                    }
                    a.add("COMPLETE_DELIVERY");
                    a.add("DELIVERY_FAILED");
                    a.add("RAISE_DISPUTE");
                }
                case DELIVERY_FAILED -> {
                    a.add("RETRY_DELIVERY");
                    a.add("RAISE_DISPUTE");
                }
                case DELIVERED -> {
                    if (held) {
                        a.add("RAISE_DISPUTE");
                    }
                }
                default -> { }
            }
        }
        if (canRate) {
            a.add("RATE");
        }
        return a;
    }
}
