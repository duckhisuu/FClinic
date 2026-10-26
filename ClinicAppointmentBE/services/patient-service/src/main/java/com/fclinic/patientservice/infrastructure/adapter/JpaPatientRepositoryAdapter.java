package com.fclinic.patientservice.infrastructure.adapter;

import com.fclinic.patientservice.application.port.out.PatientRepositoryPort;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.aggregate.MedicalRecord;
import com.fclinic.patientservice.domain.model.*;
import com.fclinic.patientservice.infrastructure.entity.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;

@Repository
@Transactional(readOnly=true)
public class JpaPatientRepositoryAdapter implements PatientRepositoryPort {
    @PersistenceContext private EntityManager em;

    public Optional<Patient> find(UUID id) { return Optional.ofNullable(em.find(JpaPatientEntity.class, id)).map(this::patient); }
    public Optional<Patient> lock(UUID id) {
        return Optional.ofNullable(em.find(JpaPatientEntity.class, id, LockModeType.PESSIMISTIC_WRITE)).map(this::patient);
    }
    public Patient insert(Patient p) {
        var e = new JpaPatientEntity();
        e.setId(p.id()); e.setPatientCode(p.patientCode()); e.setCreatedAt(p.createdAt()); e.setCreatedBy(p.createdBy());
        apply(e, p); em.persist(e); em.flush(); return patient(e);
    }
    public Patient update(Patient p) {
        var e = em.find(JpaPatientEntity.class, p.id());
        PatientError.checkVersion(p.version(), e.getVersion());
        apply(e, p); em.flush(); return patient(e);
    }
    public void checkAtCommit(Patient p) {
        var e = em.find(JpaPatientEntity.class, p.id());
        PatientError.checkVersion(p.version(), e.getVersion());
        em.lock(e, LockModeType.OPTIMISTIC);
    }
    public PageResult<Patient> search(SearchQuery q, Set<UUID> allowed) {
        if (allowed.isEmpty()) return new PageResult<>(List.of(), q.page(), q.size(), 0, 0);
        String where = " where p.id in :allowed";
        Map<String,Object> params = new HashMap<>(); params.put("allowed", allowed);
        if (q.keyword() != null) {
            where += " and (locate(:keyword, lower(p.fullName)) > 0 or locate(:keyword, lower(p.patientCode)) > 0)";
            params.put("keyword", q.keyword().toLowerCase(Locale.ROOT));
        }
        if (q.phoneNumber() != null) { where += " and p.phoneNumber = :phone"; params.put("phone", q.phoneNumber()); }
        String field = switch(q.sortField()) {
            case "fullName" -> "fullName"; case "createdAt" -> "createdAt";
            default -> throw PatientError.invalid("sort");
        };
        return page("JpaPatientEntity", "p", where, params, q, field, JpaPatientEntity.class, this::patient);
    }
    public Optional<MedicalRecord> findRecord(UUID patientId, long id) {
        return em.createQuery("select r from JpaMedicalRecordEntity r where r.patientId=:p and r.id=:id",
                JpaMedicalRecordEntity.class).setParameter("p", patientId).setParameter("id", id)
                .getResultStream().findFirst().map(this::record);
    }
    public MedicalRecord insertRecord(MedicalRecord r) {
        var e = new JpaMedicalRecordEntity();
        e.setPatientId(r.patientId()); e.setDoctorId(r.doctorId()); e.setAppointmentId(r.appointmentId());
        e.setCreatedAt(r.createdAt()); e.setCreatedBy(r.createdBy());
        apply(e, r); em.persist(e); em.flush(); return record(e);
    }
    public MedicalRecord updateRecord(MedicalRecord r) {
        var e = em.find(JpaMedicalRecordEntity.class, r.id());
        PatientError.checkVersion(r.version(), e.getVersion());
        apply(e, r); em.flush(); return record(e);
    }
    public void checkAtCommit(MedicalRecord r) {
        var e = em.find(JpaMedicalRecordEntity.class, r.id());
        PatientError.checkVersion(r.version(), e.getVersion());
        em.lock(e, LockModeType.OPTIMISTIC);
    }
    public PageResult<MedicalRecord> records(UUID patientId, SearchQuery q) {
        String where = " where r.patientId=:p";
        Map<String,Object> params = new HashMap<>(); params.put("p", patientId);
        if (q.appointmentId() != null) { where += " and r.appointmentId=:appointment"; params.put("appointment", q.appointmentId()); }
        if (!"visitDate".equals(q.sortField())) throw PatientError.invalid("sort");
        return page("JpaMedicalRecordEntity", "r", where, params, q, "visitDate", JpaMedicalRecordEntity.class, this::record);
    }
    private <E,T> PageResult<T> page(String entity, String alias, String where, Map<String,Object> params,
                                    SearchQuery q, String field, Class<E> type, Function<E,T> mapper) {
        var count = em.createQuery("select count(" + alias + ") from " + entity + " " + alias + where, Long.class);
        String direction = q.ascending() ? " asc" : " desc";
        var rows = em.createQuery("select " + alias + " from " + entity + " " + alias + where
                + " order by " + alias + "." + field + direction + ", " + alias + ".id" + direction, type);
        params.forEach((key, value) -> { count.setParameter(key, value); rows.setParameter(key, value); });
        long total = count.getSingleResult();
        long offset = (long) q.page() * q.size();
        List<T> content = offset > Integer.MAX_VALUE ? List.of()
                : rows.setFirstResult((int) offset).setMaxResults(q.size()).getResultStream().map(mapper).toList();
        return new PageResult<>(content, q.page(), q.size(), total, (int) ((total + q.size() - 1) / q.size()));
    }
    private void apply(JpaPatientEntity e, Patient p) {
        e.setFullName(p.fullName()); e.setPhoneNumber(p.phoneNumber()); e.setEmail(p.email());
        e.setDateOfBirth(p.dateOfBirth()); e.setGender(p.gender()); e.setAddress(p.address()); e.setStatus(p.status());
        e.setUpdatedAt(p.updatedAt()); e.setUpdatedBy(p.updatedBy());
    }
    private void apply(JpaMedicalRecordEntity e, MedicalRecord r) {
        e.setVisitDate(r.visitDate()); e.setSymptoms(r.symptoms()); e.setDiagnosis(r.diagnosis());
        e.setTreatment(r.treatment()); e.setNotes(r.notes()); e.setUpdatedAt(r.updatedAt()); e.setUpdatedBy(r.updatedBy());
    }
    private Patient patient(JpaPatientEntity e) {
        return new Patient(e.getId(), e.getPatientCode(), e.getFullName(), e.getPhoneNumber(), e.getEmail(),
                e.getDateOfBirth(), e.getGender(), e.getAddress(), e.getStatus(), e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getCreatedBy(), e.getUpdatedBy());
    }
    private MedicalRecord record(JpaMedicalRecordEntity e) {
        return new MedicalRecord(e.getId(), e.getPatientId(), e.getDoctorId(), e.getAppointmentId(), e.getVisitDate(),
                e.getSymptoms(), e.getDiagnosis(), e.getTreatment(), e.getNotes(), e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getCreatedBy(), e.getUpdatedBy());
    }
}
