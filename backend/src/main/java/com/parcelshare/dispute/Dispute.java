package com.parcelshare.dispute;

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
import lombok.Setter;

@Entity
@Table(name = "dispute")
@Getter
@Setter
@NoArgsConstructor
public class Dispute {

    public enum Status { OPEN, RESOLVED }

    public enum Resolution { RELEASE, REFUND, PARTIAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bookingId;

    /** Null when the dispute was raised by the (account-less) recipient. */
    private Long raisedBy;

    private String raisedByRole;
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    private Resolution resolution;

    private Long travellerSharePaise;
    private String adminNote;
    private Long resolvedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;
}
