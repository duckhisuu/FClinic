package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.model.KeycloakProvisionCommand;
import com.fclinic.authservice.application.model.KeycloakTokenClaims;
import com.fclinic.authservice.application.model.KeycloakTokenPair;
import com.fclinic.authservice.application.port.out.KeycloakIdentityPort;
import com.fclinic.authservice.domain.exception.AccountDisabledException;
import com.fclinic.authservice.domain.exception.IdentityProviderException;
import com.fclinic.authservice.domain.exception.InvalidCredentialsException;
import com.fclinic.authservice.domain.exception.InvalidTokenException;
import com.fclinic.authservice.domain.exception.UserAlreadyExistsException;
import com.fclinic.authservice.domain.util.UuidV4;
import com.fclinic.authservice.domain.valueobject.RoleName;
import com.fclinic.authservice.infrastructure.config.KeycloakProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class KeycloakRestAdapter implements KeycloakIdentityPort {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRestAdapter.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE = new ParameterizedTypeReference<>() {
    };

    private final KeycloakProperties properties;
    private final RestClient restClient;

    private volatile CachedToken cachedAdminToken;
    private final Object adminTokenLock = new Object();

    public KeycloakRestAdapter(KeycloakProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeoutMs() > 0 ? properties.connectTimeoutMs() : 10000);
        factory.setReadTimeout(properties.readTimeoutMs() > 0 ? properties.readTimeoutMs() : 60000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public UUID createUser(KeycloakProvisionCommand command) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fclinic_user_id", List.of(command.fclinicUserId().toString()));
        if (command.phoneNumber() != null && !command.phoneNumber().isBlank()) {
            attributes.put("phone_number", List.of(command.phoneNumber()));
        }
        if (command.patientId() != null) {
            attributes.put("patient_id", List.of(command.patientId().toString()));
        }
        if (command.doctorId() != null) {
            attributes.put("doctor_id", List.of(command.doctorId().toString()));
        }

        NameParts names = splitFullName(command.fullName());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", command.username());
        payload.put("email", command.email());
        payload.put("firstName", names.firstName());
        payload.put("lastName", names.lastName());
        payload.put("enabled", command.enabled());
        payload.put("emailVerified", true);
        payload.put("attributes", attributes);
        payload.put("credentials", List.of(Map.of(
                "type", "password",
                "value", command.password(),
                "temporary", false
        )));

        UUID createdKeycloakId;
        try {
            ResponseEntity<Void> response = executeAdminRequest(token -> restClient.post()
                    .uri(properties.adminRealmUsersUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity());

            URI location = response.getHeaders().getLocation();
            createdKeycloakId = extractUuidFromLocation(location)
                    .or(() -> findUserIdByUsernameOrEmail(command.username()))
                    .orElseThrow(() -> new IdentityProviderException("Keycloak không trả về userId sau khi tạo tài khoản"));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                throw new UserAlreadyExistsException("Người dùng đã tồn tại trên Keycloak: " + command.username());
            }
            throw new IdentityProviderException("Không thể tạo người dùng trên Keycloak (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak Identity Provider", ex);
        }

        try {
            replaceRealmRole(createdKeycloakId, command.roleName());
            return createdKeycloakId;
        } catch (RuntimeException ex) {
            try {
                deleteUser(createdKeycloakId);
            } catch (RuntimeException deleteEx) {
                log.error("[Keycloak] Failed cleanup delete for keycloakId={}", createdKeycloakId, deleteEx);
            }
            throw ex;
        }
    }

    @Override
    public void deleteUser(UUID keycloakUserId) {
        try {
            executeAdminRequest(token -> restClient.delete()
                    .uri(properties.adminRealmUsersUrl() + "/{id}", keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) {
                throw new IdentityProviderException("Không thể xóa người dùng trên Keycloak: " + keycloakUserId, ex);
            }
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak Identity Provider", ex);
        }
    }

    @Override
    public Optional<UUID> findUserIdByUsernameOrEmail(String identifier) {
        try {
            List<Map<String, Object>> users = executeAdminRequest(token -> restClient.get()
                    .uri(properties.adminRealmUsersUrl() + "?username={q}&exact=true", identifier)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(LIST_OF_MAPS_TYPE));
            if (users != null && !users.isEmpty()) {
                return parseUuidNullable(stringVal(users.get(0).get("id")));
            }
            List<Map<String, Object>> byEmail = executeAdminRequest(token -> restClient.get()
                    .uri(properties.adminRealmUsersUrl() + "?email={q}&exact=true", identifier)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(LIST_OF_MAPS_TYPE));
            if (byEmail != null && !byEmail.isEmpty()) {
                return parseUuidNullable(stringVal(byEmail.get(0).get("id")));
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể tra cứu người dùng trên Keycloak", ex);
        }
    }

    @Override
    public KeycloakTokenPair loginWithPassword(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.clientId());
        if (hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid profile email");

        return postTokenForm(form, false);
    }

    @Override
    public KeycloakTokenPair exchangeAuthorizationCode(String code, String redirectUri, String codeVerifier, String clientId) {
        String effectiveClientId = hasText(clientId) ? clientId : properties.clientId();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", effectiveClientId);
        if (effectiveClientId.equals(properties.clientId()) && hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        if (hasText(codeVerifier)) {
            form.add("code_verifier", codeVerifier);
        }
        return postTokenForm(form, true);
    }

    @Override
    public KeycloakTokenPair refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", properties.clientId());
        if (hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        form.add("refresh_token", refreshToken);

        return postTokenForm(form, true);
    }

    @Override
    public void logoutSession(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        if (hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        form.add("refresh_token", refreshToken);

        try {
            restClient.post()
                    .uri(properties.logoutEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401) {
                throw new InvalidTokenException("Refresh token không hợp lệ hoặc đã hết hạn khi đăng xuất");
            }
            throw new IdentityProviderException("Lỗi khi đăng xuất phiên Keycloak", ex);
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak để đăng xuất", ex);
        }
    }

    @Override
    public void revokeAllUserSessions(UUID keycloakUserId) {
        try {
            executeAdminRequest(token -> restClient.post()
                    .uri(properties.adminRealmUsersUrl() + "/{id}/logout", keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) {
                throw new IdentityProviderException("Không thể thu hồi phiên người dùng trên Keycloak", ex);
            }
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak để thu hồi phiên", ex);
        }
    }

    @Override
    public KeycloakTokenClaims introspectToken(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", accessToken);
        form.add("token_type_hint", "access_token");

        try {
            Map<String, Object> body = restClient.post()
                    .uri(properties.introspectionEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, basicHeader(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);

            if (body == null || !Boolean.TRUE.equals(body.get("active"))) {
                return KeycloakTokenClaims.inactive();
            }

            String sub = stringVal(body.get("sub"));
            String username = stringVal(body.get("preferred_username"));
            if (!hasText(username)) {
                username = stringVal(body.get("username"));
            }
            String email = stringVal(body.get("email"));
            String fullName = stringVal(body.get("name"));
            String issuer = stringVal(body.get("iss"));
            List<String> audiences = extractAudiences(body.get("aud"));
            long exp = longVal(body.get("exp"));
            String azp = stringVal(body.get("azp"));
            if (!hasText(azp)) {
                azp = stringVal(body.get("client_id"));
            }

            Set<String> authorities = extractAuthorities(body);
            UUID keycloakUserId = parseUuidNullable(sub).orElse(null);
            UUID fclinicUserId = parseUuidNullable(stringVal(body.get("fclinic_user_id"))).orElse(null);
            UUID patientId = parseUuidNullable(stringVal(body.get("patient_id"))).orElse(null);
            UUID doctorId = parseUuidNullable(stringVal(body.get("doctor_id"))).orElse(null);

            boolean serviceAccount = (hasText(username) && username.startsWith("service-account-"))
                    || (authorities.stream().anyMatch(a -> a.startsWith("SVC_"))
                    && authorities.stream().noneMatch(a -> a.startsWith("ROLE_")));

            return new KeycloakTokenClaims(
                    true,
                    sub,
                    keycloakUserId,
                    username,
                    email,
                    fullName,
                    issuer,
                    audiences,
                    exp,
                    azp,
                    authorities,
                    fclinicUserId,
                    patientId,
                    doctorId,
                    serviceAccount
            );
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401) {
                return KeycloakTokenClaims.inactive();
            }
            throw new IdentityProviderException("Keycloak Token Introspection thất bại", ex);
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak Introspection Endpoint", ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void updateUserProfile(
            UUID keycloakUserId,
            String email,
            String fullName,
            String phoneNumber,
            UUID patientId,
            UUID doctorId
    ) {
        try {
            Map<String, Object> existing = getAdminUserRepresentation(keycloakUserId);
            Map<String, Object> updated = new LinkedHashMap<>(existing);

            if (hasText(email)) {
                updated.put("email", email);
                updated.put("emailVerified", true);
            }
            if (hasText(fullName)) {
                NameParts names = splitFullName(fullName);
                updated.put("firstName", names.firstName());
                updated.put("lastName", names.lastName());
            }

            Map<String, Object> attributes = new LinkedHashMap<>();
            Object rawAttrs = existing.get("attributes");
            if (rawAttrs instanceof Map<?, ?> map) {
                map.forEach((k, v) -> attributes.put(String.valueOf(k), v));
            }
            if (phoneNumber != null) {
                if (phoneNumber.isBlank()) {
                    attributes.remove("phone_number");
                } else {
                    attributes.put("phone_number", List.of(phoneNumber));
                }
            }
            if (patientId != null) {
                attributes.put("patient_id", List.of(patientId.toString()));
            }
            if (doctorId != null) {
                attributes.put("doctor_id", List.of(doctorId.toString()));
            }
            updated.put("attributes", attributes);

            executeAdminRequest(token -> restClient.put()
                    .uri(properties.adminRealmUsersUrl() + "/{id}", keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updated)
                    .retrieve()
                    .toBodilessEntity());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                throw new UserAlreadyExistsException("Email đã được sử dụng trên Keycloak: " + email);
            }
            throw new IdentityProviderException("Không thể cập nhật hồ sơ người dùng trên Keycloak", ex);
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak Identity Provider", ex);
        }
    }

    @Override
    public void setUserEnabled(UUID keycloakUserId, boolean enabled) {
        try {
            Map<String, Object> existing = getAdminUserRepresentation(keycloakUserId);
            Map<String, Object> updated = new LinkedHashMap<>(existing);
            updated.put("enabled", enabled);

            executeAdminRequest(token -> restClient.put()
                    .uri(properties.adminRealmUsersUrl() + "/{id}", keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updated)
                    .retrieve()
                    .toBodilessEntity());
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể cập nhật trạng thái kích hoạt trên Keycloak", ex);
        }
    }

    @Override
    public void replaceRealmRole(UUID keycloakUserId, RoleName targetRole) {
        try {
            List<Map<String, Object>> currentRoles = executeAdminRequest(token -> restClient.get()
                    .uri(properties.adminRealmUsersUrl() + "/{id}/role-mappings/realm", keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(LIST_OF_MAPS_TYPE));

            if (currentRoles != null && !currentRoles.isEmpty()) {
                List<Map<String, Object>> fclinicRolesToRemove = currentRoles.stream()
                        .filter(roleMap -> {
                            String name = stringVal(roleMap.get("name"));
                            return name != null && name.startsWith("ROLE_");
                        })
                        .toList();
                if (!fclinicRolesToRemove.isEmpty()) {
                    executeAdminRequest(token -> restClient.method(HttpMethod.DELETE)
                            .uri(properties.adminRealmUsersUrl() + "/{id}/role-mappings/realm", keycloakUserId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(fclinicRolesToRemove)
                            .retrieve()
                            .toBodilessEntity());
                }
            }

            Map<String, Object> roleRepresentation = executeAdminRequest(token -> restClient.get()
                    .uri(properties.adminRealmRolesUrl() + "/{roleName}", targetRole.name())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(MAP_TYPE));

            if (roleRepresentation == null || roleRepresentation.isEmpty()) {
                throw new IdentityProviderException("Không tìm thấy realm role trên Keycloak: " + targetRole.name());
            }

            executeAdminRequest(token -> restClient.post()
                    .uri(properties.adminRealmUsersUrl() + "/{id}/role-mappings/realm", keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(roleRepresentation))
                    .retrieve()
                    .toBodilessEntity());
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể đồng bộ quyền lên Keycloak: " + targetRole.name(), ex);
        }
    }

    @Override
    public void resetPassword(UUID keycloakUserId, String newPassword) {
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false
        );
        try {
            executeAdminRequest(token -> restClient.put()
                    .uri(properties.adminRealmUsersUrl() + "/{id}/reset-password", keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(credential)
                    .retrieve()
                    .toBodilessEntity());
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể đặt lại mật khẩu trên Keycloak", ex);
        }
    }

    @Override
    public void linkFederatedGoogleIdentity(UUID keycloakUserId, String googleUserId, String googleUsername) {
        Map<String, Object> linkPayload = Map.of(
                "identityProvider", "google",
                "userId", googleUserId,
                "userName", googleUsername
        );
        try {
            executeAdminRequest(token -> restClient.post()
                    .uri(properties.adminRealmUsersUrl() + "/{id}/federated-identity/google", keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(linkPayload)
                    .retrieve()
                    .toBodilessEntity());
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            if (code == 409) {
                log.debug("[Keycloak] Google federated identity already linked for keycloakId={}", keycloakUserId);
            } else if (code == 400 || code == 404) {
                log.warn("[Keycloak] Google IdP link skipped (status={}): verify 'google' broker config in Keycloak realm", code);
            } else {
                throw new IdentityProviderException("Không thể liên kết Google Identity trên Keycloak", ex);
            }
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak Identity Provider", ex);
        }
    }

    @Override
    public KeycloakTokenPair issueTokenForFederatedUser(UUID keycloakUserId, String username) {
        try {
            KeycloakTokenPair serviceToken = fetchClientCredentialsToken();
            MultiValueMap<String, String> exchangeForm = new LinkedMultiValueMap<>();
            exchangeForm.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
            exchangeForm.add("client_id", properties.clientId());
            if (hasText(properties.clientSecret())) {
                exchangeForm.add("client_secret", properties.clientSecret());
            }
            exchangeForm.add("subject_token", serviceToken.accessToken());
            exchangeForm.add("requested_subject", keycloakUserId.toString());
            exchangeForm.add("scope", "openid profile email");

            return postTokenForm(exchangeForm, false);
        } catch (RuntimeException ex) {
            log.debug("[Keycloak] Standard token-exchange unavailable ({}); issuing federated session token via rotated password grant", ex.getMessage());
            String ephemeralSecret = generateEphemeralPassword();
            resetPassword(keycloakUserId, ephemeralSecret);
            return loginWithPassword(username, ephemeralSecret);
        }
    }

    private Map<String, Object> getAdminUserRepresentation(UUID keycloakUserId) {
        Map<String, Object> body = executeAdminRequest(token -> restClient.get()
                .uri(properties.adminRealmUsersUrl() + "/{id}", keycloakUserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(MAP_TYPE));
        if (body == null) {
            throw new IdentityProviderException("Không đọc được thông tin người dùng từ Keycloak: " + keycloakUserId);
        }
        return body;
    }

    private KeycloakTokenPair postTokenForm(MultiValueMap<String, String> form, boolean refreshOrCodeFlow) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(properties.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);

            if (body == null || !hasText(stringVal(body.get("access_token")))) {
                throw new IdentityProviderException("Keycloak không trả về access_token hợp lệ");
            }
            return toTokenPair(body);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String responseBody = ex.getResponseBodyAsString();
            if (status == 400 || status == 401) {
                if (responseBody != null && responseBody.toLowerCase().contains("disabled")) {
                    throw new AccountDisabledException("Tài khoản đã bị vô hiệu hóa trên Keycloak");
                }
                if (refreshOrCodeFlow) {
                    throw new InvalidTokenException("Token hoặc mã xác thực không hợp lệ hoặc đã hết hạn");
                }
                throw new InvalidCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác");
            }
            throw new IdentityProviderException("Lỗi xác thực từ Keycloak (HTTP " + status + ")", ex);
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak Identity Provider", ex);
        }
    }

    private <T> T executeAdminRequest(AdminOperation<T> operation) {
        String token = getAdminAccessToken(false);
        try {
            return operation.execute(token);
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            if (code == 401) {
                invalidateAdminToken();
                return operation.execute(getAdminAccessToken(false));
            }
            if (code == 403) {
                invalidateAdminToken();
                return operation.execute(getAdminAccessToken(true));
            }
            throw ex;
        }
    }

    private String getAdminAccessToken(boolean forceMasterAdminFallback) {
        CachedToken current = this.cachedAdminToken;
        Instant now = Instant.now();
        if (!forceMasterAdminFallback && current != null && current.expiresAt().isAfter(now.plusSeconds(30))) {
            return current.token();
        }
        synchronized (adminTokenLock) {
            current = this.cachedAdminToken;
            if (!forceMasterAdminFallback && current != null && current.expiresAt().isAfter(now.plusSeconds(30))) {
                return current.token();
            }
            KeycloakTokenPair tokenPair;
            if (!forceMasterAdminFallback) {
                try {
                    tokenPair = fetchClientCredentialsToken();
                } catch (RestClientResponseException ex) {
                    log.warn("[Keycloak] Client credentials grant failed (HTTP {}), falling back to master admin grant",
                            ex.getStatusCode().value());
                    tokenPair = fetchMasterAdminToken();
                }
            } else {
                tokenPair = fetchMasterAdminToken();
            }
            long ttl = Math.max(60L, tokenPair.expiresIn());
            this.cachedAdminToken = new CachedToken(tokenPair.accessToken(), now.plusSeconds(ttl));
            return tokenPair.accessToken();
        }
    }

    private void invalidateAdminToken() {
        this.cachedAdminToken = null;
    }

    private KeycloakTokenPair fetchClientCredentialsToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        Map<String, Object> body;
        try {
            body = restClient.post()
                    .uri(properties.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientResponseException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak Token Endpoint", ex);
        }

        if (body == null || !hasText(stringVal(body.get("access_token")))) {
            throw new IdentityProviderException("Không lấy được service account token từ Keycloak");
        }
        return toTokenPair(body);
    }

    private KeycloakTokenPair fetchMasterAdminToken() {
        if (!hasText(properties.adminUsername()) || !hasText(properties.adminPassword())) {
            throw new IdentityProviderException("Thiếu cấu hình quyền quản trị Keycloak (service account hoặc master admin)");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", properties.adminUsername());
        form.add("password", properties.adminPassword());

        Map<String, Object> body;
        try {
            body = restClient.post()
                    .uri(properties.adminTokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientException ex) {
            throw new IdentityProviderException("Không thể kết nối tới Keycloak Admin Token Endpoint", ex);
        }

        if (body == null || !hasText(stringVal(body.get("access_token")))) {
            throw new IdentityProviderException("Không lấy được Keycloak master admin token");
        }
        return toTokenPair(body);
    }

    private static KeycloakTokenPair toTokenPair(Map<String, Object> body) {
        return new KeycloakTokenPair(
                stringVal(body.get("access_token")),
                stringVal(body.get("refresh_token")),
                stringVal(body.get("id_token")),
                stringVal(body.get("token_type")),
                longVal(body.get("expires_in")),
                longVal(body.get("refresh_expires_in")),
                stringVal(body.get("scope"))
        );
    }

    private static Set<String> extractAuthorities(Map<String, Object> body) {
        Set<String> result = new LinkedHashSet<>();
        Object realmAccess = body.get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof Collection<?> list) {
                list.forEach(item -> addNonBlank(result, item));
            }
        }
        Object directAuthorities = body.get("authorities");
        if (directAuthorities instanceof Collection<?> list) {
            list.forEach(item -> addNonBlank(result, item));
        }
        return Set.copyOf(result);
    }

    private static List<String> extractAudiences(Object rawAud) {
        List<String> result = new ArrayList<>();
        if (rawAud instanceof String s && hasText(s)) {
            result.add(s.trim());
        } else if (rawAud instanceof Collection<?> list) {
            for (Object item : list) {
                String s = stringVal(item);
                if (hasText(s)) {
                    result.add(s.trim());
                }
            }
        }
        return List.copyOf(result);
    }

    private static void addNonBlank(Set<String> target, Object item) {
        String value = stringVal(item);
        if (hasText(value)) {
            target.add(value.trim());
        }
    }

    private static Optional<UUID> extractUuidFromLocation(URI location) {
        if (location == null) {
            return Optional.empty();
        }
        String path = location.getPath();
        if (!hasText(path)) {
            return Optional.empty();
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return Optional.empty();
        }
        return parseUuidNullable(path.substring(lastSlash + 1));
    }

    private static Optional<UUID> parseUuidNullable(String raw) {
        if (!hasText(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UuidV4.parse(raw, "uuid"));
        } catch (RuntimeException ex) {
            try {
                return Optional.of(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
    }

    private static String basicHeader(String clientId, String clientSecret) {
        String raw = (clientId == null ? "" : clientId) + ":" + (clientSecret == null ? "" : clientSecret);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static NameParts splitFullName(String fullName) {
        if (!hasText(fullName)) {
            return new NameParts("User", "FClinic");
        }
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace < 0) {
            return new NameParts(trimmed, trimmed);
        }
        return new NameParts(trimmed.substring(0, firstSpace), trimmed.substring(firstSpace + 1));
    }

    private static String generateEphemeralPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "Fc!Fed#" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && hasText(s)) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CachedToken(String token, Instant expiresAt) {
    }

    private record NameParts(String firstName, String lastName) {
    }

    @FunctionalInterface
    private interface AdminOperation<T> {
        T execute(String adminAccessToken);
    }
}

