package com.parcelshare.trip;

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
@Table(name = "trip")
@org.hibernate.annotations.DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Trip {

    public enum Status { OPEN, CANCELLED, COMPLETED, EXPIRED }

    /** Flights are deliberately excluded: airlines and airport security prohibit carrying items for strangers. */
    public enum Mode { BUS, TRAIN, CAR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "traveller_id")
    private AppUser traveller;

    @ManyToOne(optional = false)
    @JoinColumn(name = "origin_city_id")
    private City origin;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dest_city_id")
    private City destination;

    @Column(nullable = false)
    private Instant departsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", nullable = false)
    private Mode travelMode;

    private int capacityGrams;

    /** Changed only by the atomic UPDATEs in TripRepository (overbooking guard); do not set it from Java. */
    private int reservedGrams;

    private int pricePerKgPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public int remainingGrams() {
        return capacityGrams - reservedGrams;
    }
}
