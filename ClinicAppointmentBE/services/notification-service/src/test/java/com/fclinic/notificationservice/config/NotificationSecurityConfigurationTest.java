package com.fclinic.notificationservice.config;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class NotificationSecurityConfigurationTest {
    @Test
    void validatesSignatureIssuerExpiryAudienceAndCanonicalUserIdentity() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).privateKey(keyPair.getPrivate()).keyID("test").build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("https://issuer.test"),
                NotificationSecurityConfiguration.identityValidator("notification-service")));
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("https://issuer.test").subject(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).audience(List.of("notification-service"))
                .claim("fclinic_user_id", UUID.randomUUID().toString()).claim("authorities", List.of("ROLE_PATIENT")).build();
        String valid = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        assertThat(decoder.decode(valid).getAudience()).contains("notification-service");
        for (var bad : List.of(
                JwtClaimsSet.from(claims).issuer("https://other.test").build(),
                JwtClaimsSet.from(claims).audience(List.of("auth-service")).build(),
                JwtClaimsSet.from(claims).claim("fclinic_user_id", "1").build(),
                JwtClaimsSet.from(claims).claims(c -> c.remove("fclinic_user_id")).build(),
                JwtClaimsSet.from(claims).claim("authorities", List.of("SVC_PATIENT_READ")).build(),
                JwtClaimsSet.from(claims).claim("fclinic_user_id", "33333333-3333-3333-3333-333333333333").build(),
                JwtClaimsSet.from(claims).expiresAt(now.minusSeconds(120)).issuedAt(now.minusSeconds(600)).build())) {
            String encoded = encoder.encode(JwtEncoderParameters.from(bad)).getTokenValue();
            assertThatThrownBy(() -> decoder.decode(encoded)).isInstanceOf(JwtException.class);
        }
        var otherKeys = generator.generateKeyPair();
        var wrongDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) otherKeys.getPublic()).build();
        assertThatThrownBy(() -> wrongDecoder.decode(valid)).isInstanceOf(JwtException.class);
    }
}
