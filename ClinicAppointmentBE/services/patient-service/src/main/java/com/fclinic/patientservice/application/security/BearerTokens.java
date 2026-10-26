package com.fclinic.patientservice.application.security;

import com.fclinic.patientservice.domain.model.PatientError;
import java.util.regex.Pattern;

/** RFC 6750 b64token syntax. Tokens are opaque and are never decoded here. */
public final class BearerTokens {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._~+/\\-]+={0,}");
    private BearerTokens() { }
    public static String requireToken(String token) {
        if (token == null || token.length() > 8192 || !TOKEN.matcher(token).matches())
            throw new PatientError(PatientError.Code.UNAUTHENTICATED);
        return token;
    }
    public static String fromAuthorization(String header) {
        if (header == null || header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7))
            throw new PatientError(PatientError.Code.UNAUTHENTICATED);
        return requireToken(header.substring(7));
    }
}

