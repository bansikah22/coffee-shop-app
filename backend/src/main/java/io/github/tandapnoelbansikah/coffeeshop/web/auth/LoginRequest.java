package io.github.tandapnoelbansikah.coffeeshop.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated input for authenticating a local account. */
record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank String password) {
}