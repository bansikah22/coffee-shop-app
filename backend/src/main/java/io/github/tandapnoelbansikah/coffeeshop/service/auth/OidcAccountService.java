package io.github.tandapnoelbansikah.coffeeshop.service.auth;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

import io.github.tandapnoelbansikah.coffeeshop.domain.AccountRole;
import io.github.tandapnoelbansikah.coffeeshop.domain.OidcAccount;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OidcAccountService {

	private final UserAccountRepository accounts;
	private final PasswordEncoder passwordEncoder;

	public OidcAccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public OidcAccount synchronize(OidcUser identity) {
		String email = identity.getEmail();
		String subject = identity.getSubject();
		if (identity.getIssuer() == null || subject == null || subject.isBlank() || subject.length() > 255
				|| !Boolean.TRUE.equals(identity.getEmailVerified()) || email == null || email.isBlank()
				|| email.length() > 254) {
			throw rejected();
		}
		String issuer = identity.getIssuer().toString();
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		UserAccount account = accounts.findByOidcIssuerAndOidcSubject(issuer, subject).orElse(null);
		UserAccount emailOwner = accounts.findByEmailIgnoreCase(normalizedEmail).orElse(null);
		if (emailOwner != null && (account == null || !emailOwner.getId().equals(account.getId()))) {
			throw rejected();
		}
		Object roles = identity.getIdToken().getClaims().get("coffee_shop_roles");
		AccountRole role = roles instanceof Collection<?> grantedRoles && grantedRoles.contains("ADMIN")
				? AccountRole.ADMIN : AccountRole.CUSTOMER;
		if (account == null) {
			account = new UserAccount(normalizedEmail, passwordEncoder.encode(UUID.randomUUID().toString()), role);
			account.bindOidcIdentity(issuer, subject);
		}
		account.updateOidcProfile(normalizedEmail, role);
		return new OidcAccount(accounts.saveAndFlush(account), identity);
	}

	private OAuth2AuthenticationException rejected() {
		return new OAuth2AuthenticationException(new OAuth2Error("invalid_identity"), "Identity cannot be provisioned");
	}
}