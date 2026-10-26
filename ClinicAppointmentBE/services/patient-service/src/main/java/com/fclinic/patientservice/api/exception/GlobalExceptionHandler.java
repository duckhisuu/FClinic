package com.fclinic.patientservice.api.exception;

import com.fclinic.patientservice.domain.model.PatientError;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public record ErrorResponse(int status, String message, Object data, String code, List<PatientError.FieldIssue> fieldErrors) { }
    @ExceptionHandler(PatientError.class)
    ResponseEntity<ErrorResponse> business(PatientError ex) { return error(ex.code(), ex.fields()); }
    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ErrorResponse> stale(Exception ex) { return error(VERSION_CONFLICT,List.of()); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformed(Exception ex) {
        return error(VALIDATION_ERROR,List.of(new PatientError.FieldIssue("body","Invalid JSON body")));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> constraint(DataIntegrityViolationException ex) {
        // Inspect constraint identity, never expose database or clinical values from its message.
        for (Throwable t=ex; t!=null; t=t.getCause())
            if (t instanceof ConstraintViolationException c
                    && "medical_records_appointment_id_key".equals(c.getConstraintName()))
                return error(APPOINTMENT_RECORD_EXISTS,List.of());
        return error(INTERNAL_SERVER_ERROR,List.of());
    }
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    ResponseEntity<Void> absent(Exception ex) {
        // Outside the v2 resource contract (including retired v1); no invented domain code.
        return ResponseEntity.notFound().build();
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Void> method(Exception ex) { return ResponseEntity.status(405).build(); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex) { return error(INTERNAL_SERVER_ERROR,List.of()); }
    private ResponseEntity<ErrorResponse> error(PatientError.Code code, List<PatientError.FieldIssue> fields) {
        return ResponseEntity.status(code.status).body(new ErrorResponse(code.status, code.name(),null,code.name(),fields));
    }
}
