package com.parcelshare.identity;

import com.parcelshare.booking.Booking;
import com.parcelshare.booking.BookingRepository;
import com.parcelshare.booking.BookingService;
import com.parcelshare.booking.BookingStatus;
import com.parcelshare.common.ApiException;
import com.parcelshare.common.AuditService;
import com.parcelshare.notification.NotificationRepository;
import com.parcelshare.parcel.Parcel;
import com.parcelshare.parcel.ParcelRepository;
import com.parcelshare.parcel.ParcelView;
import com.parcelshare.payment.Payment;
import com.parcelshare.payment.PaymentService;
import com.parcelshare.rating.RatingRepository;
import com.parcelshare.trip.Trip;
import com.parcelshare.trip.TripRepository;
import com.parcelshare.trip.TripView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data rights: download everything we hold about you, and delete your account.
 * Deletion anonymises the account instead of removing the row, so other people's booking history, payments and
 * disputes stay intact (they may still need them), but nothing identifies you any more.
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class AccountController {

    public record DeleteRequest(@NotBlank String password) {
    }

    private final UserRepository users;
    private final ParcelRepository parcels;
    private final TripRepository trips;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final PaymentService payments;
    private final RatingRepository ratings;
    private final NotificationRepository notifications;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public Map<String, Object> export(@AuthenticationPrincipal AuthUser me) {
        AppUser u = users.findById(me.id()).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now());
        out.put("account", UserDto.of(u));
        out.put("parcelsSent", parcels.findBySenderIdOrderByCreatedAtDesc(me.id()).stream().map(p -> ParcelView.of(p, true)).toList());
        out.put("tripsPosted", trips.findByTravellerIdOrderByDepartsAtDesc(me.id()).stream().map(TripView::of).toList());
        out.put("bookings", bookingService.mine(me));
        out.put("reviewsAboutYou", ratings.findTop30ByRateeIdOrderByIdDesc(me.id()).stream()
                .map(r -> Map.of("score", r.getScore(), "comment", String.valueOf(r.getComment()), "at", r.getCreatedAt())).toList());
        out.put("notifications", notifications.findTop30ByUserIdOrderByIdDesc(me.id()).stream()
                .map(n -> Map.of("title", n.getTitle(), "at", n.getCreatedAt())).toList());
        return out;
    }

    @PostMapping("/delete")
    @Transactional
    public Map<String, String> delete(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody DeleteRequest req) {
        AppUser u = users.findById(me.id()).orElseThrow();
        if (u.getRole() == AppUser.Role.ADMIN) {
            throw ApiException.bad("Admin accounts cannot be deleted from here");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw ApiException.bad("Your password is incorrect");
        }
        for (Booking b : bookings.forUser(me.id())) {
            if (isUnfinished(b)) {
                throw ApiException.conflict("You still have an unfinished booking (" + b.getParcel().getPublicCode()
                        + "). Finish, cancel or resolve it first, then delete your account.");
            }
        }
        // nothing is in progress, so open listings can simply be withdrawn
        for (Parcel p : parcels.findBySenderIdOrderByCreatedAtDesc(me.id())) {
            if (p.getStatus() == Parcel.Status.OPEN || p.getStatus() == Parcel.Status.PENDING_REVIEW) {
                p.setStatus(Parcel.Status.CANCELLED);
            }
            p.setRecipientName("Removed");
            p.setRecipientPhone(null);
        }
        for (Trip t : trips.findByTravellerIdOrderByDepartsAtDesc(me.id())) {
            if (t.getStatus() == Trip.Status.OPEN) {
                t.setStatus(Trip.Status.CANCELLED);
            }
        }
        notifications.deleteByUserId(me.id());

        u.setEmail("deleted-" + u.getId() + "@deleted.invalid");
        u.setFullName("Deleted user");
        u.setPhone(null);
        u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        u.setVerifyToken(null);
        u.setResetTokenHash(null);
        u.setResetExpiresAt(null);
        u.setVerificationNote(null);
        u.setStatus(AppUser.Status.DELETED);
        u.setDeletedAt(Instant.now());
        u.setTokenVersion(u.getTokenVersion() + 1);
        audit.log(me.id(), "ACCOUNT_DELETED", "user", me.id(), "self-service anonymisation");
        return Map.of("status", "Your account has been deleted and your personal details removed.");
    }

    /** A booking blocks deletion while anything (a parcel, or money) is still in motion. */
    private boolean isUnfinished(Booking b) {
        BookingStatus s = b.getStatus();
        if (s == BookingStatus.CANCELLED || s == BookingStatus.REJECTED || s == BookingStatus.RESOLVED) {
            return false;
        }
        if (s == BookingStatus.DELIVERED) {
            Payment p = payments.forBooking(b.getId());
            return p.isOpen();
        }
        return true;
    }
}
