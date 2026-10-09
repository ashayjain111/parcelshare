package com.parcelshare.evidence;

import java.time.Instant;

/** Metadata-only projection so listing photos never loads the image bytes. */
public record EvidenceRow(Long id, Evidence.Kind kind, Long uploaderId, String contentType, int sizeBytes,
                          String sha256, Instant createdAt) {
}
