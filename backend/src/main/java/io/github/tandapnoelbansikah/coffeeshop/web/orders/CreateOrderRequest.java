package io.github.tandapnoelbansikah.coffeeshop.web.orders;

import java.util.Objects;

import io.github.tandapnoelbansikah.coffeeshop.domain.FulfillmentType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Validates customer contact and fulfillment details when an order is submitted. */
record CreateOrderRequest(@NotNull Long coffeeId, @Min(1) int quantity, FulfillmentType fulfillmentType,
		@NotBlank @Size(max = 32) @Pattern(regexp = "\\+?[0-9() .-]{7,32}") String customerPhone,
		@Size(max = 500) String deliveryAddress) {

	CreateOrderRequest {
		fulfillmentType = Objects.requireNonNullElse(fulfillmentType, FulfillmentType.PICKUP);
		customerPhone = customerPhone == null ? null : customerPhone.trim();
		deliveryAddress = deliveryAddress == null ? null : deliveryAddress.trim();
	}

	@AssertTrue(message = "A delivery address is required for delivery")
	boolean isDeliveryAddressPresent() {
		return fulfillmentType != FulfillmentType.DELIVERY || deliveryAddress != null && !deliveryAddress.isBlank();
	}
}