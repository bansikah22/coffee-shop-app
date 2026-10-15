package io.github.tandapnoelbansikah.coffeeshop.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated input for creating a customer account. */
record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 12, max = 72) String password) {
}