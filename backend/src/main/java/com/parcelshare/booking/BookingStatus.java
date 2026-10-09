package com.parcelshare.booking;

import java.util.Map;
import java.util.Set;

/**
 * The delivery lifecycle of one parcel on one trip. Transitions are explicit; anything not listed is illegal.
 * ARRIVED / READY_FOR_HANDOVER are tracking events, not states, to keep the machine small.
 */
public enum BookingStatus {
    REQUESTED, ACCEPTED, HANDED_OVER, IN_TRANSIT, DELIVERED, DELIVERY_FAILED, DISPUTED, RESOLVED, REJECTED, CANCELLED;

    private static final Map<BookingStatus, Set<BookingStatus>> NEXT = Map.of(
            REQUESTED, Set.of(ACCEPTED, REJECTED, CANCELLED),
            ACCEPTED, Set.of(HANDED_OVER, CANCELLED),
            HANDED_OVER, Set.of(IN_TRANSIT, DISPUTED),
            IN_TRANSIT, Set.of(DELIVERED, DELIVERY_FAILED, DISPUTED),
            DELIVERY_FAILED, Set.of(IN_TRANSIT, DISPUTED),
            DELIVERED, Set.of(DISPUTED),
            DISPUTED, Set.of(RESOLVED),
            RESOLVED, Set.of(),
            REJECTED, Set.of(),
            CANCELLED, Set.of());

    public boolean canGoTo(BookingStatus to) {
        return NEXT.get(this).contains(to);
    }

    public boolean isTerminal() {
        return NEXT.get(this).isEmpty();
    }
}
