package com.fclinic.authservice.infrastructure.config;

import com.fclinic.authservice.application.model.AuthenticatedCaller;
import com.fclinic.authservice.application.model.KeycloakTokenClaims;
import com.fclinic.authservice.application.service.UserProvisioningSupport;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.exception.AccountDisabledException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserProvisioningSupport provisioningSupport;

    public KeycloakJwtAuthenticationConverter(UserProvisioningSupport provisioningSupport) {
        this.provisioningSupport = provisioningSupport;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        KeycloakTokenClaims claims = extractClaims(jwt);
        User user;
        try {
            user = provisioningSupport.resolveOrSyncUserFromClaims(claims);
        } catch (AccountDisabledException ex) {
            throw new DisabledException(ex.getMessage(), ex);
        } catch (com.fclinic.authservice.domain.exception.InvalidTokenException ex) {
            throw new org.springframework.security.authentication.BadCredentialsException(ex.getMessage(), ex);
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority(user.getRoleName().name()));

        AuthenticatedCaller principal = new AuthenticatedCaller(
                user.getId(),
                user.getKeycloakId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoleName(),
                user.getPatientId(),
                user.getDoctorId()
        );
        return new UsernamePasswordAuthenticationToken(principal, jwt.getTokenValue(), authorities);
    }

    private KeycloakTokenClaims extractClaims(Jwt jwt) {
        String sub = jwt.getSubject();
        UUID keycloakUserId = parseUuid(sub);
        UUID fclinicUserId = parseUuid(jwt.getClaimAsString("fclinic_user_id"));
        UUID patientId = parseUuid(jwt.getClaimAsString("patient_id"));
        UUID doctorId = parseUuid(jwt.getClaimAsString("doctor_id"));

        Set<String> roleSet = new LinkedHashSet<>();
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            roles.forEach(r -> {
                if (r != null && !r.toString().isBlank()) {
                    roleSet.add(r.toString().trim());
                }
            });
        }
        List<String> authorityClaim = jwt.getClaimAsStringList("authorities");
        if (authorityClaim != null) {
            authorityClaim.stream().filter(a -> a != null && !a.isBlank()).forEach(roleSet::add);
        }

        Instant exp = jwt.getExpiresAt();
        return new KeycloakTokenClaims(
                true,
                sub,
                keycloakUserId,
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                jwt.getIssuer() != null ? jwt.getIssuer().toString() : null,
                jwt.getAudience() != null ? new ArrayList<>(jwt.getAudience()) : List.of(),
                exp != null ? exp.getEpochSecond() : 0L,
                jwt.getClaimAsString("azp"),
                Set.copyOf(roleSet),
                fclinicUserId,
                patientId,
                doctorId,
                false
        );
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

