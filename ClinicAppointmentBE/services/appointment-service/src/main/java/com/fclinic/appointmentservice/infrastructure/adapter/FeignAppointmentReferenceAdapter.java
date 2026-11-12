package com.fclinic.appointmentservice.infrastructure.adapter;

import static com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards.*;
import static com.fclinic.appointmentservice.infrastructure.client.config.WireValues.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.config.AppointmentSettings;
import com.fclinic.appointmentservice.domain.*;
import com.fclinic.appointmentservice.infrastructure.client.auth.*;
import com.fclinic.appointmentservice.infrastructure.client.auth.dto.*;
import com.fclinic.appointmentservice.infrastructure.client.config.*;
import com.fclinic.appointmentservice.infrastructure.client.doctor.DoctorClient;
import com.fclinic.appointmentservice.infrastructure.client.doctor.dto.DoctorSlotDto;
import com.fclinic.appointmentservice.infrastructure.client.patient.PatientClient;
import com.fclinic.appointmentservice.infrastructure.client.patient.dto.PatientProfileDto;
import com.fclinic.appointmentservice.infrastructure.security.PatientServiceTokenProvider;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class FeignAppointmentReferenceAdapter implements PatientReferencePort, DoctorReferencePort, RecipientLookupPort {
  private final PatientClient patients;
  private final DoctorClient doctors;
  private final AuthRecipientClient accounts;
  private final AppointmentSettings settings;
  private final PatientServiceTokenProvider patientTokens;
  public FeignAppointmentReferenceAdapter(PatientClient patients, DoctorClient doctors, AuthRecipientClient accounts,
      AppointmentSettings settings, PatientServiceTokenProvider patientTokens) {
    this.patients = patients; this.doctors = doctors; this.accounts = accounts; this.settings = settings; this.patientTokens = patientTokens;
  }
  public PatientReference patient(UUID patient, long deadline) {
    return FeignCalls.call(() -> {
      var n = patientProfile(patient, deadline, true);
      return new PatientReference(patient, text(n.fullName(), 255), text(n.phoneNumber(), 16));
    });
  }
  private PatientProfileDto patientProfile(UUID patient, long deadline, boolean active) {
    var n = patients.getPatient(patient, bearer(patientTokens.token(deadline)), FeignCalls.options(deadline)).validated();
    require(id(n.id()).equals(patient) && ("PAT-" + patient).equals(text(n.patientCode(), 40)));
    require(n.status() != null && Set.of("ACTIVE", "INACTIVE").contains(n.status()));
    if (active && !"ACTIVE".equals(n.status()))
      throw AppointmentError.conflict("PATIENT_INACTIVE", "Hồ sơ bệnh nhân đang ngừng hoạt động.");
    require(text(n.phoneNumber(), 16).matches("\\+[1-9][0-9]{7,14}"));
    return n;
  }
  public DoctorReference doctor(UUID doctor, long deadline) {
    return FeignCalls.call(() -> {
      var n = data(doctors.getDoctor(doctor, FeignCalls.options(deadline)), "appointment-doctor-reference/1");
      require(id(n.id()).equals(doctor) && n.active() != null);
      if (!n.active()) throw AppointmentError.conflict("DOCTOR_INACTIVE", "Bác sĩ đang ngừng nhận lịch.");
      require(n.consultationFee() != null && n.consultationFee().signum() >= 0);
      return new DoctorReference(doctor, text(n.name(), 255), text(n.specialty(), 255), n.consultationFee());
    });
  }
  public Slot slot(UUID doctor, UUID slot, long deadline) {
    return FeignCalls.call(() -> {
      var n = data(doctors.getSlot(doctor, slot, FeignCalls.options(deadline)), "appointment-doctor-reference/1");
      require(id(n.id()).equals(slot));
      return parseSlot(n);
    });
  }
  public List<Slot> offered(UUID doctor, LocalDate date, long deadline) {
    return FeignCalls.call(() -> {
      var values = data(doctors.getSlots(doctor, date.toString(), FeignCalls.options(deadline)), "appointment-doctor-reference/1");
      require(values.size() <= 1000);
      List<Slot> result = new ArrayList<>(); Set<UUID> ids = new HashSet<>();
      for (var n : values) {
        Slot slot = parseSlot(n);
        require(slot.doctorId().equals(doctor) && slot.date().equals(date) && ids.add(slot.id()));
        result.add(slot);
      }
      return result;
    });
  }
  private Slot parseSlot(DoctorSlotDto n) {
    require(n.published() != null && text(n.slotDate(), 10).length() == 10
        && text(n.startTime(), 8).length() == 8 && text(n.endTime(), 8).length() == 8);
    return new Slot(id(n.id()), id(n.doctorId()), LocalDate.parse(n.slotDate()),
        LocalTime.parse(n.startTime()), LocalTime.parse(n.endTime()), n.published());
  }
  public List<Recipient> recipients(UUID patient, UUID doctor) {
    return FeignCalls.call(() -> {
      String url = settings.value("recipient-url"); require(!url.isEmpty());
      long deadline = FeignCalls.deadline();
      var profile = patientProfile(patient, deadline, false);
      var n = data(accounts.accounts(URI.create(url), new RecipientLookupRequestDto(patient, doctor),
          FeignCalls.options(deadline)), "appointment-recipient-accounts/1");
      require(id(n.patientId()).equals(patient) && id(n.doctorId()).equals(doctor) && n.doctor() != null);
      UUID patientAccount = n.patientUserId() == null ? null : id(n.patientUserId());
      return List.of(new Recipient("PATIENT", patientAccount, text(profile.fullName(), 255), email(profile.email())),
          new Recipient("DOCTOR", id(n.doctor().userId()), text(n.doctor().name(), 255), email(n.doctor().email())));
    });
  }
  private static <T> T data(ResponseEntity<ProviderResponse<T>> response, String expected) {
    require(List.of(expected).equals(response.getHeaders().get(CONTRACT))); require(response.getBody() != null);
    return response.getBody().validated();
  }
}
