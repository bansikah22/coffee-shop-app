package io.github.tandapnoelbansikah.payment.configuration;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/** Protects internal APIs with scoped JWTs; only the signed provider callback is anonymous. */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/internal/**", "/webhooks/stripe"))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/checkout/return", "/error").permitAll()
                    .requestMatchers("/webhooks/stripe").permitAll()
                        // The controller performs the exact azp/audience check after JWT authentication.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/internal/payments").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/internal/payments/{id}").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults())).build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt-secret}") String secret, @Value("${app.issuer-uri}") String issuer,
            @Value("${app.jwks-uri:}") String jwksUri) {
        NimbusJwtDecoder decoder;
        if (!issuer.isBlank()) {
            decoder = jwksUri.isBlank() ? JwtDecoders.fromIssuerLocation(issuer)
                    : NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        } else {
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("Set a random PAYMENT_JWT_SECRET of at least 32 bytes for the local demo");
            }
            decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
            issuer = "payment-poc";
        }
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience() != null && token.getAudience().contains("payment-service")
                && token.getSubject() != null && token.getSubject().matches("[A-Za-z0-9_-]{1,100}")
                && token.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid payment token claims", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience));
        return decoder;
    }
}