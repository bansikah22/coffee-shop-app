package io.github.tandapnoelbansikah.coffeeshop.configuration;

import io.github.tandapnoelbansikah.coffeeshop.domain.AccountRole;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates disposable demo identities only when the local dev profile is active. */
@Component
@Profile("dev")
public class DemoAccountInitializer implements ApplicationRunner {

	private final UserAccountRepository accounts;
	private final PasswordEncoder passwordEncoder;
	private final String adminEmail;
	private final String adminPassword;
	private final String customerEmail;
	private final String customerPassword;

	public DemoAccountInitializer(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
			@Value("${app.demo.admin.email}") String adminEmail,
			@Value("${app.demo.admin.password}") String adminPassword,
			@Value("${app.demo.customer.email}") String customerEmail,
			@Value("${app.demo.customer.password}") String customerPassword) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
		this.customerEmail = customerEmail;
		this.customerPassword = customerPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		createIfMissing(adminEmail, adminPassword, AccountRole.ADMIN);
		createIfMissing(customerEmail, customerPassword, AccountRole.CUSTOMER);
	}

	private void createIfMissing(String email, String password, AccountRole role) {
		if (!accounts.existsByEmailIgnoreCase(email)) {
			accounts.save(new UserAccount(email, passwordEncoder.encode(password), role));
		}
	}
}