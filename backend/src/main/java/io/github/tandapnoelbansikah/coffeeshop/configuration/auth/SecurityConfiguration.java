package io.github.tandapnoelbansikah.coffeeshop.configuration.auth;

import io.github.tandapnoelbansikah.coffeeshop.repository.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/** Configures session authentication, CSRF protection, and role-based API access. */
@Configuration
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(UserAccountRepository userAccountRepository) {
		return email -> userAccountRepository.findByEmailIgnoreCase(email)
				.filter(io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount::isLocalAccount)
				.orElseThrow(() -> new UsernameNotFoundException("Account not found"));
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
			ObjectProvider<KeycloakConfiguration> keycloak,
			ObjectProvider<ClientRegistrationRepository> registrations) throws Exception {
		if (keycloak.getIfAvailable() != null) {
			keycloak.getObject().configure(http, registrations.getObject());
		}
		return http
				.cors(withDefaults())
				.securityContext(context -> context.securityContextRepository(contextRepository))
				.csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/oauth2/authorization/**", "/api/login/oauth2/code/**",
								"/api/v1/auth/providers", "/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login",
								"/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**")
							.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/coffees", "/api/v1/coffees/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/coffees", "/api/v1/coffees/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/v1/coffees/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/coffees/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/v1/admin/orders/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/orders/**").authenticated()
						.requestMatchers("/api/v1/auth/logout", "/api/v1/auth/me").authenticated()
						.anyRequest().denyAll())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) -> response.sendError(401))
						.accessDeniedHandler((request, response, exception) -> response.sendError(403)))
				.build();
	}
}