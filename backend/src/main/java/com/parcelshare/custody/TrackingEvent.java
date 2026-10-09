package com.parcelshare.custody;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One immutable fact in a booking's chain of custody. Rows are only ever inserted, never updated. */
@Entity
@Table(name = "tracking_event")
@Getter
@NoArgsConstructor
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long bookingId;

    @Column(updatable = false)
    private String type;

    @Column(updatable = false)
    private Long actorId;

    @Column(updatable = false)
    private String payload;

    @Column(updatable = false)
    private String prevHash;

    @Column(updatable = false)
    private String hash;

    @Column(updatable = false)
    private Instant createdAt;

    public TrackingEvent(Long bookingId, String type, Long actorId, String payload, String prevHash, String hash,
                         Instant createdAt) {
        this.bookingId = bookingId;
        this.type = type;
        this.actorId = actorId;
        this.payload = payload;
        this.prevHash = prevHash;
        this.hash = hash;
        this.createdAt = createdAt;
    }
}
