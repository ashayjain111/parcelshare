package com.parcelshare.booking;

import com.parcelshare.custody.TrackingService;
import com.parcelshare.dispute.DisputeService;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.rating.RatingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    public record CreateBookingRequest(@NotNull Long tripId, @NotNull Long parcelId) {
    }

    public record ConfirmHandoverRequest(@NotBlank String code, boolean contentsChecked, @Size(max = 300) String note) {
    }

    public record DeliverRequest(@NotBlank String code, @Size(max = 300) String note) {
    }

    public record NoteRequest(@Size(max = 300) String note) {
    }

    public record DisputeRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record RatingRequest(@Min(1) @Max(5) int score, @Size(max = 500) String comment) {
    }

    private final BookingService service;
    private final DisputeService disputes;
    private final RatingService ratings;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateBookingRequest req) {
        return service.create(me, req.tripId(), req.parcelId());
    }

    @GetMapping("/mine")
    public List<BookingView> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/{id}")
    public BookingView get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.get(me, id);
    }

    @GetMapping("/{id}/events")
    public List<TrackingService.EventView> events(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.events(me, id);
    }

    /** Recomputes the hash chain: proves the timeline has not been edited after the fact. */
    @GetMapping("/{id}/events/verify")
    public TrackingService.Verification verify(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.verifyChain(me, id);
    }

    @PostMapping("/{id}/accept")
    public BookingView accept(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.accept(me, id);
    }

    /** Reject (traveller, before acceptance) and cancel (either party, before handover) share one endpoint. */
    @PostMapping({"/{id}/cancel", "/{id}/reject"})
    public BookingView cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.cancel(me, id);
    }

    @PostMapping("/{id}/handover/code")
    public BookingService.HandoverCode handoverCode(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.issueHandoverCode(me, id);
    }

    @PostMapping("/{id}/handover/confirm")
    public BookingView confirmHandover(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @Valid @RequestBody ConfirmHandoverRequest req) {
        return service.confirmHandover(me, id, req.code(), req.contentsChecked(), req.note());
    }

    @PostMapping("/{id}/start")
    public BookingView start(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.startJourney(me, id);
    }

    @PostMapping("/{id}/arrive")
    public BookingView arrive(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.markArrived(me, id);
    }

    @PostMapping("/{id}/deliver")
    public BookingView deliver(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                               @Valid @RequestBody DeliverRequest req) {
        return service.completeDelivery(me, id, req.code(), req.note());
    }

    @PostMapping("/{id}/delivery-failed")
    public BookingView deliveryFailed(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @Valid @RequestBody NoteRequest req) {
        return service.reportDeliveryFailed(me, id, req.note());
    }

    @PostMapping("/{id}/retry-delivery")
    public BookingView retry(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.retryDelivery(me, id);
    }

    @PostMapping("/{id}/confirm-receipt")
    public BookingView confirmReceipt(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.confirmReceipt(me, id);
    }

    @PostMapping("/{id}/dispute")
    public DisputeService.DisputeView dispute(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                              @Valid @RequestBody DisputeRequest req) {
        return disputes.openByUser(me, id, req.reason());
    }

    @PostMapping("/{id}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    public void rate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody RatingRequest req) {
        ratings.rate(me, id, req.score(), req.comment());
    }
}
