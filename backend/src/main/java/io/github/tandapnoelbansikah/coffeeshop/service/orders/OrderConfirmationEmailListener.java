package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import io.github.tandapnoelbansikah.coffeeshop.domain.FulfillmentType;

/** Sends customer notifications after order placement and fulfillment transitions commit. */
@Component
@ConditionalOnProperty(name = "app.order-confirmation.enabled", havingValue = "true")
public class OrderConfirmationEmailListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrderConfirmationEmailListener.class);

	private final JavaMailSender mailSender;
	private final String fromAddress;

	public OrderConfirmationEmailListener(JavaMailSender mailSender,
			@Value("${app.order-confirmation.from}") String fromAddress) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void sendConfirmation(OrderPlacedEvent event) {
		String fulfillmentDetails = event.fulfillmentType() == FulfillmentType.DELIVERY
				? "Delivery address: " + event.deliveryAddress() + "\nContact phone: " + event.customerPhone() + "\n\n"
				: "Fulfillment: Pickup at Ember & Bean\nContact phone: " + event.customerPhone() + "\n\n";
		send(event.orderId(), event.customerEmail(), "Your Ember & Bean order is confirmed",
				"Hello,\n\n"
						+ "Thank you for choosing Ember & Bean. We have received your order and our team is preparing it.\n\n"
						+ fulfillmentDetails
						+ orderSummary(event.coffeeName(), event.quantity(), event.totalPrice())
						+ "\nWarm regards,\nEmber & Bean");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void sendReadyNotice(OrderReadyEvent event) {
		boolean delivery = event.fulfillmentType() == FulfillmentType.DELIVERY;
		send(event.orderId(), event.customerEmail(), delivery
				? "Your Ember & Bean delivery is on its way"
				: "Your Ember & Bean order is ready for collection",
				"Hello,\n\n" + (delivery
						? "Your order is on its way to the delivery address below.\n\nDelivery address: "
								+ event.deliveryAddress() + "\nContact phone: " + event.customerPhone() + "\n\n"
						: "Your order is ready for collection at Ember & Bean. Please show this email to our team.\n\n")
						+ orderSummary(event.coffeeName(), event.quantity(), event.totalPrice())
						+ "\nWarm regards,\nEmber & Bean");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void sendCompletedNotice(OrderCompletedEvent event) {
		boolean delivery = event.fulfillmentType() == FulfillmentType.DELIVERY;
		send(event.orderId(), event.customerEmail(), delivery
				? "Your Ember & Bean delivery is complete"
				: "Thank you for visiting Ember & Bean",
				"Hello,\n\n" + (delivery
						? "Your order has been marked as delivered. We hope you enjoy it.\n\n"
						: "Your order has been collected. Thank you for choosing Ember & Bean.\n\n")
						+ orderSummary(event.coffeeName(), event.quantity(), event.totalPrice())
						+ "\nWarm regards,\nEmber & Bean");
	}

	private void send(Long orderId, String customerEmail, String subject, String introduction) {
		if (customerEmail == null || customerEmail.isBlank()) {
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(customerEmail);
		message.setSubject(subject);
		message.setText(introduction);
		try {
			mailSender.send(message);
		} catch (MailException exception) {
			LOGGER.warn("Could not send order email for order {}", orderId, exception);
		}
	}

	private String orderSummary(String coffeeName, int quantity, java.math.BigDecimal totalPrice) {
		return "ORDER SUMMARY\n"
				+ "Coffee: " + coffeeName + "\n"
				+ "Quantity: " + quantity + "\n"
				+ "Total: $" + totalPrice.setScale(2).toPlainString() + "\n";
	}
}