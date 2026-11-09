package com.fclinic.appointmentservice.infrastructure.adapter;

import static com.fclinic.appointmentservice.infrastructure.AppointmentJson.*;

import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.config.AppointmentSettings;
import com.fclinic.appointmentservice.config.ProviderCredentials;
import com.fclinic.appointmentservice.domain.*;
import com.fclinic.appointmentservice.infrastructure.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class HttpAppointmentReferenceAdapter
    implements PatientReferencePort, DoctorReferencePort, RecipientLookupPort {

  private final ProviderHttp http;
  private final AppointmentSettings settings;
  private final ProviderCredentials credentials;
  private final com.fclinic.appointmentservice.infrastructure.security.PatientServiceTokenProvider patientTokens;

  public HttpAppointmentReferenceAdapter(ProviderHttp http, AppointmentSettings settings,
      com.fclinic.appointmentservice.infrastructure.security.PatientServiceTokenProvider patientTokens,
      ProviderCredentials credentials) {
    this.http = http;
    this.settings = settings;
    this.patientTokens = patientTokens;
    this.credentials = credentials;
  }

  public PatientReference patient(UUID patient, long deadline) {
    JsonNode n = patientProfile(patient, deadline, true);
    return new PatientReference(patient, text(n.path("fullName"), 255), text(n.path("phoneNumber"), 16));
  }

  private JsonNode patientProfile(UUID patient, long deadline, boolean requireActive) {
    JsonNode n;
    try {
      n =
          http.get(
              settings.patientUrl() + "/api/v3/patients/" + patient,
              patientTokens.token(deadline),
              deadline);
    } catch (ProviderHttp.MissingReference e) {
      throw new AppointmentError(404, "PATIENT_NOT_FOUND", "Không tìm thấy bệnh nhân.");
    }
    require(
        n.isObject()
            && n.propertyNames()
                .containsAll(
                    Set.of(
                        "id",
                        "patientCode",
                        "fullName",
                        "phoneNumber",
                        "email",
                        "dateOfBirth",
                        "gender",
                        "address",
                        "status",
                        "version",
                        "createdAt",
                        "updatedAt",
                        "createdBy",
                        "updatedBy")));
    require(
        patient.toString().equals(text(n.path("id"), 36))
            && ("PAT-" + patient).equals(text(n.path("patientCode"), 40)));
    String status = text(n.path("status"), 16);
    require(Set.of("ACTIVE", "INACTIVE").contains(status));
    if (requireActive && !status.equals("ACTIVE"))
      throw AppointmentError.conflict("PATIENT_INACTIVE", "Hồ sơ bệnh nhân đang ngừng hoạt động.");
    String phone = text(n.path("phoneNumber"), 16);
    require(phone.matches("\\+[1-9][0-9]{7,14}"));
    return n;
  }

  public DoctorReference doctor(UUID doctor, long deadline) {
    JsonNode n;
    try {
      n =
          http.getBasic(settings.doctorUrl() + "/internal/v1/doctors/" + doctor,
              credentials.doctorClientId(), credentials.doctorClientSecret(), deadline, "appointment-doctor-reference/1");
    } catch (ProviderHttp.MissingReference e) {
      throw new AppointmentError(404, "DOCTOR_NOT_FOUND", "Không tìm thấy bác sĩ.");
    }
    require(
        n.isObject()
            && n.propertyNames()
                .containsAll(
                    Set.of(
                        "id",
                        "name",
                        "specialty",
                        "department",
                        "qualification",
                        "experienceYears",
                        "consultationFee",
                        "roomNumber",
                        "bio",
                        "avatarUrl",
                        "active")));
    require(id(n.path("id")).equals(doctor) && n.path("active").isBoolean());
    if (!n.path("active").asBoolean())
      throw AppointmentError.conflict("DOCTOR_INACTIVE", "Bác sĩ đang ngừng nhận lịch.");
    require(n.path("consultationFee").isNumber());
    BigDecimal fee;
    try {
      fee = new BigDecimal(n.path("consultationFee").asText());
    } catch (Exception e) {
      throw AppointmentError.unavailable();
    }
    require(fee.signum() >= 0);
    return new DoctorReference(
        doctor, text(n.path("name"), 255), text(n.path("specialty"), 255), fee);
  }

  public Slot slot(UUID doctor, UUID slot, long deadline) {
    try {
      JsonNode n =
          http.getBasic(
              settings.doctorUrl() + "/internal/v1/doctors/" + doctor + "/slots/" + slot,
              credentials.doctorClientId(), credentials.doctorClientSecret(), deadline, "appointment-doctor-reference/1");
      require(n.isObject() && id(n.path("id")).equals(slot) && n.path("published").isBoolean());
      return parseSlot(n, n.path("published").asBoolean());
    } catch (ProviderHttp.MissingReference e) {
      // The report does not name the provider's missing-slot code (D-18).
      // At this business boundary, an absent published offer is not bookable.
      throw AppointmentError.conflict(
          "SLOT_NOT_BOOKABLE", "Khung giờ không tồn tại hoặc không thuộc bác sĩ đã chọn.");
    } catch (AppointmentError e) {
      throw e;
    } catch (RuntimeException e) {
      throw AppointmentError.unavailable();
    }
  }

  public List<Slot> offered(UUID doctor, LocalDate date, long deadline) {
    try {
      JsonNode array =
          http.getBasic(settings.doctorUrl() + "/internal/v1/doctors/" + doctor + "/slots?date=" + date,
              credentials.doctorClientId(), credentials.doctorClientSecret(), deadline, "appointment-doctor-reference/1");
      require(array.isArray() && array.size() <= 1000);
      List<Slot> result = new ArrayList<>();
      Set<UUID> ids = new HashSet<>();
      for (JsonNode n : array) {
        require(n.path("published").isBoolean());
        Slot s = parseSlot(n, n.path("published").asBoolean());
        require(s.doctorId().equals(doctor) && s.date().equals(date) && ids.add(s.id()));
        result.add(s);
      }
      return result;
    } catch (ProviderHttp.MissingReference e) {
      throw new AppointmentError(404, "DOCTOR_NOT_FOUND", "Không tìm thấy bác sĩ.");
    } catch (AppointmentError e) {
      throw e;
    } catch (RuntimeException e) {
      throw AppointmentError.unavailable();
    }
  }

  private Slot parseSlot(JsonNode n, boolean published) {
    require(
        text(n.path("slotDate"), 10).length() == 10
            && text(n.path("startTime"), 8).length() == 8
            && text(n.path("endTime"), 8).length() == 8);
    return new Slot(
        id(n.path("id")),
        id(n.path("doctorId")),
        LocalDate.parse(text(n.path("slotDate"), 10)),
        LocalTime.parse(text(n.path("startTime"), 8)),
        LocalTime.parse(text(n.path("endTime"), 8)),
        published);
  }

  /**
   * Account IDs come only from Auth's exact business mappings. Patient contacts come from Patient.
   */
  public List<Recipient> recipients(UUID patientId, UUID doctorId) {
    String url = settings.value("recipient-url");
    if (url.isEmpty()) throw AppointmentError.unavailable();
    long deadline = ProviderHttp.deadline();
    JsonNode profile = patientProfile(patientId, deadline, false);
    JsonNode n;
    try {
      n =
          http.postBasic(url, credentials.recipientClientId(), credentials.recipientClientSecret(),
              Map.of("patientId", patientId.toString(), "doctorId", doctorId),
              deadline, "appointment-recipient-accounts/1");
    } catch (ProviderHttp.MissingReference e) {
      throw AppointmentError.unavailable();
    }
    require(n.isObject() && n.propertyNames().equals(Set.of("patientId", "doctorId", "patientUserId", "doctor")));
    require(id(n.path("patientId")).equals(patientId) && id(n.path("doctorId")).equals(doctorId));
    UUID patientAccount = n.path("patientUserId").isNull() ? null : id(n.path("patientUserId"));
    JsonNode doctor = n.path("doctor");
    require(doctor.isObject() && doctor.propertyNames().equals(Set.of("userId", "name", "email")));
    return List.of(new Recipient("PATIENT", patientAccount, text(profile.path("fullName"), 255), email(profile.path("email"))),
        new Recipient("DOCTOR", id(doctor.path("userId")), text(doctor.path("name"), 255), email(doctor.path("email"))));
  }

  private String email(JsonNode value) {
    if (value.isNull()) return null;
    String result = text(value, 320);
    require(result.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"));
    return result;
  }
}
