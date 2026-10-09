package com.parcelshare.trip;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelOfferRepository extends JpaRepository<ParcelOffer, Long> {

    boolean existsByTripIdAndParcelId(Long tripId, Long parcelId);

    List<ParcelOffer> findByParcelId(Long parcelId);

    List<ParcelOffer> findByTripId(Long tripId);
}
