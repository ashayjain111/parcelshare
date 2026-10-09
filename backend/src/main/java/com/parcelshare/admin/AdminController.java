package com.parcelshare.admin;

import com.parcelshare.booking.BookingRepository;
import com.parcelshare.booking.BookingService;
import com.parcelshare.booking.BookingStatus;
import com.parcelshare.booking.BookingView;
import com.parcelshare.common.ApiException;
import com.parcelshare.common.AuditLog;
import com.parcelshare.common.AuditService;
import com.parcelshare.dispute.Dispute;
import com.parcelshare.dispute.DisputeService;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.identity.UserRepository;
import com.parcelshare.parcel.Parcel;
import com.parcelshare.parcel.ParcelRepository;
import com.parcelshare.parcel.ParcelView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Everything under /admin/** requires ROLE_ADMIN (see SecurityConfig). Every mutation is audit-logged. */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    public record AdminUserView(Long id, String email, String phone, String fullName, String role, String status,
                                boolean emailVerified, String travellerVerification, String verificationNote,
                                double ratingAvg, int ratingCount, int completedDeliveries, int cancelledCount,
                                int disputedCount) {
        static AdminUserView of(AppUser u) {
            return new AdminUserView(u.getId(), u.getEmail(), u.getPhone(), u.getFullName(), u.getRole().name(),
                    u.getStatus().name(), u.isEmailVerified(), u.getTravellerVerification().name(),
                    u.getVerificationNote(), Math.round(u.ratingAvg() * 10) / 10.0, u.getRatingCount(),
                    u.getCompletedDeliveries(), u.getCancelledCount(), u.getDisputedCount());
        }
    }

    public record DecisionRequest(boolean approve) {
    }

    public record ResolveRequest(@NotNull Dispute.Resolution resolution, Long travellerSharePaise,
                                 @Size(max = 1000) String note) {
    }

    private final UserRepository users;
    private final ParcelRepository parcels;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final DisputeService disputes;
    private final AuditService audit;

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        return Map.of(
                "users", users.count(),
                "pendingVerifications", users.findByTravellerVerification(AppUser.Verification.PENDING).size(),
                "parcelsAwaitingReview", parcels.findByStatusOrderByCreatedAtAsc(Parcel.Status.PENDING_REVIEW).size(),
                "bookings", bookings.count(),
                "inTransit", bookings.countByStatus(BookingStatus.IN_TRANSIT),
                "openDisputes", disputes.list(true).size());
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<AdminUserView> users() {
        return users.findAll().stream().map(AdminUserView::of).toList();
    }

    @PostMapping("/users/{id}/suspend")
    @Transactional
    public AdminUserView suspend(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getRole() == AppUser.Role.ADMIN) {
            throw ApiException.bad("Admins cannot be suspended");
        }
        u.setStatus(AppUser.Status.SUSPENDED);
        audit.log(me.id(), "USER_SUSPENDED", "user", id, u.getEmail());
        return AdminUserView.of(u);
    }

    @PostMapping("/users/{id}/reactivate")
    @Transactional
    public AdminUserView reactivate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setStatus(AppUser.Status.ACTIVE);
        audit.log(me.id(), "USER_REACTIVATED", "user", id, u.getEmail());
        return AdminUserView.of(u);
    }

    @GetMapping("/verifications")
    @Transactional(readOnly = true)
    public List<AdminUserView> pendingVerifications() {
        return users.findByTravellerVerification(AppUser.Verification.PENDING).stream().map(AdminUserView::of).toList();
    }

    @PostMapping("/verifications/{userId}/decision")
    @Transactional
    public AdminUserView decide(@AuthenticationPrincipal AuthUser me, @PathVariable Long userId,
                                @RequestBody DecisionRequest req) {
        AppUser u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setTravellerVerification(req.approve() ? AppUser.Verification.APPROVED : AppUser.Verification.REJECTED);
        audit.log(me.id(), req.approve() ? "VERIFICATION_APPROVED" : "VERIFICATION_REJECTED", "user", userId, u.getEmail());
        return AdminUserView.of(u);
    }

    @GetMapping("/parcels/pending")
    @Transactional(readOnly = true)
    public List<ParcelView> pendingParcels() {
        return parcels.findByStatusOrderByCreatedAtAsc(Parcel.Status.PENDING_REVIEW).stream()
                .map(p -> ParcelView.of(p, false)).toList();
    }

    @PostMapping("/parcels/{id}/review")
    @Transactional
    public ParcelView reviewParcel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                   @RequestBody DecisionRequest req) {
        Parcel p = parcels.findById(id).orElseThrow(() -> ApiException.notFound("Parcel not found"));
        if (p.getStatus() != Parcel.Status.PENDING_REVIEW) {
            throw ApiException.conflict("Parcel is not awaiting review");
        }
        p.setStatus(req.approve() ? Parcel.Status.OPEN : Parcel.Status.REJECTED);
        audit.log(me.id(), req.approve() ? "PARCEL_APPROVED" : "PARCEL_REJECTED", "parcel", id, p.getPublicCode());
        return ParcelView.of(p, false);
    }

    @GetMapping("/bookings")
    @Transactional(readOnly = true)
    public List<BookingView> bookings(@AuthenticationPrincipal AuthUser me) {
        return bookings.latest(PageRequest.of(0, 100)).stream().map(b -> bookingService.view(b, me)).toList();
    }

    @GetMapping("/disputes")
    public List<DisputeService.DisputeView> disputes(@RequestParam(defaultValue = "true") boolean openOnly) {
        return disputes.list(openOnly);
    }

    @PostMapping("/disputes/{id}/resolve")
    public DisputeService.DisputeView resolve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                              @Valid @RequestBody ResolveRequest req) {
        return disputes.resolve(me.id(), id, req.resolution(), req.travellerSharePaise(), req.note());
    }

    @GetMapping("/audit")
    public List<AuditLog> audit() {
        return audit.latest(100);
    }
}
