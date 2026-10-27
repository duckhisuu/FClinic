package com.fclinic.appointmentservice.domain.aggregate;

import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.Slot;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.appointmentservice.domain.vo.BookingCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public class Appointment {
  public static final ZoneId CLINIC = ZoneId.of("Asia/Saigon");
  private Long id;
  private BookingCode bookingCode;
  private UUID patientId;
  private String patientName;
  private String patientPhone;
  private Long doctorId;
  private String doctorName;
  private String doctorSpecialty;
  private Long slotId;
  private LocalDate appointmentDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String reasonForVisit;
  private BigDecimal consultationFee;
  private AppointmentStatus status;
  private long version;
  private Instant createdAt;
  private Instant updatedAt;

  public Appointment(
      Long id,
      BookingCode bookingCode,
      UUID patientId,
      String patientName,
      String patientPhone,
      Long doctorId,
      String doctorName,
      String doctorSpecialty,
      Long slotId,
      LocalDate appointmentDate,
      LocalTime startTime,
      LocalTime endTime,
      String reasonForVisit,
      BigDecimal consultationFee,
      AppointmentStatus status,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.bookingCode = bookingCode;
    this.patientId = patientId;
    this.patientName = patientName;
    this.patientPhone = patientPhone;
    this.doctorId = doctorId;
    this.doctorName = doctorName;
    this.doctorSpecialty = doctorSpecialty;
    this.slotId = slotId;
    this.appointmentDate = appointmentDate;
    this.startTime = startTime;
    this.endTime = endTime;
    this.reasonForVisit = reasonForVisit;
    this.consultationFee = consultationFee;
    this.status = status;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Appointment createNew(
      UUID patientId,
      String patientName,
      String patientPhone,
      long doctorId,
      String doctorName,
      String doctorSpecialty,
      Slot slot,
      String reason,
      BigDecimal fee,
      Clock clock) {
    slot.requireBookable(doctorId, clock);
    Instant now = clock.instant();
    return new Appointment(
        null,
        BookingCode.generate(),
        patientId,
        patientName,
        patientPhone,
        doctorId,
        doctorName,
        doctorSpecialty,
        slot.id(),
        slot.date(),
        slot.start(),
        slot.end(),
        reason,
        fee,
        AppointmentStatus.CONFIRMED,
        0,
        now,
        now);
  }

  public Appointment copy() {
    return new Appointment(
        id,
        bookingCode,
        patientId,
        patientName,
        patientPhone,
        doctorId,
        doctorName,
        doctorSpecialty,
        slotId,
        appointmentDate,
        startTime,
        endTime,
        reasonForVisit,
        consultationFee,
        status,
        version,
        createdAt,
        updatedAt);
  }

  public Instant startInstant() {
    return appointmentDate.atTime(startTime).atZone(CLINIC).toInstant();
  }

  public void requireChange(Clock clock, long expectedVersion) {
    if (version != expectedVersion)
      throw AppointmentError.conflict(
          "VERSION_CONFLICT", "Lịch hẹn đã thay đổi. Vui lòng tải lại.");
    if (status != AppointmentStatus.CONFIRMED || !startInstant().isAfter(clock.instant()))
      throw AppointmentError.conflict(
          "INVALID_TRANSITION", "Chỉ được thay đổi lịch đã xác nhận trước giờ khám.");
  }

  public void cancel(long expectedVersion, Clock clock) {
    requireChange(clock, expectedVersion);
    status = AppointmentStatus.CANCELLED;
    version++;
    updatedAt = clock.instant();
  }

  public void reschedule(Slot destination, long expectedVersion, Clock clock) {
    requireChange(clock, expectedVersion);
    destination.requireBookable(doctorId, clock);
    if (slotId == destination.id()
        || (appointmentDate.equals(destination.date()) && startTime.equals(destination.start())))
      throw AppointmentError.conflict(
          "INVALID_TRANSITION", "Vui lòng chọn khung giờ khác lịch hiện tại.");
    slotId = destination.id();
    appointmentDate = destination.date();
    startTime = destination.start();
    endTime = destination.end();
    version++;
    updatedAt = clock.instant();
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getId() {
    return id;
  }

  public BookingCode getBookingCode() {
    return bookingCode;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public String getPatientName() {
    return patientName;
  }

  public String getPatientPhone() {
    return patientPhone;
  }

  public Long getDoctorId() {
    return doctorId;
  }

  public String getDoctorName() {
    return doctorName;
  }

  public String getDoctorSpecialty() {
    return doctorSpecialty;
  }

  public Long getSlotId() {
    return slotId;
  }

  public LocalDate getAppointmentDate() {
    return appointmentDate;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public String getReasonForVisit() {
    return reasonForVisit;
  }

  public BigDecimal getConsultationFee() {
    return consultationFee;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
