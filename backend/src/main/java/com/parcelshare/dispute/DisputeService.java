package com.parcelshare.dispute;

import com.parcelshare.booking.Booking;
import com.parcelshare.booking.BookingRepository;
import com.parcelshare.booking.BookingStateMachine;
import com.parcelshare.booking.BookingStatus;
import com.parcelshare.common.ApiException;
import com.parcelshare.common.AuditService;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.parcel.Parcel;
import com.parcelshare.parcel.ParcelRepository;
import com.parcelshare.payment.Payment;
import com.parcelshare.payment.PaymentService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Digital dispute workflow: raising a dispute freezes the money and the booking; an admin reviews the booking's
 * tamper-evident event timeline and chooses release / refund / partial.
 */
@Service
@RequiredArgsConstructor
public class DisputeService {

    public record DisputeView(Long id, Long bookingId, String parcelCode, String senderName, String travellerName,
                              String bookingStatus, long amountPaise, String raisedByRole, String reason, String status,
                              String resolution, Long travellerSharePaise, String adminNote, Instant createdAt,
                              Instant resolvedAt) {
    }

    private final DisputeRepository disputes;
    private final BookingRepository bookings;
    private final BookingStateMachine machine;
    private final PaymentService payments;
    private final ParcelRepository parcels;
    private final AuditService audit;

    @Transactional
    public DisputeView openByUser(AuthUser me, Long bookingId, String reason) {
        Booking b = bookings.lockById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        String role = b.senderId().equals(me.id()) ? "SENDER" : b.travellerId().equals(me.id()) ? "TRAVELLER" : null;
        if (role == null) {
            throw ApiException.forbidden("Not your booking");
        }
        return view(open(b, me.id(), role, reason));
    }

    /** The recipient has no account; their unguessable link is their credential. */
    @Transactional
    public DisputeView openByRecipient(String recipientToken, String reason) {
        Long id = bookings.findByRecipientToken(recipientToken)
                .orElseThrow(() -> ApiException.notFound("Invalid link")).getId();
        Booking b = bookings.lockById(id).orElseThrow();
        return view(open(b, null, "RECIPIENT", reason));
    }

    private Dispute open(Booking b, Long actorId, String role, String reason) {
        Payment pay = payments.forBooking(b.getId());
        payments.freezeForDispute(pay);
        machine.transition(b, BookingStatus.DISPUTED, actorId, "DISPUTE_RAISED", role + ": " + reason);
        Dispute d = new Dispute();
        d.setBookingId(b.getId());
        d.setRaisedBy(actorId);
        d.setRaisedByRole(role);
        d.setReason(reason.trim());
        return disputes.save(d);
    }

    @Transactional
    public DisputeView resolve(Long adminId, Long disputeId, Dispute.Resolution resolution, Long travellerSharePaise,
                               String note) {
        Dispute d = disputes.findById(disputeId).orElseThrow(() -> ApiException.notFound("Dispute not found"));
        if (d.getStatus() != Dispute.Status.OPEN) {
            throw ApiException.conflict("Dispute is already resolved");
        }
        Booking b = bookings.lockById(d.getBookingId()).orElseThrow();
        Payment pay = payments.forBooking(b.getId());
        long share = switch (resolution) {
            case RELEASE -> pay.getAmountPaise();
            case REFUND -> 0;
            case PARTIAL -> {
                if (travellerSharePaise == null || travellerSharePaise <= 0 || travellerSharePaise >= pay.getAmountPaise()) {
                    throw ApiException.bad("For a partial resolution, travellerSharePaise must be between 1 and "
                            + (pay.getAmountPaise() - 1));
                }
                yield travellerSharePaise;
            }
        };
        payments.settle(pay, share, adminId, "Dispute " + d.getId() + " resolved: " + resolution);
        machine.transition(b, BookingStatus.RESOLVED, adminId, "DISPUTE_RESOLVED",
                resolution + " traveller share=" + share + (note == null ? "" : "; " + note));
        parcels.transition(b.getParcel().getId(), Parcel.Status.BOOKED, Parcel.Status.CLOSED);
        if (resolution == Dispute.Resolution.REFUND) {
            AppUser t = b.getTrip().getTraveller();
            t.setDisputedCount(t.getDisputedCount() + 1);
        }
        d.setStatus(Dispute.Status.RESOLVED);
        d.setResolution(resolution);
        d.setTravellerSharePaise(share);
        d.setAdminNote(note);
        d.setResolvedBy(adminId);
        d.setResolvedAt(Instant.now());
        audit.log(adminId, "DISPUTE_RESOLVED", "dispute", d.getId(), resolution + " share=" + share);
        return view(d);
    }

    @Transactional(readOnly = true)
    public List<DisputeView> list(boolean openOnly) {
        List<Dispute> list = openOnly ? disputes.findByStatusOrderByCreatedAtAsc(Dispute.Status.OPEN)
                : disputes.findAllByOrderByCreatedAtDesc();
        return list.stream().map(this::view).toList();
    }

    private DisputeView view(Dispute d) {
        Booking b = bookings.findById(d.getBookingId()).orElseThrow();
        Payment pay = payments.forBooking(b.getId());
        return new DisputeView(d.getId(), b.getId(), b.getParcel().getPublicCode(),
                b.getParcel().getSender().getFullName(), b.getTrip().getTraveller().getFullName(),
                b.getStatus().name(), pay.getAmountPaise(), d.getRaisedByRole(), d.getReason(), d.getStatus().name(),
                d.getResolution() == null ? null : d.getResolution().name(), d.getTravellerSharePaise(),
                d.getAdminNote(), d.getCreatedAt(), d.getResolvedAt());
    }
}
