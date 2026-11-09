package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.model.IntrospectionPolicy;
import com.fclinic.authservice.application.model.IntrospectionResult;
import com.fclinic.authservice.application.model.KeycloakTokenClaims;
import com.fclinic.authservice.application.model.TokenValidationResult;
import com.fclinic.authservice.application.port.in.IntrospectTokenUseCase;
import com.fclinic.authservice.application.port.in.ValidateTokenUseCase;
import com.fclinic.authservice.application.port.out.KeycloakIdentityPort;
import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.util.UuidV4;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class TokenVerificationService implements ValidateTokenUseCase, IntrospectTokenUseCase {

    private final KeycloakIdentityPort keycloakIdentityPort;
    private final UserRepositoryPort userRepository;
    private final IntrospectionPolicy policy;
    private final Clock clock;

    public TokenVerificationService(
            KeycloakIdentityPort keycloakIdentityPort,
            UserRepositoryPort userRepository,
            IntrospectionPolicy policy,
            Clock clock
    ) {
        this.keycloakIdentityPort = keycloakIdentityPort;
        this.userRepository = userRepository;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TokenValidationResult validate(String rawToken) {
        String token = stripBearerPrefix(rawToken);
        if (token == null) {
            return TokenValidationResult.invalid();
        }
        KeycloakTokenClaims claims = keycloakIdentityPort.introspectToken(token);
        if (!isTokenActiveAndUnexpired(claims)) {
            return TokenValidationResult.invalid();
        }

        if (claims.serviceAccount()) {
            return TokenValidationResult.invalid();
        }

        Optional<User> userOpt = resolveUser(claims);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return TokenValidationResult.invalid();
        }
        User user = userOpt.get();
        return new TokenValidationResult(
                true,
                user.getId(),
                user.getKeycloakId(),
                user.getPatientId(),
                user.getDoctorId(),
                user.getUsername(),
                user.getRoleName().name(),
                user.getEmail()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public IntrospectionResult introspect(String rawToken) {
        String token = stripBearerPrefix(rawToken);
        if (token == null) {
            return IntrospectionResult.inactive();
        }

        KeycloakTokenClaims claims = keycloakIdentityPort.introspectToken(token);
        if (!isTokenActiveAndUnexpired(claims) || claims.subject() == null || claims.subject().isBlank()) {
            return IntrospectionResult.inactive();
        }

        String issuer = resolveVerifiedIssuer(claims);
        if (issuer == null) {
            return IntrospectionResult.inactive();
        }

        List<String> audiences = normalizeAudiences(claims.audiences());
        if (audiences.isEmpty()) {
            return IntrospectionResult.inactive();
        }

        if (claims.serviceAccount()) {
            List<String> serviceAuthorities = claims.authorities().stream()
                    .filter(a -> a.matches("SVC_[A-Z0-9_]{1,80}"))
                    .sorted()
                    .toList();
            if (serviceAuthorities.isEmpty()) {
                return IntrospectionResult.inactive();
            }
            return IntrospectionResult.activeService(
                    issuer,
                    audiences,
                    claims.expiresAtEpochSecond(),
                    claims.subject(),
                    serviceAuthorities
            );
        }

        Optional<User> userOpt = resolveUser(claims);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return IntrospectionResult.inactive();
        }
        User user = userOpt.get();
        if (!UuidV4.isUuidV4(user.getId())) {
            return IntrospectionResult.inactive();
        }

        return IntrospectionResult.activeUser(
                issuer,
                audiences,
                claims.expiresAtEpochSecond(),
                claims.subject(),
                user.getId(),
                user.getPatientId(),
                user.getDoctorId(),
                List.of(user.getRoleName().name())
        );
    }

    private boolean isTokenActiveAndUnexpired(KeycloakTokenClaims claims) {
        if (claims == null || !claims.active()) {
            return false;
        }
        long nowEpoch = clock.instant().getEpochSecond();
        return claims.expiresAtEpochSecond() > nowEpoch;
    }

    private String resolveVerifiedIssuer(KeycloakTokenClaims claims) {
        String configuredIssuer = policy.issuer();
        if (claims.issuer() == null || claims.issuer().isBlank()) {
            return configuredIssuer;
        }
        if (configuredIssuer == null || configuredIssuer.isBlank()) {
            return claims.issuer();
        }
        if (trimTrailingSlash(claims.issuer()).equals(trimTrailingSlash(configuredIssuer))) {
            return configuredIssuer;
        }
        return null;
    }

    private List<String> normalizeAudiences(List<String> claimAudiences) {
        Set<String> merged = new LinkedHashSet<>();
        if (claimAudiences != null) {
            claimAudiences.stream().filter(a -> a != null && !a.isBlank()).forEach(merged::add);
        }
        return List.copyOf(merged);
    }

    private Optional<User> resolveUser(KeycloakTokenClaims claims) {
        if (claims.keycloakUserId() == null || !claims.keycloakUserId().toString().equals(claims.subject())) return Optional.empty();
        // Current mapping comes only from the verified issuer/subject binding, never a username fallback.
        return userRepository.findByKeycloakId(claims.keycloakUserId());
    }

    private static String stripBearerPrefix(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String trimmed = rawToken.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            trimmed = trimmed.substring(7).trim();
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

