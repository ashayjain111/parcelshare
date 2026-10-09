package com.parcelshare.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    List<Payment> findByStatusAndReleaseAtBefore(Payment.Status status, Instant before);
}
