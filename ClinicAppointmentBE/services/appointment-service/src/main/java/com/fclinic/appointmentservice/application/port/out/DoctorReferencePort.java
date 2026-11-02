package com.fclinic.appointmentservice.application.port.out;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.Slot;
import java.time.*;
import java.util.*;

public interface DoctorReferencePort {
  DoctorReference doctor(UUID doctorId, long deadline);

  Slot slot(UUID doctorId, UUID slotId, long deadline);

  List<Slot> offered(UUID doctorId, LocalDate date, long deadline);
}
