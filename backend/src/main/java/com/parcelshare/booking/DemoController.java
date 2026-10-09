package com.parcelshare.booking;

import com.parcelshare.common.ApiException;
import com.parcelshare.custody.CodeService;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.payment.Payment;
import com.parcelshare.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo helper: plays whichever other party needs to act next, so one person can show the whole lifecycle alone.
 * It calls the exact same service methods (and therefore the same rules, codes and events) as the real endpoints,
 * only the actor changes. The bean does not exist unless parcelshare.demo-mode=true. Never enable in production.
 */
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "parcelshare.demo-mode", havingValue = "true")
public class DemoController {

    private final BookingService service;
    private final BookingRepository bookings;
    private final PaymentService payments;
    private final CodeService codes;

    @PostMapping("/bookings/{id}/advance")
    public BookingView advance(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!b.senderId().equals(me.id()) && !b.travellerId().equals(me.id())) {
            throw ApiException.forbidden("Not your booking");
        }
        AuthUser sender = actor(b.getParcel().getSender());
        AuthUser traveller = actor(b.getTrip().getTraveller());

        switch (b.getStatus()) {
            case REQUESTED -> service.accept(traveller, id);
            case ACCEPTED -> {
                var code = service.issueHandoverCode(sender, id);
                service.confirmHandover(traveller, id, code.code(), true, "demo: contents inspected");
            }
            case HANDED_OVER -> service.startJourney(traveller, id);
            case IN_TRANSIT -> {
                if (service.get(traveller, id).actions().contains("MARK_ARRIVED")) {
                    service.markArrived(traveller, id);
                } else {
                    service.completeDelivery(traveller, id, codes.deliveryCode(b), "demo");
                }
            }
            case DELIVERY_FAILED -> service.retryDelivery(traveller, id);
            case DELIVERED -> {
                Payment p = payments.forBooking(id);
                if (p.getStatus() == Payment.Status.HELD) {
                    service.confirmReceipt(sender, id);
                }
            }
            default -> throw ApiException.conflict("Nothing left to simulate in status " + b.getStatus());
        }
        return service.get(me, id);
    }

    private static AuthUser actor(AppUser u) {
        return new AuthUser(u.getId(), u.getEmail(), u.getRole());
    }
}
