package io.github.tandapnoelbansikah.coffeeshop.configuration;

import io.github.tandapnoelbansikah.coffeeshop.service.orders.PaymentCheckoutClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

/** Configures the backend's dedicated Keycloak workload identity for Payments calls. */
@Configuration
@ConditionalOnProperty(name = "app.payment.enabled", havingValue = "true")
public class PaymentClientConfiguration {
    @Bean
    ClientRegistrationRepository paymentClientRegistration(
            @Value("${app.payment.token-uri}") String tokenUri,
            @Value("${app.payment.client-id}") String clientId,
            @Value("${app.payment.client-secret}") String clientSecret) {
        if (clientSecret.isBlank()) throw new IllegalStateException("Payment service client secret is required when payments are enabled");
        var registration = ClientRegistration.withRegistrationId("payment-service")
                .clientId(clientId).clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(tokenUri).build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
        OAuth2AuthorizedClientManager paymentAuthorizedClientManager(
            @Qualifier("paymentClientRegistration") ClientRegistrationRepository registrations) {
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, service);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    PaymentCheckoutClient paymentCheckoutClient(OAuth2AuthorizedClientManager manager,
            @Value("${app.payment.base-url}") String baseUrl) {
        return new PaymentCheckoutClient(manager, RestClient.builder().baseUrl(baseUrl).build());
    }
}