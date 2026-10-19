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
import io.github.tandapnoelbansikah.coffeeshop.service.orders.PaymentCheckoutClient;
import io.github.tandapnoelbansikah.coffeeshop.service.orders.PaymentResultEvent;
import io.github.tandapnoelbansikah.coffeeshop.service.orders.PaymentResultProcessor;
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
		"app.payment.enabled=true", "app.payment.client-secret=test-client-secret",
		"app.payment.token-uri=https://keycloak.test/token",
		"app.cors.allowed-origins=http://localhost:8088" })
@AutoConfigureMockMvc
class OrderControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserAccountRepository userAccountRepository;
	@Autowired
	private OrderService orderService;
	@Autowired
	private PaymentResultProcessor paymentResultProcessor;
	@MockitoBean
	private JavaMailSender mailSender;
	@MockitoBean
	private PaymentCheckoutClient paymentCheckoutClient;

	private UserAccount customer;

	@BeforeEach
	void createCustomer() {
		customer = userAccountRepository.saveAndFlush(
				new UserAccount("customer-" + java.util.UUID.randomUUID() + "@example.com", "hash", AccountRole.CUSTOMER));
		org.mockito.Mockito.when(paymentCheckoutClient.start(any())).thenAnswer(invocation -> {
			var order = invocation.getArgument(0, io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder.class);
			return new PaymentCheckoutClient.Checkout(java.util.UUID.nameUUIDFromBytes(("payment-" + order.getId()).getBytes()).toString(),
					order.getId().toString(), order.getTotalPrice().movePointRight(2).longValueExact(), "USD", "PENDING",
					"https://checkout.stripe.com/c/pay/synthetic-test-session");
		});
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
				.andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
				.andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/c/pay/synthetic-test-session"))
				.andExpect(jsonPath("$.customerEmail").value(customer.getEmail()));
		verify(mailSender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
		var pending = orderService.listCustomerOrders(customer.getId()).getFirst();
		markPaid(pending);
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
				.andExpect(jsonPath("$[0].customerPhone").value("555-0100"))
				.andExpect(jsonPath("$[0].status").value("PAID"));

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
				.andExpect(jsonPath("$.deliveryAddress").value("12 Main Street, Central"))
				.andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));

		mockMvc.perform(get("/api/v1/orders").with(user(customer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryAddress").value("12 Main Street, Central"))
				.andExpect(jsonPath("$[0].status").value("AWAITING_PAYMENT"));
		verify(mailSender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
		markPaid(orderService.listCustomerOrders(customer.getId()).getFirst());

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
		markPaid(orderService.listCustomerOrders(customer.getId()).getFirst());
		mockMvc.perform(get("/api/v1/orders").with(user(customer)))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PAID"));
	}

	@Test
	void markingOrderReadySendsOneReadyEmailAndRequiresAdmin() throws Exception {
		var order = orderService.createOrder(1L, 1, FulfillmentType.PICKUP, "555-0100", null, customer);
		String paymentId = java.util.UUID.randomUUID().toString();
		orderService.attachPaymentAttempt(order.getId(), customer.getId(), paymentId);
		paymentResultProcessor.apply(paymentEvent(order.getId(), paymentId, 300, "payment.succeeded", "SUCCEEDED"));
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

	@Test
	void checkoutRetryIsOwnedAndReusesTheSamePaymentAttempt() throws Exception {
		var result = mockMvc.perform(post("/api/v1/orders").with(user(customer)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":1,\"fulfillmentType\":\"PICKUP\",\"customerPhone\":\"555-0100\"}"))
				.andExpect(status().isCreated()).andReturn();
		Number rawOrderId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		Long orderId = rawOrderId.longValue();
		String paymentId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.paymentAttemptId");
		mockMvc.perform(post("/api/v1/orders/{orderId}/checkout", orderId).with(user(customer)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.paymentAttemptId").value(paymentId));
		UserAccount other = userAccountRepository.saveAndFlush(new UserAccount(
				"other-" + java.util.UUID.randomUUID() + "@example.com", "hash", AccountRole.CUSTOMER));
		mockMvc.perform(post("/api/v1/orders/{orderId}/checkout", orderId).with(user(other)).with(csrf()))
				.andExpect(status().isNotFound());
		org.mockito.Mockito.verify(paymentCheckoutClient, times(2)).start(any());
	}

	@Test
	void mismatchedPaymentCannotMarkOrderPaidAndDuplicateSuccessSendsEmailOnce() {
		var order = orderService.createOrder(1L, 1, FulfillmentType.PICKUP, "555-0100", null, customer);
		String paymentId = java.util.UUID.randomUUID().toString();
		orderService.attachPaymentAttempt(order.getId(), customer.getId(), paymentId);
		var mismatch = paymentEvent(order.getId(), paymentId, 301, "payment.succeeded", "SUCCEEDED");
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentResultProcessor.apply(mismatch))
				.isInstanceOf(IllegalArgumentException.class);
		org.assertj.core.api.Assertions.assertThat(orderService.listCustomerOrders(customer.getId()).getFirst().getStatus())
				.isEqualTo(io.github.tandapnoelbansikah.coffeeshop.domain.OrderStatus.AWAITING_PAYMENT);
		verify(mailSender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));

		var success = paymentEvent(order.getId(), paymentId, 300, "payment.succeeded", "SUCCEEDED");
		org.assertj.core.api.Assertions.assertThat(paymentResultProcessor.apply(success)).isTrue();
		org.assertj.core.api.Assertions.assertThat(paymentResultProcessor.apply(success)).isFalse();
		org.assertj.core.api.Assertions.assertThat(orderService.listCustomerOrders(customer.getId()).getFirst().getStatus())
				.isEqualTo(io.github.tandapnoelbansikah.coffeeshop.domain.OrderStatus.PAID);
		verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
	}

	@Test
	void awaitingPaymentOrderCannotBePreparedByAdministrator() throws Exception {
		var order = orderService.createOrder(1L, 1, FulfillmentType.PICKUP, "555-0100", null, customer);
		mockMvc.perform(patch("/api/v1/admin/orders/{id}/ready", order.getId()).with(user("admin").roles("ADMIN")).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
		org.assertj.core.api.Assertions.assertThat(orderService.listCustomerOrders(customer.getId()).getFirst().getStatus())
				.isEqualTo(io.github.tandapnoelbansikah.coffeeshop.domain.OrderStatus.AWAITING_PAYMENT);
		verify(mailSender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
	}

	private void markPaid(io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder order) {
		paymentResultProcessor.apply(paymentEvent(order.getId(), order.getPaymentAttemptId(),
				order.getTotalPrice().movePointRight(2).longValueExact(), "payment.succeeded", "SUCCEEDED"));
	}

	private PaymentResultEvent paymentEvent(Long orderId, String paymentId, long amountMinor, String eventType, String status) {
		return new PaymentResultEvent(1, java.util.UUID.randomUUID().toString(), eventType, paymentId,
				orderId.toString(), amountMinor, "USD", status);
	}
}