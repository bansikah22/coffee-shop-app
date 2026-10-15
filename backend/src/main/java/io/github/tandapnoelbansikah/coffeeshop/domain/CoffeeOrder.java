package io.github.tandapnoelbansikah.coffeeshop.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stores an order snapshot, including item name and price at purchase time. Introduced in Git on 2026-10-13. */
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

	protected CoffeeOrder() {
	}

	public CoffeeOrder(Long coffeeId, String coffeeName, int quantity, BigDecimal unitPrice, Long customerId,
			String customerEmail) {
		this.coffeeId = coffeeId;
		this.coffeeName = coffeeName;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
		this.createdAt = Instant.now();
		this.customerId = customerId;
		this.customerEmail = customerEmail;
	}

	public Long getId() { return id; }
	public String getCoffeeName() { return coffeeName; }
	public int getQuantity() { return quantity; }
	public BigDecimal getTotalPrice() { return totalPrice; }
	public Instant getCreatedAt() { return createdAt; }
	public Long getCustomerId() { return customerId; }
	public String getCustomerEmail() { return customerEmail; }
}