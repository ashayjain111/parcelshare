package com.parcelshare.evidence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    @Query("""
            select new com.parcelshare.evidence.EvidenceRow(e.id, e.kind, e.uploaderId, e.contentType, e.sizeBytes,
                                                            e.sha256, e.createdAt)
            from Evidence e where e.bookingId = :bookingId order by e.id
            """)
    List<EvidenceRow> rowsFor(Long bookingId);

    long countByBookingId(Long bookingId);

    List<Evidence> findByBookingIdOrderById(Long bookingId);
}
