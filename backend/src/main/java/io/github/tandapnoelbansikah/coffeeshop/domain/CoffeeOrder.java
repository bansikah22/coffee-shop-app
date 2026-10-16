package io.github.tandapnoelbansikah.coffeeshop.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stores purchased item, customer contact, fulfillment, and price snapshots for an order. */
@Entity
@Table(name = "coffee_orders")
public class CoffeeOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "coffee_id", nullable = false)
	private Long coffeeId;

	@Column(name = "coffee_name", nullable = false, length = 100)
	private String coffeeName;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	@Column(name = "total_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalPrice;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "customer_id")
	private Long customerId;

	@Column(name = "customer_email", length = 254)
	private String customerEmail;

	@Enumerated(EnumType.STRING)
	@Column(name = "fulfillment_type", nullable = false, length = 20)
	private FulfillmentType fulfillmentType = FulfillmentType.PICKUP;

	@Column(name = "customer_phone", length = 32)
	private String customerPhone;

	@Column(name = "delivery_address", length = 500)
	private String deliveryAddress;

	@Enumerated(EnumType.STRING)
	@Column(name = "order_status", nullable = false, length = 20)
	private OrderStatus status = OrderStatus.PLACED;

	protected CoffeeOrder() {
	}

	public CoffeeOrder(Long coffeeId, String coffeeName, int quantity, BigDecimal unitPrice, Long customerId,
			String customerEmail, FulfillmentType fulfillmentType, String customerPhone, String deliveryAddress) {
		this.coffeeId = coffeeId;
		this.coffeeName = coffeeName;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
		this.createdAt = Instant.now();
		this.customerId = customerId;
		this.customerEmail = customerEmail;
		this.fulfillmentType = fulfillmentType;
		this.customerPhone = customerPhone;
		this.deliveryAddress = deliveryAddress;
	}

	public Long getId() { return id; }
	public String getCoffeeName() { return coffeeName; }
	public int getQuantity() { return quantity; }
	public BigDecimal getTotalPrice() { return totalPrice; }
	public Instant getCreatedAt() { return createdAt; }
	public Long getCustomerId() { return customerId; }
	public String getCustomerEmail() { return customerEmail; }
	public OrderStatus getStatus() { return status; }
	public FulfillmentType getFulfillmentType() { return fulfillmentType; }
	public String getCustomerPhone() { return customerPhone; }
	public String getDeliveryAddress() { return deliveryAddress; }

	public boolean markReadyForFulfillment() {
		if (status != OrderStatus.PLACED) {
			return false;
		}
		status = fulfillmentType == FulfillmentType.DELIVERY ? OrderStatus.OUT_FOR_DELIVERY : OrderStatus.READY;
		return true;
	}

	public boolean markCompleted() {
		if (status != OrderStatus.READY && status != OrderStatus.OUT_FOR_DELIVERY) {
			return false;
		}
		status = OrderStatus.COMPLETED;
		return true;
	}
}