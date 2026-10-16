package io.github.tandapnoelbansikah.coffeeshop.domain;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Local account credentials and role used by Spring Security. */
@Entity
@Table(name = "customer_accounts")
public class UserAccount implements UserDetails {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountRole role;

	@Column(name = "oidc_issuer", length = 512)
	private String oidcIssuer;

	@Column(name = "oidc_subject", length = 255)
	private String oidcSubject;

	protected UserAccount() {
	}

	public UserAccount(String email, String passwordHash, AccountRole role) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	public Long getId() {
		return id;
	}

	protected UserAccount(UserAccount account) {
		this.id = account.id;
		this.email = account.email;
		this.passwordHash = account.passwordHash;
		this.role = account.role;
		this.oidcIssuer = account.oidcIssuer;
		this.oidcSubject = account.oidcSubject;
	}

	public void bindOidcIdentity(String issuer, String subject) {
		this.oidcIssuer = issuer;
		this.oidcSubject = subject;
	}

	public boolean isLocalAccount() {
		return oidcIssuer == null;
	}

	public void updateOidcProfile(String email, AccountRole role) {
		if (isLocalAccount()) {
			throw new IllegalStateException("Local accounts cannot be updated through OIDC");
		}
		this.email = email;
		this.role = role;
	}

	public String getEmail() {
		return email;
	}

	public AccountRole getRole() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}
}