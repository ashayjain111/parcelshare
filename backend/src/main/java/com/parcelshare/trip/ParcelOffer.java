package com.parcelshare.trip;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** "I can carry this parcel on my trip." Only a nudge: the sender still chooses, books and pays. */
@Entity
@Table(name = "parcel_offer")
@Getter
@NoArgsConstructor
public class ParcelOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tripId;
    private Long parcelId;
    private Instant createdAt = Instant.now();

    public ParcelOffer(Long tripId, Long parcelId) {
        this.tripId = tripId;
        this.parcelId = parcelId;
    }
}
