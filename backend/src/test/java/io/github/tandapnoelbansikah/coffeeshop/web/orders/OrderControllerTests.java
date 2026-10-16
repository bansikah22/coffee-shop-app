package io.github.tandapnoelbansikah.coffeeshop.web.orders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import org.mockito.ArgumentCaptor;
import io.github.tandapnoelbansikah.coffeeshop.domain.AccountRole;
import io.github.tandapnoelbansikah.coffeeshop.domain.FulfillmentType;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.UserAccountRepository;
import io.github.tandapnoelbansikah.coffeeshop.service.orders.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = { "app.order-confirmation.enabled=true", "management.health.mail.enabled=false",
		"app.cors.allowed-origins=http://localhost:8088" })
@AutoConfigureMockMvc
class OrderControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserAccountRepository userAccountRepository;
	@Autowired
	private OrderService orderService;
	@MockitoBean
	private JavaMailSender mailSender;

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
				.content("{\"coffeeId\":1,\"quantity\":2,\"fulfillmentType\":\"PICKUP\",\"customerPhone\":\"555-0100\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.coffeeName").value("Americano"))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.totalPrice").value(6.00))
				.andExpect(jsonPath("$.fulfillmentType").value("PICKUP"))
				.andExpect(jsonPath("$.customerPhone").value("555-0100"))
				.andExpect(jsonPath("$.customerEmail").value(customer.getEmail()));
		ArgumentCaptor<SimpleMailMessage> email = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(email.capture());
		org.assertj.core.api.Assertions.assertThat(email.getValue().getTo()).contains(customer.getEmail());
		org.assertj.core.api.Assertions.assertThat(email.getValue().getSubject())
				.isEqualTo("Your Ember & Bean order is confirmed");
		org.assertj.core.api.Assertions.assertThat(email.getValue().getText())
				.contains("Americano", "Quantity: 2", "Total: $6.00");
		org.assertj.core.api.Assertions.assertThat(email.getValue().getText()).doesNotContain("#");

		mockMvc.perform(get("/api/v1/orders").with(user(customer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].customerEmail").value(customer.getEmail()))
				.andExpect(jsonPath("$[0].fulfillmentType").value("PICKUP"))
				.andExpect(jsonPath("$[0].customerPhone").value("555-0100"));

		UserAccount otherCustomer = userAccountRepository.saveAndFlush(
				new UserAccount("other-" + java.util.UUID.randomUUID() + "@example.com", "hash", AccountRole.CUSTOMER));
		mockMvc.perform(get("/api/v1/orders").with(user(otherCustomer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void createsDeliveryOrderWithAddressAndIncludesDetailsInResponse() throws Exception {
		mockMvc.perform(post("/api/v1/orders").with(user(customer)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":1,\"fulfillmentType\":\"DELIVERY\",\"customerPhone\":\"+1 555-0100\",\"deliveryAddress\":\"12 Main Street, Central\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fulfillmentType").value("DELIVERY"))
				.andExpect(jsonPath("$.customerPhone").value("+1 555-0100"))
				.andExpect(jsonPath("$.deliveryAddress").value("12 Main Street, Central"));

		mockMvc.perform(get("/api/v1/orders").with(user(customer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryAddress").value("12 Main Street, Central"));

		ArgumentCaptor<SimpleMailMessage> email = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(email.capture());
		org.assertj.core.api.Assertions.assertThat(email.getValue().getText())
				.contains("Delivery address: 12 Main Street, Central", "Contact phone: +1 555-0100");
	}

	@Test
	void rejectsDeliveryOrderWithoutAnAddress() throws Exception {
		mockMvc.perform(post("/api/v1/orders").with(user(customer)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":1,\"fulfillmentType\":\"DELIVERY\",\"customerPhone\":\"555-0100\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsAnOrderWithAnInvalidQuantity() throws Exception {
		mockMvc.perform(post("/api/v1/orders").with(user(customer)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":0,\"fulfillmentType\":\"PICKUP\",\"customerPhone\":\"555-0100\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void keepsOrderSuccessfulWhenConfirmationEmailDeliveryFails() throws Exception {
		doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));
		mockMvc.perform(post("/api/v1/orders").with(user(customer)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":1,\"fulfillmentType\":\"PICKUP\",\"customerPhone\":\"555-0100\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void markingOrderReadySendsOneReadyEmailAndRequiresAdmin() throws Exception {
		var order = orderService.createOrder(1L, 1, FulfillmentType.PICKUP, "555-0100", null, customer);
		String readyPath = "/api/v1/admin/orders/" + order.getId() + "/ready";
		mockMvc.perform(patch(readyPath).header("Origin", "http://localhost:8088").with(user(customer)).with(csrf()))
				.andExpect(status().isForbidden());

		mockMvc.perform(patch(readyPath).header("Origin", "http://localhost:8088")
				.with(user("admin").roles("ADMIN")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"));
		mockMvc.perform(patch(readyPath).header("Origin", "http://localhost:8088")
				.with(user("admin").roles("ADMIN")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"));

		ArgumentCaptor<SimpleMailMessage> emails = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, times(2)).send(emails.capture());
		org.assertj.core.api.Assertions.assertThat(emails.getAllValues().get(1).getTo()).contains(customer.getEmail());
		org.assertj.core.api.Assertions.assertThat(emails.getAllValues().get(1).getSubject())
				.isEqualTo("Your Ember & Bean order is ready for collection");
		org.assertj.core.api.Assertions.assertThat(emails.getAllValues().get(1).getText())
				.contains("ready for collection", "Americano", "Total: $3.00");
		org.assertj.core.api.Assertions.assertThat(emails.getAllValues().get(1).getText()).doesNotContain("#");
		mockMvc.perform(options("/api/v1/admin/orders/7/ready")
				.header("Origin", "http://localhost:8088")
				.header("Access-Control-Request-Method", "PATCH")
				.header("Access-Control-Request-Headers", "X-CSRF-TOKEN"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8088"));
	}
}