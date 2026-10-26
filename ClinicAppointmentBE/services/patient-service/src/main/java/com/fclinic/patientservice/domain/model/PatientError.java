package com.fclinic.patientservice.domain.model;
import java.util.List;

public class PatientError extends RuntimeException {
    public enum Code {
        VALIDATION_ERROR(400), UNAUTHENTICATED(401), FORBIDDEN(403), PATIENT_NOT_FOUND(404),
        MEDICAL_RECORD_NOT_FOUND(404), VERSION_CONFLICT(409), PATIENT_INACTIVE(409),
        APPOINTMENT_RECORD_EXISTS(409), REFERENCE_MISMATCH(422), DEPENDENCY_UNAVAILABLE(503),
        INTERNAL_SERVER_ERROR(500);
        public final int status;
        Code(int status) { this.status = status; }
    }
    public record FieldIssue(String field, String message) { }
    private final Code code;
    private final List<FieldIssue> fields;
    public PatientError(Code code) { this(code, List.of()); }
    public PatientError(Code code, List<FieldIssue> fields) {
        super(code.name());
        this.code = code;
        this.fields = List.copyOf(fields);
    }
    public Code code() { return code; }
    public List<FieldIssue> fields() { return fields; }
    public static PatientError invalid(String field) {
        return new PatientError(Code.VALIDATION_ERROR, List.of(new FieldIssue(field, "Invalid value")));
    }
    public static void checkVersion(long expected, long actual) {
        if (expected != actual) throw new PatientError(Code.VERSION_CONFLICT);
    }
}
