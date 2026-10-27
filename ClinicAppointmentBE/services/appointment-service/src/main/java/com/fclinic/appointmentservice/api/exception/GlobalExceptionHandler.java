package com.fclinic.appointmentservice.api.exception;

import com.fclinic.appointmentservice.domain.AppointmentError;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  public static Map<String, Object> body(int status, String code, String message, List<?> fields) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("status", status);
    m.put("message", message);
    m.put("data", null);
    m.put("code", code);
    m.put("fieldErrors", fields);
    return m;
  }

  @ExceptionHandler(AppointmentError.class)
  public ResponseEntity<?> business(AppointmentError e) {
    return ResponseEntity.status(e.status())
        .body(body(e.status(), e.code(), e.getMessage(), List.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    var fields =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> Map.of("field", f.getField(), "message", "Giá trị không hợp lệ."))
            .distinct()
            .toList();
    return ResponseEntity.badRequest()
        .body(body(400, "VALIDATION_ERROR", "Vui lòng kiểm tra dữ liệu.", fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class,
    MissingServletRequestParameterException.class,
    HandlerMethodValidationException.class
  })
  public ResponseEntity<?> malformed(Exception e) {
    return ResponseEntity.badRequest()
        .body(body(400, "VALIDATION_ERROR", "Dữ liệu hoặc tham số không hợp lệ.", List.of()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> internal(Exception e) {
    return ResponseEntity.internalServerError()
        .body(
            body(
                500,
                "INTERNAL_SERVER_ERROR",
                "Không thể hoàn tất thao tác. Vui lòng thử lại.",
                List.of()));
  }
}
