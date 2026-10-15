package io.github.tandapnoelbansikah.coffeeshop.web;

/** Provides the CSRF header name and token to the same-origin frontend. */
record CsrfResponse(String headerName, String token) {
}