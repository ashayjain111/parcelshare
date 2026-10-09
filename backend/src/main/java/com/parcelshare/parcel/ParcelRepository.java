package com.parcelshare.parcel;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    List<Parcel> findBySenderIdOrderByCreatedAtDesc(Long senderId);

    List<Parcel> findByStatusOrderByCreatedAtAsc(Parcel.Status status);

    boolean existsByPublicCode(String publicCode);

    @Query("""
            select p from Parcel p
            where p.status = :open
              and p.origin.region.id = :originRegion and p.destination.region.id = :destRegion
              and p.windowStart <= :departsAt and p.windowEnd >= :departsAt
              and p.weightGrams <= :remaining
              and p.sender.id <> :travellerId
            """)
    List<Parcel> candidatesForTrip(Long originRegion, Long destRegion, Instant departsAt, int remaining,
                                   Long travellerId, Parcel.Status open);

    /** Compare-and-set on status: only one concurrent booking can flip a parcel from OPEN to BOOKED. */
    @Modifying(flushAutomatically = true)
    @Query("update Parcel p set p.status = :to where p.id = :id and p.status = :from")
    int transition(Long id, Parcel.Status from, Parcel.Status to);

    @Modifying(flushAutomatically = true)
    @Query("update Parcel p set p.status = :to where p.status = :from and p.windowEnd < :before")
    int expireOlderThan(Instant before, Parcel.Status from, Parcel.Status to);
}
