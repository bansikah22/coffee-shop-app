package io.github.tandapnoelbansikah.coffeeshop.web;

import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;

/** Public account identity and role returned by authentication endpoints. */
record AccountResponse(Long id, String email, String role) {

	static AccountResponse from(UserAccount account) {
		return new AccountResponse(account.getId(), account.getEmail(), account.getRole().name());
	}
}