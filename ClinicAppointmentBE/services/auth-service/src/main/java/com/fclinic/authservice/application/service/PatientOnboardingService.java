package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.model.*;
import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.RoleName;
import com.fclinic.authservice.domain.exception.PatientOnboardingException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class PatientOnboardingService {
    private final UserRepositoryPort users;
    private final KeycloakIdentityPort keycloak;
    private final PatientOnboardingPort store;
    private final PatientProvisioningPort patients;
    private final JsonMapper json=JsonMapper.builder().build();
    public PatientOnboardingService(UserRepositoryPort users, KeycloakIdentityPort keycloak,
            PatientOnboardingPort store, PatientProvisioningPort patients) {
        this.users=users; this.keycloak=keycloak; this.store=store; this.patients=patients;
    }
    public User owner(AuthenticatedCaller caller) {
        if (caller==null) throw new AccessDeniedException("Patient account required");
        var user=users.findById(caller.userId()).orElseThrow(()->new AccessDeniedException("Account unavailable"));
        if (!user.isActive() || user.getRoleName()!=RoleName.ROLE_PATIENT || !user.getKeycloakId().equals(caller.keycloakId()))
            throw new AccessDeniedException("Patient account required");
        return user;
    }
    public Optional<Map<String,Object>> pending(AuthenticatedCaller caller) {
        var user=owner(caller);
        return store.find(user.getId()).map(r -> json.readValue(r.commandBody(), Map.class));
    }
    public User onboard(AuthenticatedCaller caller, PatientOnboardingCommand command) {
        User user=owner(caller);
        if (user.getPatientId()!=null && store.find(user.getId()).isEmpty()) return user;
        String body=json.writeValueAsString(command.profile(user.getEmail()));
        var inputs=new TreeMap<>(command.profile(null)); inputs.remove("email");
        var r=store.reserve(user.getId(), user.getKeycloakId(), json.writeValueAsString(inputs), body, hash(body));
        patients.ensureProfile(r);
        // Keycloak update is outside the DB transaction. Retrying the same reservation repairs partial failures.
        user=owner(caller);
        if (user.getPatientId()!=null && !user.getPatientId().equals(r.patientId()))
            throw new PatientOnboardingException(409,"Liên kết hồ sơ đã thay đổi. Vui lòng tải lại tài khoản.");
        try {
            keycloak.updateUserProfile(user.getKeycloakId(), user.getEmail(), user.getFullName(),
                user.getPhoneNumber(), r.patientId(), user.getDoctorId());
        } catch (RuntimeException ex) {
            throw new PatientOnboardingException(503,"Hồ sơ đã được lưu. Vui lòng thử lại để hoàn tất liên kết tài khoản.");
        }
        store.bind(r);
        return owner(caller);
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
