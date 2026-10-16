package com.fundsindia.mfcpoc.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * MFCentral payload encryption: AES-256/CBC/PKCS5Padding with a constant IV. The AES key is the first 32
 * characters of the lowercase hex SHA-256 of the shared key, as UTF-8 bytes ("first 32 byte of the hash" in
 * MFC's doc means the hex string, not the raw digest; verified by decrypting live PROD replies).
 * PROD MFC carries ciphertext as URL-safe Base64 with padding (observed in a live 422 reply), so we encode the
 * same way and decode either alphabet.
 */
public final class PayloadCipher {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private final SecretKeySpec key;
    private final IvParameterSpec iv;

    public PayloadCipher(String sharedKey, String ivText) {
        if (sharedKey == null || sharedKey.isBlank()) {
            throw new IllegalArgumentException("Shared encryption key is required");
        }
        byte[] ivBytes = ivText == null ? new byte[0] : ivText.getBytes(UTF_8);
        if (ivBytes.length != 16) {
            throw new IllegalArgumentException("IV must be exactly 16 bytes, got " + ivBytes.length);
        }
        String hexDigest = HexFormat.of().formatHex(sha256(sharedKey.getBytes(UTF_8)));
        this.key = new SecretKeySpec(hexDigest.substring(0, 32).getBytes(UTF_8), "AES");
        this.iv = new IvParameterSpec(ivBytes);
    }

    public String encrypt(String plainText) {
        return Base64.getUrlEncoder().encodeToString(apply(Cipher.ENCRYPT_MODE, plainText.getBytes(UTF_8)));
    }

    public String decrypt(String base64CipherText) {
        String urlSafe = base64CipherText.strip().replace('+', '-').replace('/', '_');
        return new String(apply(Cipher.DECRYPT_MODE, Base64.getUrlDecoder().decode(urlSafe)), UTF_8);
    }

    // Cipher instances are not thread-safe, so one is created per call.
    private byte[] apply(int mode, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, key, iv);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES " + (mode == Cipher.ENCRYPT_MODE ? "encryption" : "decryption") + " failed", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
