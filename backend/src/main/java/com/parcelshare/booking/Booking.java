package com.parcelshare.booking;

import com.parcelshare.parcel.Parcel;
import com.parcelshare.trip.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(optional = false)
    @JoinColumn(name = "parcel_id")
    private Parcel parcel;

    private long pricePaise;
    private long commissionPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.REQUESTED;

    private String handoverHash;
    private Instant handoverExpiresAt;
    private int handoverAttempts;
    private int deliveryAttempts;

    /** Unguessable token in the link the sender gives the recipient (who has no account). */
    @Column(nullable = false, unique = true)
    private String recipientToken;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long senderId() {
        return parcel.getSender().getId();
    }

    public Long travellerId() {
        return trip.getTraveller().getId();
    }
}
