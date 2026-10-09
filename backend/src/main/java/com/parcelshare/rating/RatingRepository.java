package com.parcelshare.rating;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    boolean existsByBookingIdAndRaterId(Long bookingId, Long raterId);

    java.util.List<Rating> findTop30ByRateeIdOrderByIdDesc(Long rateeId);
}
