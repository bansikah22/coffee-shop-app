package io.github.tandapnoelbansikah.coffeeshop.configuration;

import java.util.Locale;

import io.github.tandapnoelbansikah.coffeeshop.domain.AccountRole;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

/** Creates the initial administrator only when explicit deployment credentials are configured. */
@Component
@Profile("!dev")
public class BootstrapAdminInitializer implements ApplicationRunner {

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String password;

	public BootstrapAdminInitializer(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
			@Value("${app.bootstrap-admin.email:}") String email,
			@Value("${app.bootstrap-admin.password:}") String password) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (email.isBlank() && password.isBlank()) return;
		if (email.isBlank() || password.isBlank() || password.length() < 12 || password.length() > 72) {
			throw new IllegalStateException("Configure both bootstrap admin values; password length must be 12-72 characters");
		}

		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		userAccountRepository.findByEmailIgnoreCase(normalizedEmail).ifPresentOrElse(account -> {
			if (account.getRole() != AccountRole.ADMIN) {
				throw new IllegalStateException("Bootstrap admin email is already registered as a customer");
			}
		}, () -> userAccountRepository.save(
				new UserAccount(normalizedEmail, passwordEncoder.encode(password), AccountRole.ADMIN)));
	}
}