package com.parcelshare.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** One-time secrets (password reset links): random 256-bit tokens, and only their SHA-256 is ever stored. */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    public static String random() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
