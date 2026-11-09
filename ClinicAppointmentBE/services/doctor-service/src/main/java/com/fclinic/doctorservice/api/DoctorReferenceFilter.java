package com.fclinic.doctorservice.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Internal reads have service credentials even when reached directly, without a Gateway. */
@Component
public class DoctorReferenceFilter extends OncePerRequestFilter {
    private final String appointmentId, appointmentSecret, patientId, patientSecret;
    public DoctorReferenceFilter(Environment env) {
        appointmentId = env.getProperty("doctor.references.appointment-client-id", "appointment-doctor-reference");
        appointmentSecret = env.getProperty("doctor.references.appointment-client-secret", "");
        patientId = env.getProperty("doctor.references.patient-client-id", "patient-doctor-reference");
        patientSecret = env.getProperty("doctor.references.patient-client-secret", "");
        if (!appointmentSecret.isBlank() && appointmentSecret.equals(patientSecret))
            throw new IllegalArgumentException("Doctor reference secrets must be distinct");
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/v1/doctors");
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        var headers = Collections.list(req.getHeaders("Authorization"));
        boolean profile = req.getRequestURI().matches("/internal/v1/doctors/[0-9a-f-]{36}");
        boolean appointment = headers.size() == 1 && matches(headers.getFirst(), appointmentId, appointmentSecret);
        boolean patient = headers.size() == 1 && matches(headers.getFirst(), patientId, patientSecret);
        if (!appointment && !patient) { deny(res, 401); return; }
        if (!"GET".equals(req.getMethod()) || (patient && !profile)) { deny(res, 403); return; }
        res.setHeader("X-FClinic-Reference-Contract", patient ? "patient-doctor-reference/2" : "appointment-doctor-reference/1");
        chain.doFilter(req, res);
    }
    private static void deny(HttpServletResponse res, int status) throws IOException {
        res.setStatus(status); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":"+status+",\"message\":\"Service credential required\",\"data\":null,\"code\":\""+
                (status == 401 ? "UNAUTHENTICATED" : "FORBIDDEN")+"\",\"fieldErrors\":[]}");
    }
    private static boolean matches(String header, String id, String secret) {
        if (id.isBlank() || secret.isBlank() || header.length() > 8192 || !header.regionMatches(true, 0, "Basic ", 0, 6)) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':'); if (colon <= 0) return false;
            return MessageDigest.isEqual(id.getBytes(StandardCharsets.UTF_8), URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8))
                & MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), URLDecoder.decode(decoded.substring(colon+1), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) { return false; }
    }
}
