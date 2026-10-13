package io.github.tandapnoelbansikah.coffeeshop.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
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

	@Test
	void createsAnOrderForAnAvailableCoffee() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":2}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.coffeeName").value("Americano"))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.totalPrice").value(6.00));
	}

	@Test
	void rejectsAnOrderWithAnInvalidQuantity() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"coffeeId\":1,\"quantity\":0}"))
				.andExpect(status().isBadRequest());
	}
}