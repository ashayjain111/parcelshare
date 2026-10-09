package com.parcelshare.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A photo attached to a booking. Immutable once saved; its SHA-256 is also written into the tamper-evident event chain. */
@Entity
@Table(name = "evidence")
@Getter
@NoArgsConstructor
public class Evidence {

    /** CONTENTS = what is inside at handover, DELIVERY = proof of drop-off, DAMAGE = problem report. */
    public enum Kind { CONTENTS, DELIVERY, DAMAGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long bookingId;

    @Column(updatable = false)
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Kind kind;

    @Column(updatable = false)
    private String contentType;

    @Column(updatable = false)
    private int sizeBytes;

    @Column(updatable = false)
    private String sha256;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(updatable = false, length = 8_000_000)
    private byte[] data;

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    public Evidence(Long bookingId, Long uploaderId, Kind kind, String contentType, byte[] data, String sha256) {
        this.bookingId = bookingId;
        this.uploaderId = uploaderId;
        this.kind = kind;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
        this.sha256 = sha256;
    }
}
