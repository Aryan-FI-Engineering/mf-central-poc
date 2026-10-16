package com.fundsindia.mfcpoc.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts the {@code data} parameter for MFCentral's portal handoff ({@code /api/auth/start}).
 *
 * <p>Distinct from {@link PayloadCipher}: here the QR-URL key is used as its <em>raw</em> UTF-8 bytes
 * (it is exactly 32 bytes, i.e. an AES-256 key), the output is lowercase hex rather than Base64, and the
 * value is {@code hex(IV):hex(ciphertext)} with the IV prepended in the clear. Verified against a live
 * {@code data=} blob produced by FundsIndia's own ecas/otp backend for the same client.
 */
public final class PortalDataCipher {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final HexFormat HEX = HexFormat.of();

    private final SecretKeySpec key;
    private final byte[] iv;

    public PortalDataCipher(String rawKey, String ivText) {
        byte[] keyBytes = rawKey == null ? new byte[0] : rawKey.getBytes(UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("QR URL key must be 16/24/32 bytes for raw AES, got " + keyBytes.length);
        }
        this.iv = ivText == null ? new byte[0] : ivText.getBytes(UTF_8);
        if (iv.length != 16) {
            throw new IllegalArgumentException("IV must be exactly 16 bytes, got " + iv.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Returns {@code hex(IV):hex(ciphertext)}, ready to be URL-encoded into the {@code data} parameter. */
    public String encrypt(String plainText) {
        return HEX.formatHex(iv) + ":" + HEX.formatHex(apply(Cipher.ENCRYPT_MODE, plainText.getBytes(UTF_8)));
    }

    /** Inverse of {@link #encrypt}; the IV in the input is ignored in favour of the configured one. */
    public String decrypt(String ivHexColonCipherHex) {
        String cipherHex = ivHexColonCipherHex.substring(ivHexColonCipherHex.indexOf(':') + 1);
        return new String(apply(Cipher.DECRYPT_MODE, HEX.parseHex(cipherHex)), UTF_8);
    }

    private byte[] apply(int mode, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, key, new IvParameterSpec(iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Portal data " + (mode == Cipher.ENCRYPT_MODE ? "encryption" : "decryption") + " failed", e);
        }
    }
}
