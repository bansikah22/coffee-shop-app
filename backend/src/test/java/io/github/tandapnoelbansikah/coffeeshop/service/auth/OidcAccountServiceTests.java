package io.github.tandapnoelbansikah.coffeeshop.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.tandapnoelbansikah.coffeeshop.domain.AccountRole;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OidcAccountServiceTests {
	@Autowired
	private OidcAccountService service;
	@Autowired
	private UserAccountRepository accounts;
	@Autowired
	private UserDetailsService localAccounts;

	@Test
	void preservesOwnershipAcrossEmailChangesAndRefreshesRoles() {
		String subject = UUID.randomUUID().toString();
		var first = service.synchronize(identity(subject, subject + "@example.com", true, List.of("ADMIN")));
		var next = service.synchronize(identity(subject, "new-" + subject + "@example.com", true, List.of()));
		assertThat(first.getRole()).isEqualTo(AccountRole.ADMIN);
		assertThat(next.getId()).isEqualTo(first.getId());
		assertThat(next.getRole()).isEqualTo(AccountRole.CUSTOMER);
		assertThatThrownBy(() -> localAccounts.loadUserByUsername(next.getEmail()))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void refusesEmailLinkingToLocalAccounts() {
		String email = UUID.randomUUID() + "@example.com";
		accounts.saveAndFlush(new UserAccount(email, "unused-hash", AccountRole.ADMIN));
		assertThatThrownBy(() -> service.synchronize(identity("external", email, true, List.of("ADMIN"))))
				.isInstanceOf(OAuth2AuthenticationException.class);
	}

	@Test
	void refusesUnverifiedEmailAndDoesNotTrustUnrelatedRoles() {
		String email = UUID.randomUUID() + "@example.com";
		assertThatThrownBy(() -> service.synchronize(identity("unverified", email, false, List.of("ADMIN"))))
				.isInstanceOf(OAuth2AuthenticationException.class);
		assertThat(service.synchronize(identity("customer-" + UUID.randomUUID(), email, true, List.of("realm-admin"))).getRole())
				.isEqualTo(AccountRole.CUSTOMER);
	}

	private DefaultOidcUser identity(String subject, String email, boolean verified, List<String> roles) {
		var token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(300),
				Map.of("iss", "https://identity.example.com/realms/coffee-shop", "sub", subject,
						"email", email, "email_verified", verified, "coffee_shop_roles", roles));
		return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token);
	}
}