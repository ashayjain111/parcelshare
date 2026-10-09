package com.parcelshare.custody;

import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only, hash-chained event log. Each event's hash covers the previous event's hash, so editing or deleting
 * any past row breaks every hash after it and {@link #verify} will say so. Callers must hold the booking row lock
 * (BookingRepository.lockById) so two writers cannot fork the chain.
 */
@Service
@RequiredArgsConstructor
public class TrackingService {

    public static final String GENESIS = "0".repeat(64);

    public record EventView(Long id, String type, Long actorId, String actorName, String payload, Instant createdAt,
                            String hash) {
    }

    public record Verification(boolean valid, Long firstBrokenEventId, int events) {
    }

    private final TrackingEventRepository events;
    private final UserRepository users;
    private final ApplicationEventPublisher publisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public TrackingEvent record(Long bookingId, String type, Long actorId, String payload) {
        String prev = events.findTopByBookingIdOrderByIdDesc(bookingId).map(TrackingEvent::getHash).orElse(GENESIS);
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String safePayload = payload == null ? "" : (payload.length() > 1000 ? payload.substring(0, 1000) : payload);
        String hash = hash(prev, bookingId, type, actorId, at, safePayload);
        TrackingEvent saved = events.save(new TrackingEvent(bookingId, type, actorId, safePayload, prev, hash, at));
        publisher.publishEvent(new TrackingRecorded(bookingId, type, actorId));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EventView> timeline(Long bookingId) {
        List<TrackingEvent> list = events.findByBookingIdOrderByIdAsc(bookingId);
        Map<Long, String> names = users.findAllById(list.stream().map(TrackingEvent::getActorId)
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        return list.stream().map(e -> new EventView(e.getId(), e.getType(), e.getActorId(),
                e.getActorId() == null ? "System" : names.getOrDefault(e.getActorId(), "Unknown"), e.getPayload(),
                e.getCreatedAt(), e.getHash())).toList();
    }

    @Transactional(readOnly = true)
    public Verification verify(Long bookingId) {
        List<TrackingEvent> list = events.findByBookingIdOrderByIdAsc(bookingId);
        String prev = GENESIS;
        for (TrackingEvent e : list) {
            String expected = hash(prev, e.getBookingId(), e.getType(), e.getActorId(), e.getCreatedAt(),
                    e.getPayload() == null ? "" : e.getPayload());
            if (!e.getPrevHash().equals(prev) || !e.getHash().equals(expected)) {
                return new Verification(false, e.getId(), list.size());
            }
            prev = e.getHash();
        }
        return new Verification(true, null, list.size());
    }

    @Transactional(readOnly = true)
    public boolean has(Long bookingId, String type) {
        return events.existsByBookingIdAndType(bookingId, type);
    }

    static String hash(String prev, Long bookingId, String type, Long actorId, Instant at, String payload) {
        String material = String.join("|", prev, String.valueOf(bookingId), type, String.valueOf(actorId),
                String.valueOf(at.toEpochMilli()), payload);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
