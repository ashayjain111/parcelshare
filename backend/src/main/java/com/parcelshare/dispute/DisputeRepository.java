package com.parcelshare.dispute;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    List<Dispute> findByStatusOrderByCreatedAtAsc(Dispute.Status status);

    List<Dispute> findAllByOrderByCreatedAtDesc();

    List<Dispute> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
