package com.fclinic.appointmentservice.infrastructure.adapter;

import static com.fclinic.appointmentservice.infrastructure.AppointmentJson.*;

import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.config.AppointmentSettings;
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

  public HttpAppointmentReferenceAdapter(ProviderHttp http, AppointmentSettings settings) {
    this.http = http;
    this.settings = settings;
  }

  public PatientReference patient(UUID patient, long deadline) {
    JsonNode n;
    try {
      n =
          http.get(
              settings.patientUrl() + "/api/v2/patients/" + patient,
              settings.serviceToken(),
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
    if (!status.equals("ACTIVE"))
      throw AppointmentError.conflict("PATIENT_INACTIVE", "Hồ sơ bệnh nhân đang ngừng hoạt động.");
    String phone = text(n.path("phoneNumber"), 16);
    require(phone.matches("\\+[1-9][0-9]{7,14}"));
    return new PatientReference(patient, text(n.path("fullName"), 255), phone);
  }

  public DoctorReference doctor(long doctor, long deadline) {
    JsonNode n;
    try {
      n =
          http.get(
              settings.doctorUrl() + "/api/v1/doctors/" + doctor,
              settings.serviceToken(),
              deadline);
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
    require(id(n.path("id")) == doctor && n.path("active").isBoolean());
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

  public Slot slot(long doctor, long slot, long deadline) {
    try {
      JsonNode n =
          http.get(
              settings.doctorUrl() + "/internal/v1/doctors/" + doctor + "/slots/" + slot,
              settings.serviceToken(),
              deadline);
      require(n.isObject() && id(n.path("id")) == slot && n.path("published").isBoolean());
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

  public List<Slot> offered(long doctor, LocalDate date, long deadline) {
    try {
      JsonNode array =
          http.get(
              settings.doctorUrl() + "/api/v1/doctors/" + doctor + "/slots?date=" + date,
              settings.serviceToken(),
              deadline);
      require(array.isArray() && array.size() <= 1000);
      List<Slot> result = new ArrayList<>();
      Set<Long> ids = new HashSet<>();
      for (JsonNode n : array) {
        Slot s = parseSlot(n, true);
        require(s.doctorId() == doctor && s.date().equals(date) && ids.add(s.id()));
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
   * Endpoint supplied by Auth/Doctor owner. No guessed address, account, phone linking or mock
   * fallback.
   */
  public List<Recipient> recipients(UUID patientId, long doctorId) {
    String url = settings.value("recipient-url");
    if (url.isEmpty()) throw AppointmentError.unavailable();
    JsonNode n;
    try {
      n =
          http.post(
              url,
              settings.serviceToken(),
              Map.of("patientId", patientId.toString(), "doctorId", doctorId),
              ProviderHttp.deadline());
    } catch (ProviderHttp.MissingReference e) {
      throw AppointmentError.unavailable();
    }
    require(n.isArray() && n.size() == 2);
    Set<String> types = new HashSet<>();
    List<Recipient> result = new ArrayList<>();
    for (JsonNode r : n) {
      String type = text(r.path("recipientType"), 16);
      require(Set.of("PATIENT", "DOCTOR").contains(type) && types.add(type));
      require(r.has("userId") && r.has("email"));
      Long account = r.path("userId").isNull() ? null : id(r.path("userId"));
      require(type.equals("PATIENT") || account != null);
      String name = text(r.path("name"), 255);
      String email = r.path("email").isNull() ? null : text(r.path("email"), 320);
      require(email == null || email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"));
      result.add(new Recipient(type, account, name, email));
    }
    return result;
  }
}
