package com.parcelshare.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Row lock: serialises all mutations of one booking (state changes, code attempts, event chain). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(Long id);

    @Query("select b from Booking b where b.parcel.sender.id = :uid or b.trip.traveller.id = :uid order by b.createdAt desc")
    List<Booking> forUser(Long uid);

    Optional<Booking> findByRecipientToken(String token);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant before);

    List<Booking> findByTripId(Long tripId);

    @Query("select b from Booking b order by b.createdAt desc")
    List<Booking> latest(Pageable pageable);

    long countByStatus(BookingStatus status);
}
