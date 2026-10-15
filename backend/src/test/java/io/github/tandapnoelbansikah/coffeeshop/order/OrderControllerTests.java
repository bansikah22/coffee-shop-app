package io.github.tandapnoelbansikah.coffeeshop.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import io.github.tandapnoelbansikah.coffeeshop.domain.AccountRole;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserAccountRepository userAccountRepository;

	private UserAccount customer;

	@BeforeEach
	void createCustomer() {
		customer = userAccountRepository.saveAndFlush(
				new UserAccount("customer-" + java.util.UUID.randomUUID() + "@example.com", "hash", AccountRole.CUSTOMER));
	}

	@Test
	void createsAnOrderForAnAvailableCoffee() throws Exception {
		mockMvc.perform(post("/api/v1/orders").with(user(customer)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":2}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.coffeeName").value("Americano"))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.totalPrice").value(6.00))
				.andExpect(jsonPath("$.customerEmail").value(customer.getEmail()));

		mockMvc.perform(get("/api/v1/orders").with(user(customer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].customerEmail").value(customer.getEmail()));

		UserAccount otherCustomer = userAccountRepository.saveAndFlush(
				new UserAccount("other-" + java.util.UUID.randomUUID() + "@example.com", "hash", AccountRole.CUSTOMER));
		mockMvc.perform(get("/api/v1/orders").with(user(otherCustomer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void rejectsAnOrderWithAnInvalidQuantity() throws Exception {
		mockMvc.perform(post("/api/v1/orders").with(user(customer)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":0}"))
				.andExpect(status().isBadRequest());
	}
}