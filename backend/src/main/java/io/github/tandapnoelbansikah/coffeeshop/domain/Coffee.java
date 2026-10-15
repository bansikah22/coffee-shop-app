package io.github.tandapnoelbansikah.coffeeshop.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persists a coffee catalog item and its ordering availability. Introduced in Git on 2026-10-13. */
@Entity
@Table(name = "coffees")
public class Coffee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String name;

	@Column(nullable = false, length = 500)
	private String description;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Column(nullable = false)
	private boolean available;

	protected Coffee() {
	}

	public Coffee(String name, String description, BigDecimal price, boolean available) {
		update(name, description, price, available);
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public boolean isAvailable() {
		return available;
	}

	public void update(String name, String description, BigDecimal price, boolean available) {
		this.name = name;
		this.description = description;
		this.price = price;
		this.available = available;
	}
}