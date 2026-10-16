package io.github.tandapnoelbansikah.coffeeshop.domain;

import java.util.Map;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class OidcAccount extends UserAccount implements OidcUser {

	private final OidcUser delegate;

	public OidcAccount(UserAccount account, OidcUser delegate) {
		super(account);
		this.delegate = delegate;
	}

	@Override
	public Map<String, Object> getClaims() {
		return delegate.getClaims();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return delegate.getAttributes();
	}

	@Override
	public OidcIdToken getIdToken() {
		return delegate.getIdToken();
	}

	@Override
	public OidcUserInfo getUserInfo() {
		return delegate.getUserInfo();
	}

	@Override
	public String getName() {
		return delegate.getName();
	}
}