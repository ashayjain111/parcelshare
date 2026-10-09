package com.parcelshare.parcel;

import com.parcelshare.geo.City;
import com.parcelshare.identity.AppUser;
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
@Table(name = "parcel")
@Getter
@Setter
@NoArgsConstructor
public class Parcel {

    /** Listing lifecycle. The delivery lifecycle lives on Booking, not here. */
    public enum Status { PENDING_REVIEW, OPEN, BOOKED, CLOSED, CANCELLED, EXPIRED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicCode;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id")
    private AppUser sender;

    @ManyToOne(optional = false)
    @JoinColumn(name = "origin_city_id")
    private City origin;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dest_city_id")
    private City destination;

    private Instant windowStart;
    private Instant windowEnd;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private ParcelCategory category;

    private int weightGrams;
    private String description;
    private long declaredValuePaise;
    private String recipientName;
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    private String flagReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
