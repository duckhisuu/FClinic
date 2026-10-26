package com.fclinic.patientservice.api.dto;

import com.fclinic.patientservice.domain.model.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.fclinic.patientservice.domain.model.PatientError.invalid;

/** Explicit map binding prevents numeric/string coercion and preserves omitted-vs-null semantics. */
@Component
public class PatientInput {
    private static final Set<String> PROFILE = Set.of("fullName","phoneNumber","email","dateOfBirth","gender","address");
    private static final Set<String> CLINICAL = Set.of("visitDate","symptoms","diagnosis","treatment","notes");
    private static final long SAFE_ID = 9007199254740991L;
    private final Clock clock;
    private final Validator validator;
    public PatientInput(Clock clock, Validator validator) { this.clock = clock; this.validator = validator; }
    private static class EmailValue { @Email String value; }

    public ProfileData profile(Map<String,Object> body, boolean replace) {
        fields(body, replace ? union(PROFILE, "status", "version") : PROFILE);
        String name = requiredText(body, "fullName", 255);
        String phone = phone(text(body, "phoneNumber", true, 40));
        String email = text(body, "email", false, 255);
        if (email != null && !validator.validateValue(EmailValue.class, "value", email).isEmpty()) throw invalid("email");
        LocalDate dob = date(body);
        ProfileData.Gender gender = body.containsKey("gender") || replace
                ? enumeration(body, "gender", ProfileData.Gender.class) : ProfileData.Gender.UNKNOWN;
        ProfileData.Status status = replace ? enumeration(body, "status", ProfileData.Status.class) : ProfileData.Status.ACTIVE;
        if (replace) version(body);
        return new ProfileData(name, phone, email, dob, gender, text(body, "address", false, 2000), status);
    }
    public ClinicalData clinical(Map<String,Object> body, boolean replace) {
        fields(body, replace ? union(CLINICAL, "version", "changeReason") : union(CLINICAL, "doctorId", "appointmentId"));
        if (replace) { version(body); reason(body); }
        else {
            positiveId(body.get("doctorId"), "doctorId");
            if (body.get("appointmentId") != null) positiveId(body.get("appointmentId"), "appointmentId");
        }
        String value = text(body, "visitDate", true, 100);
        Instant visit;
        try { visit = OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeException ex) { throw invalid("visitDate"); }
        if (visit.isAfter(clock.instant())) throw invalid("visitDate");
        return new ClinicalData(visit.truncatedTo(ChronoUnit.MICROS), requiredText(body,"symptoms",2000), requiredText(body,"diagnosis",2000),
                text(body,"treatment",false,4000), text(body,"notes",false,4000));
    }
    public long version(Map<String,Object> body) { return integer(body.get("version"), "version", 0, Long.MAX_VALUE); }
    public String reason(Map<String,Object> body) { return requiredText(body, "changeReason", 1000); }
    public long positiveId(Object value, String field) { return integer(value, field, 1, SAFE_ID); }
    public UUID uuid(String value, String field) {
        if (value == null || !value.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
            throw invalid(field);
        return UUID.fromString(value);
    }
    public long pathId(String value, String field) {
        if (value == null || !value.matches("[0-9]+")) throw invalid(field);
        try { return positiveId(Long.parseLong(value), field); }
        catch (NumberFormatException ex) { throw invalid(field); }
    }
    public SearchQuery query(Map<String,String> params, boolean medical) {
        int page = queryInteger(params, "page", 0, 0, Integer.MAX_VALUE);
        int size = queryInteger(params, "size", 10, 1, 100);
        String sort = params.getOrDefault("sort", medical ? "visitDate,desc" : "fullName,asc");
        Set<String> validSorts = medical ? Set.of("visitDate,asc","visitDate,desc")
                : Set.of("fullName,asc","fullName,desc","createdAt,asc","createdAt,desc");
        if (!validSorts.contains(sort)) throw invalid("sort");
        String keyword = params.get("keyword");
        if (keyword != null) {
            if (keyword.length() > 255) throw invalid("keyword");
            keyword = keyword.strip(); if (keyword.isEmpty()) keyword = null;
        }
        String phone = params.containsKey("phoneNumber") ? phone(params.get("phoneNumber")) : null;
        Long appointment = medical && params.containsKey("appointmentId") ? pathId(params.get("appointmentId"), "appointmentId") : null;
        return new SearchQuery(keyword, phone, appointment, page, size, sort.split(",")[0], sort.endsWith(",asc"));
    }
    public String phone(String input) {
        if (input == null || input.isBlank() || input.length() > 40 || !input.matches("^[+0-9 ()-]+$"))
            throw invalid("phoneNumber");
        String phone = input.replaceAll("[ ()-]", "");
        if (phone.matches("0[0-9]{9}")) phone = "+84" + phone.substring(1);
        if (!phone.matches("^\\+[1-9][0-9]{7,14}$")) throw invalid("phoneNumber");
        return phone;
    }
    private LocalDate date(Map<String,Object> body) {
        Object raw = body.get("dateOfBirth");
        if (raw == null) return null;
        if (!(raw instanceof String value) || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid("dateOfBirth");
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Saigon"))))) throw invalid("dateOfBirth");
            return date;
        } catch (DateTimeException ex) { throw invalid("dateOfBirth"); }
    }
    private static long integer(Object value, String field, long min, long max) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof java.math.BigInteger)) throw invalid(field);
        try {
            long number = new BigDecimal(value.toString()).longValueExact();
            if (number < min || number > max) throw invalid(field);
            return number;
        } catch (ArithmeticException ex) { throw invalid(field); }
    }
    private static int queryInteger(Map<String,String> params, String field, int defaultValue, int min, int max) {
        if (!params.containsKey(field)) return defaultValue;
        String value = params.get(field);
        if (value == null || !value.matches("[0-9]+")) throw invalid(field);
        try { long n = Long.parseLong(value); if (n < min || n > max) throw invalid(field); return (int) n; }
        catch (NumberFormatException ex) { throw invalid(field); }
    }
    private static <E extends Enum<E>> E enumeration(Map<String,Object> body, String field, Class<E> type) {
        Object raw = body.get(field);
        if (!(raw instanceof String value)) throw invalid(field);
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException ex) { throw invalid(field); }
    }
    private static String requiredText(Map<String,Object> body, String field, int max) { return text(body,field,true,max); }
    private static String text(Map<String,Object> body, String field, boolean required, int max) {
        Object raw = body.get(field);
        if (raw == null) { if (required) throw invalid(field); return null; }
        if (!(raw instanceof String value) || value.codePointCount(0, value.length()) > max) throw invalid(field);
        String trimmed = value.strip();
        if (trimmed.isEmpty()) { if (required) throw invalid(field); return null; }
        return trimmed;
    }
    private static Set<String> union(Set<String> original, String... fields) {
        Set<String> result = new HashSet<>(original); result.addAll(List.of(fields)); return result;
    }
    private static void fields(Map<String,Object> body, Set<String> allowed) {
        if (body == null) throw invalid("body");
        // Never echo an attacker-controlled property name into routine error output.
        if (!allowed.containsAll(body.keySet())) throw invalid("body");
    }
}
