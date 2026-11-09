package com.fclinic.authservice.infrastructure.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class ServiceClientCredentials {
  private ServiceClientCredentials() { }
  public static boolean matches(String header, String id, String secret) {
    if (header == null || header.length() > 8192 || !header.regionMatches(true, 0, "Basic ", 0, 6)
        || id == null || id.isBlank() || secret == null || secret.isBlank()) return false;
    try {
      String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
      int split = decoded.indexOf(':');
      if (split <= 0) return false;
      return MessageDigest.isEqual(id.getBytes(StandardCharsets.UTF_8),
          URLDecoder.decode(decoded.substring(0, split), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8))
          & MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
          URLDecoder.decode(decoded.substring(split + 1), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) { return false; }
  }
}
