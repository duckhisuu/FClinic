package com.fclinic.appointmentservice.infrastructure.client.config;

import com.fclinic.appointmentservice.domain.AppointmentError;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FeignHeaderGuards {
  public static final String CONTRACT = "X-FClinic-Reference-Contract";
  private FeignHeaderGuards() {}
  public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
  public static String basic(String id, String secret) {
    require(id != null && !id.isBlank() && secret != null && !secret.isBlank());
    return "Basic " + Base64.getEncoder().encodeToString((encode(id) + ":" + encode(secret)).getBytes(StandardCharsets.UTF_8));
  }
  public static String bearer(String token) {
    require(token != null && token.matches("[A-Za-z0-9._~+/-]{1,8192}={0,2}"));
    return "Bearer " + token;
  }
  public static void endpoint(URI uri, boolean allowQuery) {
    require(uri != null && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
        && (allowQuery || uri.getQuery() == null)
        && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
        && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()))));
  }
  public static List<String> values(Map<String, ? extends Collection<String>> headers, String name) {
    return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
        .flatMap(e -> e.getValue().stream()).toList();
  }
  public static void contract(Map<String, ? extends Collection<String>> headers, String expected) {
    if (expected != null) require(values(headers, CONTRACT).equals(List.of(expected)));
  }
  public static void require(boolean ok) { if (!ok) throw AppointmentError.unavailable(); }
}

