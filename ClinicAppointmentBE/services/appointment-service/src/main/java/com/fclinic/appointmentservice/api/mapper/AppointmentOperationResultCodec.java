package com.fclinic.appointmentservice.api.mapper;

import com.fclinic.appointmentservice.api.dto.AppointmentResponse;
import com.fclinic.appointmentservice.application.port.out.OperationResultCodecPort;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.infrastructure.AppointmentJson;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AppointmentOperationResultCodec implements OperationResultCodecPort {
  public AppointmentOperationResult encode(AppointmentView view, int status) {
    return new AppointmentOperationResult(
        status,
        AppointmentJson.write(
            Map.of(
                "status",
                status,
                "message",
                "Thành công",
                "data",
                AppointmentResponse.from(view))));
  }
}
