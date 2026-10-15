package io.github.tandapnoelbansikah.coffeeshop.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminOrderControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deniesAnonymousAndCustomerAccessButAllowsAdministrators() throws Exception {
		mockMvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/orders").with(user("customer").roles("CUSTOMER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/orders").with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}
}