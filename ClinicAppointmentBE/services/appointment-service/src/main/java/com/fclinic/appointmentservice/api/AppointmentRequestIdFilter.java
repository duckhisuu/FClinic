package com.fclinic.appointmentservice.api;

import com.fclinic.appointmentservice.api.exception.GlobalExceptionHandler;
import com.fclinic.appointmentservice.infrastructure.AppointmentJson;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Correlation and retired API response only. Does not authenticate or authorize requests. */
@Component
public class AppointmentRequestIdFilter extends OncePerRequestFilter {
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v2/appointments")
        && !request.getRequestURI().startsWith("/api/v1/appointments");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null
        || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
      if (requestId != null) {
        response.setStatus(400);
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                AppointmentJson.write(
                    GlobalExceptionHandler.body(
                        400, "VALIDATION_ERROR", "X-Request-Id không hợp lệ.", List.of())));
        return;
      }
      requestId = UUID.randomUUID().toString();
    }
    response.setHeader("X-Request-Id", requestId);
    if (request.getRequestURI().startsWith("/api/v1/appointments")) {
      response.setStatus(410);
      response.setContentType("application/json");
      response
          .getWriter()
          .write(
              AppointmentJson.write(
                  Map.of(
                      "status", 410, "message", "API lịch hẹn v1 đã ngừng sử dụng. Hãy dùng v2.")));
      return;
    }
    chain.doFilter(request, response);
  }
}
