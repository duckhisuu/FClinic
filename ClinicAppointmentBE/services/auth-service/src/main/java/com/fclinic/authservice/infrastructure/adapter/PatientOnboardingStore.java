package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.domain.exception.PatientOnboardingException;
import com.fclinic.authservice.application.port.out.PatientOnboardingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

/** Short local transactions only. The durable reservation survives all downstream failures. */
@Component
public class PatientOnboardingStore implements PatientOnboardingPort {
    private final JdbcTemplate jdbc;
    public PatientOnboardingStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Reservation> find(UUID user) {
        return jdbc.query("SELECT * FROM patient_onboardings WHERE user_id=?", (rs, i) -> new Reservation(
            rs.getObject("user_id",UUID.class), rs.getObject("patient_id",UUID.class), rs.getObject("subject",UUID.class),
            rs.getString("request_body"), rs.getString("command_body"), rs.getString("request_hash")), user).stream().findFirst();
    }
    @Transactional
    public Reservation reserve(UUID user, UUID subject, String command, String body, String hash) {
        UUID linked = lockOwner(user, subject);
        var existing = find(user);
        if (existing.isPresent()) {
            var r = existing.get();
            if (!r.subject().equals(subject) || !r.commandBody().equals(command)
                    || (linked != null && !linked.equals(r.patientId())))
                throw conflict();
            return r;
        }
        if (linked != null) throw conflict();
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patient_onboardings(user_id,patient_id,subject,request_body,command_body,request_hash) VALUES(?,?,?,?,?,?)",
            user, patient, subject, body, command, hash);
        return new Reservation(user, patient, subject, body, command, hash);
    }
    @Transactional
    public void bind(Reservation r) {
        UUID linked = lockOwner(r.userId(), r.subject());
        if (linked != null && !linked.equals(r.patientId())) throw conflict();
        jdbc.update("UPDATE users SET patient_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", r.patientId(), r.userId());
    }
    private UUID lockOwner(UUID user, UUID subject) {
        var rows = jdbc.queryForList("SELECT u.keycloak_id,u.patient_id,u.active,r.role_name FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=? FOR UPDATE OF u", user);
        if (rows.size()!=1) throw new AccessDeniedException("Account unavailable");
        var row=rows.getFirst();
        if (!Boolean.TRUE.equals(row.get("active")) || !subject.equals(row.get("keycloak_id"))
                || !"ROLE_PATIENT".equals(row.get("role_name"))) throw new AccessDeniedException("Patient account required");
        return (UUID)row.get("patient_id");
    }
    private PatientOnboardingException conflict() {
        return new PatientOnboardingException(409,"Hồ sơ đang khởi tạo hoặc liên kết đã thay đổi. Vui lòng tải lại tiến trình.");
    }
}
