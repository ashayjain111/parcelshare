package com.parcelshare.custody;

import com.parcelshare.common.AppProps;
import com.parcelshare.booking.Booking;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Handover and delivery verification codes.
 * - Handover code: random 6 digits, single-use, short expiry, only its hash is stored.
 * - Delivery code: derived (HMAC) from the booking, so nothing secret is stored and the sender can always re-read it.
 * Both are further protected by attempt limits enforced in BookingService.
 */
@Service
public class CodeService {

    private final SecureRandom random = new SecureRandom();
    private final byte[] secret;

    public CodeService(AppProps props) {
        this.secret = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String newHandoverCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public String hashHandover(Long bookingId, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(secret);
            return HexFormat.of().formatHex(md.digest((":h:" + bookingId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String deliveryCode(Booking b) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal(("delivery:" + b.getId() + ":" + b.getRecipientToken())
                    .getBytes(StandardCharsets.UTF_8));
            long n = ((out[0] & 0xFFL) << 24 | (out[1] & 0xFFL) << 16 | (out[2] & 0xFFL) << 8 | (out[3] & 0xFFL))
                    % 1_000_000L;
            return String.format("%06d", n);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean constantTimeEquals(String a, String b) {
        return a != null && b != null
                && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
