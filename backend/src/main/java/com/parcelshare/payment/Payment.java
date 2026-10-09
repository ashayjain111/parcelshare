package com.parcelshare.payment;

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
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    public enum Status { HELD, ON_DISPUTE_HOLD, RELEASED, REFUNDED, PARTIALLY_RELEASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long bookingId;

    private long amountPaise;
    private long commissionPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.HELD;

    private String gateway;
    private String gatewayRef;

    /** When a delivered booking's money becomes releasable if nobody disputes it. */
    private Instant releaseAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isOpen() {
        return status == Status.HELD || status == Status.ON_DISPUTE_HOLD;
    }
}
