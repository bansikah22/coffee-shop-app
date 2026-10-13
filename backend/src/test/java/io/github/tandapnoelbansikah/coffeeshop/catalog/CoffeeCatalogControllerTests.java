package io.github.tandapnoelbansikah.coffeeshop.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CoffeeCatalogControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listsCoffeesAlphabetically() throws Exception {
		mockMvc.perform(get("/api/v1/coffees"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(4))
				.andExpect(jsonPath("$[0].name").value("Americano"))
				.andExpect(jsonPath("$[2].name").value("Flat White"))
				.andExpect(jsonPath("$[0].price").value(3.00));
	}

	@Test
	void createsRetrievesUpdatesAndDeletesCoffee() throws Exception {
		String request = "{\"name\":\"Latte\",\"description\":\"Espresso with steamed milk.\",\"price\":4.25,\"available\":true}";

		String response = mockMvc.perform(post("/api/v1/coffees").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Latte"))
				.andReturn().getResponse().getContentAsString();
		String coffeeId = response.replaceAll(".*\"id\":(\\d+).*", "$1");

		mockMvc.perform(get("/api/v1/coffees/{coffeeId}", coffeeId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Espresso with steamed milk."));

		mockMvc.perform(put("/api/v1/coffees/{coffeeId}", coffeeId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Iced Latte\",\"description\":\"Chilled espresso with milk.\",\"price\":4.75,\"available\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Iced Latte"))
				.andExpect(jsonPath("$.available").value(false));

		mockMvc.perform(delete("/api/v1/coffees/{coffeeId}", coffeeId)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/coffees/{coffeeId}", coffeeId)).andExpect(status().isNotFound());
	}
}