package com.jobapplier.job;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class JobNormalizer {
    private JobNormalizer() {}

    public static String dedupeHash(String company, String title, String location) {
        String key = (norm(company) + "|" + norm(title) + "|" + norm(location));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
