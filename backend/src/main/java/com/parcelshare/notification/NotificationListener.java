package com.parcelshare.notification;

import com.parcelshare.booking.Booking;
import com.parcelshare.booking.BookingRepository;
import com.parcelshare.common.AppProps;
import com.parcelshare.custody.TrackingRecorded;
import com.parcelshare.identity.AppUser;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns booking milestones into emails. Runs after the originating transaction commits (so we never email about
 * something that rolled back) and off the request thread (so a slow mail server cannot slow the app).
 */
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    record Message(AppUser to, String subject, String line) {
    }

    private final BookingRepository bookings;
    private final NotificationService notifications;
    private final AppProps props;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTracking(TrackingRecorded e) {
        try {
            Booking b = bookings.findById(e.bookingId()).orElse(null);
            if (b == null) {
                return;
            }
            for (Message m : messagesFor(e.type(), b)) {
                notifications.notify(m.to(), e.type(), m.subject(), m.line(), "/bookings/" + b.getId());
            }
        } catch (Exception ex) {
            log.warn("Notification failed for event {}: {}", e.type(), ex.getMessage());
        }
    }

    List<Message> messagesFor(String type, Booking b) {
        AppUser sender = b.getParcel().getSender();
        AppUser traveller = b.getTrip().getTraveller();
        String code = b.getParcel().getPublicCode();
        String route = b.getTrip().getOrigin().getName() + " to " + b.getTrip().getDestination().getName();
        String tag = code + " (" + route + ")";
        List<Message> out = new ArrayList<>();
        switch (type) {
            case "BOOKING_REQUESTED" -> out.add(new Message(traveller, "New parcel request " + code,
                    sender.getFullName() + " wants you to carry a " + b.getParcel().getWeightGrams() / 1000.0 + " kg parcel ("
                            + b.getParcel().getCategory().getName() + ") on " + route + ". You would earn Rs "
                            + (b.getPricePaise() - b.getCommissionPaise()) / 100 + ". Please accept or decline soon."));
            case "BOOKING_ACCEPTED" -> out.add(new Message(sender, "Accepted: " + code,
                    traveller.getFullName() + " accepted your booking for " + tag + ". Arrange the handover, then generate the handover code."));
            case "BOOKING_REJECTED" -> out.add(new Message(sender, "Declined: " + code,
                    traveller.getFullName() + " declined " + tag + ". Your payment is refunded and the parcel is open for other travellers."));
            case "REQUEST_EXPIRED" -> out.add(new Message(sender, "No response: " + code,
                    "The traveller did not respond in time for " + tag + ". You were refunded and can pick another traveller."));
            case "CANCELLED_BY_SENDER" -> out.add(new Message(traveller, "Booking cancelled: " + code,
                    sender.getFullName() + " cancelled " + tag + "."));
            case "CANCELLED_BY_TRAVELLER" -> out.add(new Message(sender, "Traveller cancelled: " + code,
                    traveller.getFullName() + " cancelled " + tag + ". You were refunded; please choose another traveller."));
            case "HANDOVER_CONFIRMED" -> out.add(new Message(sender, "Parcel handed over: " + code,
                    traveller.getFullName() + " confirmed they received " + tag + "."));
            case "JOURNEY_STARTED" -> out.add(new Message(sender, "On its way: " + code,
                    "Your parcel " + tag + " is now in transit."));
            case "ARRIVED" -> out.add(new Message(sender, "Arrived: " + code,
                    "The traveller reported arriving at the destination for " + tag
                            + ". The recipient will read out the delivery code on handover."));
            case "DELIVERY_FAILED" -> out.add(new Message(sender, "Delivery attempt failed: " + code,
                    "The traveller could not complete delivery of " + tag + ". Please coordinate with the recipient."));
            case "DELIVERY_CONFIRMED" -> out.add(new Message(sender, "Delivered: " + code,
                    "Your parcel " + tag + " was delivered. Confirm receipt to pay the traveller now, or raise a dispute within "
                            + props.releaseWindowHours() + " hours."));
            case "PAYMENT_RELEASED" -> out.add(new Message(traveller, "You have been paid: " + code,
                    "Payment for " + tag + " has been released: Rs " + (b.getPricePaise() - b.getCommissionPaise()) / 100 + "."));
            case "DISPUTE_RAISED" -> {
                out.add(new Message(sender, "Dispute opened: " + code, "A dispute was opened on " + tag + ". The payment is frozen and an admin will review."));
                out.add(new Message(traveller, "Dispute opened: " + code, "A dispute was opened on " + tag + ". The payment is frozen and an admin will review."));
            }
            case "DISPUTE_RESOLVED" -> {
                out.add(new Message(sender, "Dispute resolved: " + code, "An admin resolved the dispute on " + tag + ". See the booking for the outcome."));
                out.add(new Message(traveller, "Dispute resolved: " + code, "An admin resolved the dispute on " + tag + ". See the booking for the outcome."));
            }
            default -> { }
        }
        return out;
    }
}
