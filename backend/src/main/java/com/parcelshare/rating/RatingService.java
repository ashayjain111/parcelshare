package com.parcelshare.rating;

import com.parcelshare.booking.Booking;
import com.parcelshare.booking.BookingRepository;
import com.parcelshare.booking.BookingStatus;
import com.parcelshare.common.ApiException;
import com.parcelshare.custody.TrackingService;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final BookingRepository bookings;
    private final RatingRepository ratings;
    private final TrackingService tracking;

    @Transactional
    public void rate(AuthUser me, Long bookingId, int score, String comment) {
        if (score < 1 || score > 5) {
            throw ApiException.bad("Score must be between 1 and 5");
        }
        Booking b = bookings.lockById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (b.getStatus() != BookingStatus.DELIVERED && b.getStatus() != BookingStatus.RESOLVED) {
            throw ApiException.conflict("You can rate once the booking is delivered or resolved");
        }
        AppUser sender = b.getParcel().getSender();
        AppUser traveller = b.getTrip().getTraveller();
        AppUser ratee;
        if (sender.getId().equals(me.id())) {
            ratee = traveller;
        } else if (traveller.getId().equals(me.id())) {
            ratee = sender;
        } else {
            throw ApiException.forbidden("Not your booking");
        }
        if (ratings.existsByBookingIdAndRaterId(bookingId, me.id())) {
            throw ApiException.conflict("You already rated this booking");
        }
        ratings.save(new Rating(bookingId, me.id(), ratee.getId(), score, comment));
        ratee.setRatingSum(ratee.getRatingSum() + score);
        ratee.setRatingCount(ratee.getRatingCount() + 1);
        tracking.record(bookingId, "RATED", me.id(), "score=" + score);
    }
}
