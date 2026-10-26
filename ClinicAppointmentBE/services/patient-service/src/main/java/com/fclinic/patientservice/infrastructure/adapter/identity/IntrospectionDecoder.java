package com.fclinic.patientservice.infrastructure.adapter.identity;

import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.model.PatientError;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.util.*;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

/** Strict local wire profile patient-identity-introspection/1, NOT the draft validateCaller DTO. */
public final class IntrospectionDecoder {
    public static final String VERSION = "patient-identity-introspection/1";
    private static final Set<String> FIELDS = Set.of("active", "contractVersion", "iss", "aud", "exp", "sub",
            "principalType", "userId", "patientId", "doctorId", "authorities");
    private static final Set<String> USER_ROLES = Set.of("ROLE_PATIENT", "ROLE_DOCTOR", "ROLE_STAFF", "ROLE_ADMIN");
    private static final long MAX_ID = 9007199254740991L;
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(8192)
                    .maxNumberLength(20).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final IdentitySettings settings;
    private final Clock clock;

    public IntrospectionDecoder(IdentitySettings settings, Clock clock) { this.settings = settings; this.clock = clock; }

    public TrustedCaller decode(byte[] body) {
        try {
            JsonNode root = JSON.readTree(body);
            check(root != null && root.isObject() && root.has("active") && root.path("active").isBoolean());
            // RFC 7662 inactive credentials disclose no identity metadata.
            if (!root.path("active").asBoolean()) {
                check(root.size() == 1);
                throw new PatientError(UNAUTHENTICATED);
            }
            check(root.size() == FIELDS.size() && root.propertyNames().equals(FIELDS));
            check(VERSION.equals(text(root, "contractVersion", 64)));
            String issuer = text(root, "iss", 512);
            Set<String> audiences = audiences(root.path("aud"));
            long expiry = integer(root.path("exp"), false);
            if (!settings.issuer().equals(issuer) || !audiences.contains(settings.audience())
                    || clock.instant().getEpochSecond() >= expiry) throw new PatientError(UNAUTHENTICATED);
            String subject = text(root, "sub", 255);
            String kind = text(root, "principalType", 16);
            check(kind.equals("USER") || kind.equals("SERVICE"));
            Long userId = nullableId(root.path("userId"));
            Long doctorId = nullableId(root.path("doctorId"));
            UUID patientId = patientId(root.path("patientId"));
            Set<String> authorities = authorities(root.path("authorities"));
            if (kind.equals("USER")) {
                check(userId != null && USER_ROLES.containsAll(authorities));
                // An unlinked account remains authenticated but gains no self/doctor access.
                return new TrustedCaller(subject, TrustedCaller.PrincipalType.USER, authorities, patientId, doctorId, null, true);
            }
            check(userId == null && doctorId == null && patientId == null
                    && authorities.stream().allMatch(a -> a.matches("SVC_[A-Z0-9_]{1,80}")));
            IdentitySettings.ServiceBinding binding = settings.services().get(subject);
            if (binding == null || !binding.authorities().containsAll(authorities) || authorities.isEmpty())
                throw new PatientError(FORBIDDEN);
            // Exact verified subject lookup; neither wire DTO nor browser controls serviceName.
            return new TrustedCaller(subject, TrustedCaller.PrincipalType.SERVICE, authorities, null, null, binding.name(), true);
        } catch (PatientError error) { throw error; }
        catch (RuntimeException error) { throw new PatientError(DEPENDENCY_UNAVAILABLE); }
    }

    private static Set<String> audiences(JsonNode n) {
        if (n.isString()) return Set.of(value(n, 128));
        check(n.isArray() && !n.isEmpty() && n.size() <= 16);
        Set<String> result = new HashSet<>();
        n.forEach(a -> check(result.add(value(a, 128))));
        return result;
    }
    private static Set<String> authorities(JsonNode n) {
        check(n.isArray() && n.size() <= 32);
        Set<String> result = new HashSet<>();
        n.forEach(a -> check(result.add(value(a, 128))));
        return Set.copyOf(result);
    }
    private static UUID patientId(JsonNode n) {
        if (n.isNull()) return null;
        String v = value(n, 36);
        check(v.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        return UUID.fromString(v);
    }
    private static Long nullableId(JsonNode n) { return n.isNull() ? null : integer(n, true); }
    private static long integer(JsonNode n, boolean positive) {
        check(n.isIntegralNumber() && n.canConvertToLong());
        long v = n.longValue();
        check(v >= (positive ? 1 : 0) && v <= MAX_ID);
        return v;
    }
    private static String text(JsonNode root, String field, int max) { return value(root.path(field), max); }
    private static String value(JsonNode n, int max) {
        check(n.isString());
        String v = n.asText();
        check(!v.isBlank() && v.length() <= max && v.chars().noneMatch(Character::isISOControl));
        return v;
    }
    private static void check(boolean condition) { if (!condition) throw new PatientError(DEPENDENCY_UNAVAILABLE); }
}

