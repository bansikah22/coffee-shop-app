package io.github.tandapnoelbansikah.coffeeshop.service;

import java.util.Locale;

import io.github.tandapnoelbansikah.coffeeshop.domain.AccountRole;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Registers customer accounts and hashes passwords before persistence. */
@Service
public class AccountService {

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;

	public AccountService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public UserAccount registerCustomer(String email, String password) {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
		}
		return userAccountRepository.save(new UserAccount(normalizedEmail, passwordEncoder.encode(password), AccountRole.CUSTOMER));
	}
}