package com.parcelshare.evidence;

import com.parcelshare.common.ApiException;
import com.parcelshare.identity.AuthUser;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/bookings/{bookingId}/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceService.EvidenceView upload(@AuthenticationPrincipal AuthUser me, @PathVariable Long bookingId,
                                               @RequestParam Evidence.Kind kind, @RequestParam MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw ApiException.bad("No file received");
        }
        return service.upload(me, bookingId, kind, file.getBytes());
    }

    @GetMapping
    public List<EvidenceService.EvidenceView> list(@AuthenticationPrincipal AuthUser me, @PathVariable Long bookingId) {
        return service.list(me, bookingId);
    }

    /** Proves every stored photo still matches the hash recorded in the event chain when it was uploaded. */
    @GetMapping("/verify")
    public EvidenceService.PhotoVerification verify(@AuthenticationPrincipal AuthUser me, @PathVariable Long bookingId) {
        return service.verify(me, bookingId);
    }

    @GetMapping("/{evidenceId}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal AuthUser me, @PathVariable Long bookingId,
                                        @PathVariable Long evidenceId) {
        EvidenceService.Image img = service.image(me, bookingId, evidenceId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(img.bytes());
    }
}
