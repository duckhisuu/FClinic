import com.fclinic.authservice.application.model.*;
import com.fclinic.authservice.application.command.UpdateUserCommand;
import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.application.service.*;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.model.PasswordResetOtp;
import com.fclinic.authservice.domain.valueobject.*;
import com.fclinic.authservice.infrastructure.config.KeycloakJwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;

/** Isolated audit reproductions: mocks at ports, production services/converter, no real accounts. */
class ReviewAuthLogic {
  static final Instant NOW = Instant.parse("2026-11-10T01:00:00Z");
  static User user(RoleName role) {
    String label = "synthetic_" + UUID.randomUUID().toString().substring(0, 8);
    return User.create(UUID.randomUUID(), UUID.randomUUID(), label, label + "@example.test",
        null, "Synthetic", new Role(UUID.randomUUID(), role, "audit", NOW, NOW), null, null, null);
  }
  static void check(boolean condition, String name) {
    if (!condition) throw new AssertionError("Not reproduced: " + name);
    System.out.println("REPRODUCED " + name);
  }
  public static void main(String[] args) throws Exception {
    var users = mock(UserRepositoryPort.class);
    var identities = mock(KeycloakIdentityPort.class);
    var roles = mock(RoleRepositoryPort.class);
    var victim = user(RoleName.ROLE_ADMIN);
    when(users.findByEmail(victim.getEmail())).thenReturn(Optional.of(victim));
    var support = new UserProvisioningSupport(users, roles, identities);
    var stranger = UUID.randomUUID();
    var jwt = Jwt.withTokenValue("synthetic-only").header("alg", "RS256")
        .subject(stranger.toString()).issuer("https://synthetic.example.test/realms/audit")
        .claim("email", victim.getEmail()).issuedAt(NOW).expiresAt(NOW.plusSeconds(300)).build();
    var authentication = new KeycloakJwtAuthenticationConverter(support).convert(jwt);
    check(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        && !stranger.equals(victim.getKeycloakId()), "different_subject_same_email_receives_existing_admin_role");

    var otpRepo = mock(PasswordResetOtpRepositoryPort.class);
    var delivery = mock(OtpDeliveryPort.class);
    var patient = user(RoleName.ROLE_PATIENT);
    when(users.findByEmail(patient.getEmail())).thenReturn(Optional.of(patient));
    var recovery = new PasswordRecoveryService(users, otpRepo, delivery, identities,
        new PasswordRecoveryPolicy(300, false), Clock.fixed(NOW, ZoneOffset.UTC));
    var otpId = UUID.randomUUID();
    // Model READ_COMMITTED: each transaction reads its own unused snapshot of the same row.
    var barrier = new CyclicBarrier(2);
    when(otpRepo.findLatestByEmail(patient.getEmail())).thenAnswer(call -> {
      var snapshot = new PasswordResetOtp(otpId, patient.getId(), patient.getEmail(), "123456",
          NOW.plusSeconds(300), false, NOW, NOW);
      barrier.await(5, TimeUnit.SECONDS);
      return Optional.of(snapshot);
    });
    clearInvocations(identities);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> recovery.resetPassword(patient.getEmail(), "123456", "Synthetic-A-123!"));
      var second = executor.submit(() -> recovery.resetPassword(patient.getEmail(), "123456", "Synthetic-B-123!"));
      first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
    }
    verify(identities, times(2)).resetPassword(eq(patient.getKeycloakId()), anyString());
    check(true, "same_otp_two_concurrent_password_resets");

    var firstPatient = user(RoleName.ROLE_PATIENT);
    var secondPatient = user(RoleName.ROLE_PATIENT);
    when(users.findById(firstPatient.getId())).thenReturn(Optional.of(firstPatient));
    when(users.findById(secondPatient.getId())).thenReturn(Optional.of(secondPatient));
    when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    var admin = new AdminUserService(users, roles, identities, support);
    var profileId = UUID.randomUUID();
    var link = new UpdateUserCommand(null, null, null, null, null, profileId, null);
    admin.updateUser(firstPatient.getId(), link);
    admin.updateUser(secondPatient.getId(), link);
    check(firstPatient.getPatientId().equals(secondPatient.getPatientId()),
        "admin_can_link_two_accounts_to_same_patient_without_uniqueness_check");
  }
}
