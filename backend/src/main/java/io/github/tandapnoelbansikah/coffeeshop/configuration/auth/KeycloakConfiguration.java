package io.github.tandapnoelbansikah.coffeeshop.configuration.auth;

import java.net.URI;
import java.util.Map;

import io.github.tandapnoelbansikah.coffeeshop.service.auth.OidcAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration
@ConditionalOnProperty(name = "app.keycloak.enabled", havingValue = "true")
public class KeycloakConfiguration {

	private final String applicationUrl;
	private final OidcAccountService accounts;

	public KeycloakConfiguration(@Value("${app.public-url}") String applicationUrl, OidcAccountService accounts) {
		this.applicationUrl = applicationUrl;
		this.accounts = accounts;
	}

	@Bean
	@Primary
	ClientRegistrationRepository clientRegistrationRepository(
			@Value("${app.keycloak.issuer}") String issuer,
			@Value("${app.keycloak.internal-issuer:${app.keycloak.issuer}}") String internalIssuer,
			@Value("${app.keycloak.client-id}") String clientId,
			@Value("${app.keycloak.client-secret}") String clientSecret, Environment environment) {
		boolean development = environment.acceptsProfiles(Profiles.of("dev"));
		for (String address : new String[] { applicationUrl, issuer, internalIssuer }) {
			URI uri = URI.create(address);
			if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null
					|| uri.getUserInfo() != null || !("https".equals(uri.getScheme())
						|| development && "http".equals(uri.getScheme()))) {
				throw new IllegalArgumentException("OIDC URLs must be absolute HTTPS URLs (HTTP is dev-only)");
			}
		}
		if (clientSecret.isBlank() || clientId.isBlank()) {
			throw new IllegalArgumentException("Keycloak requires a confidential client ID and secret");
		}
		String publicEndpoint = issuer + "/protocol/openid-connect";
		String internalEndpoint = internalIssuer + "/protocol/openid-connect";
		var registration = ClientRegistration.withRegistrationId("keycloak")
				.clientId(clientId).clientSecret(clientSecret)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri(applicationUrl + "/api/login/oauth2/code/keycloak")
				.scope("openid").clientName("Keycloak")
				.issuerUri(issuer).authorizationUri(publicEndpoint + "/auth")
				.tokenUri(internalEndpoint + "/token").jwkSetUri(internalEndpoint + "/certs")
				.userInfoUri(internalEndpoint + "/userinfo").userNameAttributeName("sub")
				.providerConfigurationMetadata(Map.of("end_session_endpoint", publicEndpoint + "/logout"))
				.build();
		return new InMemoryClientRegistrationRepository(registration);
	}

	public void configure(HttpSecurity http, ClientRegistrationRepository registrations) throws Exception {
		var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/api/oauth2/authorization");
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
		var delegate = new OidcUserService();
		var logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
		logout.setPostLogoutRedirectUri(applicationUrl + "/");
		http.oauth2Login(oauth -> oauth
				.loginPage(applicationUrl + "/login")
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
				.redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
				.authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
				.userInfoEndpoint(endpoint -> endpoint.oidcUserService(request -> accounts.synchronize(delegate.loadUser(request))))
				.successHandler((request, response, authentication) -> response.sendRedirect(applicationUrl
						+ (authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"))
								? "/admin" : "/")))
				.failureHandler((request, response, exception) -> response.sendRedirect(applicationUrl + "/login?error=sso")))
			.logout(configuration -> configuration.logoutUrl("/api/v1/auth/oidc-logout")
					.logoutSuccessHandler(logout).deleteCookies("JSESSIONID"));
	}
}