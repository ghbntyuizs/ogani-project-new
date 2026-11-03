package com.ogani.store.order.service.checkout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class CheckoutFingerprint {
    private CheckoutFingerprint() {}

    public static String hash(List<String> fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] bytes = field == null ? new byte[0] : field.getBytes(StandardCharsets.UTF_8);
                digest.update(((field == null ? -1 : bytes.length) + ":").getBytes(StandardCharsets.UTF_8));
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    public static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
