package io.github.tandapnoelbansikah.coffeeshop.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Provides local account registration, session login/logout, and current identity endpoints. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AccountService accountService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;

	AuthController(AccountService accountService, AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository) {
		this.accountService = accountService;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
	}

	@GetMapping("/csrf")
	CsrfResponse csrf(CsrfToken csrfToken) {
		return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse register(@Valid @RequestBody RegisterRequest request) {
		return AccountResponse.from(accountService.registerCustomer(request.email(), request.password()));
	}

	@PostMapping("/login")
	AccountResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));
		servletRequest.getSession(true);
		servletRequest.changeSessionId();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, servletRequest, servletResponse);
		return AccountResponse.from((UserAccount) authentication.getPrincipal());
	}

	@GetMapping("/me")
	AccountResponse currentAccount(@AuthenticationPrincipal UserAccount account) {
		return AccountResponse.from(account);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}
}