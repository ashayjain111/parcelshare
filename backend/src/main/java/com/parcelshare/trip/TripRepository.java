package com.parcelshare.trip;

import com.parcelshare.identity.AppUser;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByTravellerIdOrderByDepartsAtDesc(Long travellerId);

    @Query("""
            select t from Trip t
            where t.status = :open and t.departsAt between :start and :end
            order by t.departsAt asc
            """)
    List<Trip> searchByDate(Instant start, Instant end, Trip.Status open);

    /** Hard constraints of the matching engine, expressed as SQL. Soft ranking happens in MatchScorer. */
    @Query("""
            select t from Trip t
            where t.status = :open
              and t.origin.region.id = :originRegion and t.destination.region.id = :destRegion
              and t.departsAt between :from and :to and t.departsAt > :now
              and (t.capacityGrams - t.reservedGrams) >= :weight
              and t.traveller.id <> :senderId
              and t.traveller.travellerVerification = :approved
              and t.traveller.status = :active
            """)
    List<Trip> candidatesForParcel(Long originRegion, Long destRegion, Instant from, Instant to, Instant now,
                                   int weight, Long senderId, Trip.Status open,
                                   AppUser.Verification approved, AppUser.Status active);

    /**
     * Atomic capacity reservation: the WHERE clause is the overbooking guard. Returns 0 rows if the trip
     * no longer has room, so two concurrent bookings can never exceed capacity.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Trip t set t.reservedGrams = t.reservedGrams + :grams
            where t.id = :id and t.status = :open and t.capacityGrams - t.reservedGrams >= :grams
            """)
    int reserve(Long id, int grams, Trip.Status open);

    @Modifying(flushAutomatically = true)
    @Query("update Trip t set t.reservedGrams = t.reservedGrams - :grams where t.id = :id and t.reservedGrams >= :grams")
    int release(Long id, int grams);

    @Modifying(flushAutomatically = true)
    @Query("update Trip t set t.status = :to where t.status = :from and t.departsAt < :before")
    int expireOlderThan(Instant before, Trip.Status from, Trip.Status to);
}
