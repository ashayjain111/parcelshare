package com.parcelshare.evidence;

import com.parcelshare.booking.Booking;
import com.parcelshare.booking.BookingRepository;
import com.parcelshare.booking.BookingStatus;
import com.parcelshare.common.ApiException;
import com.parcelshare.custody.TrackingEvent;
import com.parcelshare.custody.TrackingEventRepository;
import com.parcelshare.custody.TrackingService;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.identity.UserRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Photo evidence. Who may upload which kind, and when, is explicit. Each upload is also recorded as a tracking
 * event containing the photo's SHA-256, so a photo cannot be swapped later without breaking the event hash chain.
 */
@Service
@RequiredArgsConstructor
public class EvidenceService {

    static final int MAX_PER_BOOKING = 12;

    public record EvidenceView(Long id, String kind, Long uploaderId, String uploaderName, String uploaderRole,
                               int sizeBytes, String sha256, Instant createdAt) {
    }

    public record Image(byte[] bytes, String contentType) {
    }

    public record PhotoVerification(boolean valid, List<Long> mismatchedPhotoIds, int photos) {
    }

    private static final Map<Evidence.Kind, Set<BookingStatus>> ALLOWED_STATUS = Map.of(
            Evidence.Kind.CONTENTS, EnumSet.of(BookingStatus.ACCEPTED, BookingStatus.HANDED_OVER),
            Evidence.Kind.DELIVERY, EnumSet.of(BookingStatus.IN_TRANSIT, BookingStatus.DELIVERED, BookingStatus.DELIVERY_FAILED),
            Evidence.Kind.DAMAGE, EnumSet.of(BookingStatus.HANDED_OVER, BookingStatus.IN_TRANSIT, BookingStatus.DELIVERED,
                    BookingStatus.DELIVERY_FAILED, BookingStatus.DISPUTED));

    private final EvidenceRepository evidence;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final TrackingService tracking;
    private final TrackingEventRepository events;

    @Transactional
    public EvidenceView upload(AuthUser me, Long bookingId, Evidence.Kind kind, byte[] original) {
        Booking b = bookings.lockById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        boolean isSender = b.senderId().equals(me.id());
        boolean isTraveller = b.travellerId().equals(me.id());
        if (!isSender && !isTraveller) {
            throw ApiException.forbidden("Not your booking");
        }
        if (kind == Evidence.Kind.DELIVERY && !isTraveller) {
            throw ApiException.forbidden("Only the traveller can add delivery photos");
        }
        if (!ALLOWED_STATUS.get(kind).contains(b.getStatus())) {
            throw ApiException.conflict(kind + " photos cannot be added while the booking is " + b.getStatus());
        }
        if (evidence.countByBookingId(bookingId) >= MAX_PER_BOOKING) {
            throw ApiException.conflict("Photo limit reached for this booking");
        }

        byte[] clean = ImageSanitizer.toCleanJpeg(original);
        String sha = sha256(clean);
        Evidence saved = evidence.save(new Evidence(bookingId, me.id(), kind, "image/jpeg", clean, sha));
        tracking.record(bookingId, "EVIDENCE_ADDED", me.id(), "kind=" + kind + " photo=" + saved.getId() + " sha256=" + sha);
        return new EvidenceView(saved.getId(), kind.name(), me.id(), null, isSender ? "SENDER" : "TRAVELLER",
                clean.length, sha, saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<EvidenceView> list(AuthUser me, Long bookingId) {
        Booking b = visibleBooking(me, bookingId);
        Map<Long, String> names = users.findAllById(List.of(b.senderId(), b.travellerId())).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName, (a, c) -> a));
        return evidence.rowsFor(bookingId).stream().map(r -> new EvidenceView(r.id(), r.kind().name(), r.uploaderId(),
                names.getOrDefault(r.uploaderId(), "Unknown"), r.uploaderId().equals(b.senderId()) ? "SENDER" : "TRAVELLER",
                r.sizeBytes(), r.sha256(), r.createdAt())).toList();
    }

    @Transactional(readOnly = true)
    public Image image(AuthUser me, Long bookingId, Long evidenceId) {
        visibleBooking(me, bookingId);
        Evidence e = evidence.findById(evidenceId).filter(x -> x.getBookingId().equals(bookingId))
                .orElseThrow(() -> ApiException.notFound("Photo not found"));
        return new Image(e.getData(), e.getContentType());
    }

    private Booking visibleBooking(AuthUser me, Long bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!me.isAdmin() && !b.senderId().equals(me.id()) && !b.travellerId().equals(me.id())) {
            throw ApiException.forbidden("Not your booking");
        }
        return b;
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Recomputes each photo's SHA-256 from the stored bytes and compares it with the hash that was written into the
     * tamper-evident event chain at upload time. Swapping the image (even together with its own hash column) is caught.
     */
    @Transactional(readOnly = true)
    public PhotoVerification verify(AuthUser me, Long bookingId) {
        visibleBooking(me, bookingId);
        Map<Long, String> recorded = events.findByBookingIdOrderByIdAsc(bookingId).stream()
                .filter(e -> "EVIDENCE_ADDED".equals(e.getType()))
                .collect(Collectors.toMap(this::photoId, this::photoHash, (a, c) -> a));
        List<Long> bad = new java.util.ArrayList<>();
        List<Evidence> all = evidence.findByBookingIdOrderById(bookingId);
        for (Evidence e : all) {
            String expected = recorded.get(e.getId());
            if (expected == null || !expected.equals(sha256(e.getData())) || !expected.equals(e.getSha256())) {
                bad.add(e.getId());
            }
        }
        return new PhotoVerification(bad.isEmpty(), bad, all.size());
    }

    private Long photoId(TrackingEvent e) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("photo=(\\d+)").matcher(e.getPayload());
        return m.find() ? Long.parseLong(m.group(1)) : -1L;
    }

    private String photoHash(TrackingEvent e) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("sha256=([0-9a-f]{64})").matcher(e.getPayload());
        return m.find() ? m.group(1) : "";
    }
}
