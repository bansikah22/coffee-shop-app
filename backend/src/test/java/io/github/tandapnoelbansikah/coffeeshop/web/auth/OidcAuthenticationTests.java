package io.github.tandapnoelbansikah.coffeeshop.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.tandapnoelbansikah.coffeeshop.service.auth.OidcAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.keycloak.enabled=true",
		"app.public-url=https://coffee.example.com",
		"app.keycloak.issuer=https://identity.example.com/realms/coffee-shop",
		"app.keycloak.client-secret=test-only-client-secret"
})
@AutoConfigureMockMvc
class OidcAuthenticationTests {
	@Autowired
	private MockMvc mvc;
	@Autowired
	private OidcAccountService accounts;

	@Test
	void advertisesProviderAndStartsAuthorizationCodeWithPkce() throws Exception {
		mvc.perform(get("/api/v1/auth/providers")).andExpect(status().isOk())
				.andExpect(jsonPath("$.keycloak").value(true));
		var result = mvc.perform(get("/api/oauth2/authorization/keycloak"))
				.andExpect(status().is3xxRedirection()).andReturn();
		assertThat(result.getResponse().getRedirectedUrl())
				.startsWith("https://identity.example.com/realms/coffee-shop/protocol/openid-connect/auth?")
				.contains("response_type=code", "code_challenge_method=S256", "code_challenge=", "state=", "nonce=",
						"redirect_uri=https://coffee.example.com/api/login/oauth2/code/keycloak");
	}

	@Test
	void refusesCallbackWithoutMatchingAuthorizationRequest() throws Exception {
		mvc.perform(get("/api/login/oauth2/code/keycloak").param("code", "forged").param("state", "invalid"))
				.andExpect(redirectedUrl("https://coffee.example.com/login?error=sso"));
		mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void keepsOidcPrincipalCompatibleWithAccountAndRoleEndpoints() throws Exception {
		var customer = token(List.of("CUSTOMER"));
		mvc.perform(get("/api/v1/auth/me").with(authentication(customer)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.authProvider").value("keycloak"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"));
		mvc.perform(get("/api/v1/orders").with(authentication(customer))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/admin/orders").with(authentication(customer))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/orders").with(authentication(token(List.of("ADMIN")))))
				.andExpect(status().isOk());
	}

	@Test
	void requiresCsrfAndUsesProviderLogoutWithExactReturnUrl() throws Exception {
		var customer = token(List.of("CUSTOMER"));
		mvc.perform(post("/api/v1/auth/oidc-logout").with(authentication(customer)))
				.andExpect(status().isForbidden());
		var response = mvc.perform(post("/api/v1/auth/oidc-logout").with(authentication(customer)).with(csrf()))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse();
		assertThat(response.getRedirectedUrl())
				.startsWith("https://identity.example.com/realms/coffee-shop/protocol/openid-connect/logout?")
				.contains("id_token_hint=", "post_logout_redirect_uri=https://coffee.example.com/");
	}

	private OAuth2AuthenticationToken token(List<String> roles) {
		String subject = UUID.randomUUID().toString();
		var idToken = new OidcIdToken("test-token", Instant.now(), Instant.now().plusSeconds(300),
				Map.of("iss", "https://identity.example.com/realms/coffee-shop", "sub", subject,
						"email", subject + "@example.com", "email_verified", true, "coffee_shop_roles", roles));
		var identity = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
		var account = accounts.synchronize(identity);
		return new OAuth2AuthenticationToken(account, account.getAuthorities(), "keycloak");
	}
}