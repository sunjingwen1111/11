package com.example.demo.system.auth;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PasswordCodec {
    private PasswordCodec() {
    }

    public static String hash(String password) {
        return md5(md5(md5(password)));
    }

    public static boolean matches(String candidate, String stored) {
        return equalsHash(hash(candidate), stored);
    }

    public static boolean matchesLegacy(String candidate, String stored) {
        return equalsHash(md5(candidate), stored);
    }

    private static boolean equalsHash(String computed, String stored) {
        return stored != null && MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.US_ASCII), stored.getBytes(StandardCharsets.US_ASCII));
    }

    private static String md5(String password) {
        return DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
    }
}
