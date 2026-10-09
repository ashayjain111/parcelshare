package com.parcelshare.booking;

import com.parcelshare.common.ApiException;
import com.parcelshare.custody.CodeService;
import com.parcelshare.custody.TrackingService;
import com.parcelshare.dispute.DisputeService;
import com.parcelshare.payment.Payment;
import com.parcelshare.payment.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The recipient has no account. The sender shares an unguessable link; this page shows the delivery code (to read
 * out to the traveller) and lets the recipient report a problem after delivery.
 */
@RestController
@RequestMapping("/public/receive")
@RequiredArgsConstructor
public class PublicRecipientController {

    public record TimelineItem(String type, Instant at) {
    }

    public record RecipientView(String parcelCode, String recipientName, String route, String travellerName,
                                String status, String deliveryCode, boolean canReportProblem,
                                List<TimelineItem> timeline) {
    }

    public record ReportRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    private final BookingRepository bookings;
    private final PaymentService payments;
    private final TrackingService tracking;
    private final CodeService codes;
    private final DisputeService disputes;

    @GetMapping("/{token}")
    @Transactional(readOnly = true)
    public RecipientView view(@PathVariable String token) {
        Booking b = bookings.findByRecipientToken(token).orElseThrow(() -> ApiException.notFound("Invalid link"));
        BookingStatus s = b.getStatus();
        boolean codeVisible = s == BookingStatus.ACCEPTED || s == BookingStatus.HANDED_OVER
                || s == BookingStatus.IN_TRANSIT || s == BookingStatus.DELIVERY_FAILED;
        boolean canReport = s == BookingStatus.DELIVERED
                && payments.forBooking(b.getId()).getStatus() == Payment.Status.HELD;
        List<TimelineItem> timeline = tracking.timeline(b.getId()).stream()
                .filter(e -> !e.type().equals("HANDOVER_CODE_ISSUED") && !e.type().equals("RATED"))
                .map(e -> new TimelineItem(e.type(), e.createdAt())).toList();
        return new RecipientView(b.getParcel().getPublicCode(), b.getParcel().getRecipientName(),
                b.getTrip().getOrigin().getName() + " → " + b.getTrip().getDestination().getName(),
                b.getTrip().getTraveller().getFullName(), s.name(), codeVisible ? codes.deliveryCode(b) : null,
                canReport, timeline);
    }

    @PostMapping("/{token}/report")
    public DisputeService.DisputeView report(@PathVariable String token, @Valid @RequestBody ReportRequest req) {
        return disputes.openByRecipient(token, req.reason());
    }
}
