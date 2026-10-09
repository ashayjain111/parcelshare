package com.parcelshare.custody;

/** Published after a tracking event is written; listeners (e.g. notifications) react once the transaction commits. */
public record TrackingRecorded(Long bookingId, String type, Long actorId) {
}
