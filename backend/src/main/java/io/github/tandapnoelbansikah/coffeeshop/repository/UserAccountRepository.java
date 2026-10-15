package io.github.tandapnoelbansikah.coffeeshop.repository;

import java.util.Optional;

import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

/** Looks up local accounts by their normalized email address. */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	Optional<UserAccount> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);
}