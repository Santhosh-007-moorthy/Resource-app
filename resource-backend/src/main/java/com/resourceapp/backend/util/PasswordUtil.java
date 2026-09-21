package com.resourceapp.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Minimal one-way password hashing so plain-text passwords are never stored or compared
 * directly. This intentionally avoids pulling in a full security framework: it's a small,
 * dependency-free SHA-256 hash, adequate for this app's scope but not a replacement for a
 * proper adaptive hash (BCrypt/Argon2) in a production system with real attacker exposure.
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean matches(String rawPassword, String hashedPassword) {
        return hash(rawPassword).equals(hashedPassword);
    }
}
