package com.parcelshare.parcel;

import com.parcelshare.geo.CityDto;
import com.parcelshare.identity.UserSummary;
import java.time.Instant;

public record ParcelView(Long id, String publicCode, UserSummary sender, CityDto origin, CityDto destination,
                         Instant windowStart, Instant windowEnd, String categoryName, String categoryTier,
                         int weightGrams, String description, long declaredValuePaise, String recipientName,
                         String status, String flagReason, Instant createdAt) {

    /** Recipient details are private: only shown to the sender, and to the traveller once booked. */
    public static ParcelView of(Parcel p, boolean includeRecipient) {
        return new ParcelView(p.getId(), p.getPublicCode(), UserSummary.of(p.getSender()), CityDto.of(p.getOrigin()),
                CityDto.of(p.getDestination()), p.getWindowStart(), p.getWindowEnd(), p.getCategory().getName(),
                p.getCategory().getTier().name(), p.getWeightGrams(), p.getDescription(), p.getDeclaredValuePaise(),
                includeRecipient ? p.getRecipientName() : null, p.getStatus().name(), p.getFlagReason(),
                p.getCreatedAt());
    }
}
