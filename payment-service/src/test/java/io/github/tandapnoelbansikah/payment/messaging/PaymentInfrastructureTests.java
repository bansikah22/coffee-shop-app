package io.github.tandapnoelbansikah.payment.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import com.google.gson.Gson;
import io.github.tandapnoelbansikah.payment.application.CheckoutService;
import io.github.tandapnoelbansikah.payment.application.PaymentVerifier;
import io.github.tandapnoelbansikah.payment.demo.DemoOrderConsumer;
import io.github.tandapnoelbansikah.payment.demo.DemoOrderStore;
import io.github.tandapnoelbansikah.payment.domain.PaymentResult;
import io.github.tandapnoelbansikah.payment.provider.PaymentGateway;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs real PostgreSQL and RabbitMQ with only the external Stripe HTTP boundary mocked. */
@SpringBootTest
@ActiveProfiles("test")
@Import(PaymentInfrastructureTests.DemoConfiguration.class)
@EnabledIfEnvironmentVariable(named = "PAYMENT_INFRA_TEST", matches = "true")
class PaymentInfrastructureTests {
    @Autowired CheckoutService checkout;
    @Autowired PaymentVerifier verifier;
    @Autowired OutboxPublisher outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired ConfirmedPublisher publisher;
    @Autowired DemoOrderStore demoStore;
    @MockitoBean PaymentGateway gateway;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry config) {
        config.add("spring.datasource.url", () -> environment("PAYMENT_IT_DB_URL", "jdbc:postgresql://localhost:15433/payments"));
        config.add("spring.datasource.username", () -> "payments");
        config.add("spring.datasource.password", () -> System.getenv("PAYMENT_IT_DB_PASSWORD"));
        config.add("spring.rabbitmq.host", () -> environment("PAYMENT_IT_RABBIT_HOST", "localhost"));
        config.add("spring.rabbitmq.port", () -> environment("PAYMENT_IT_RABBIT_PORT", "15673"));
        config.add("spring.rabbitmq.username", () -> System.getenv("PAYMENT_IT_RABBIT_USER"));
        config.add("spring.rabbitmq.password", () -> System.getenv("PAYMENT_IT_RABBIT_PASSWORD"));
    }

    @TestConfiguration
    static class DemoConfiguration {
        @Bean
        DemoOrderStore demoOrderStore() {
            var dataSource = demoDataSource();
            Flyway.configure().dataSource(dataSource).locations("classpath:db/consumer").load().migrate();
            return new DemoOrderStore(new JdbcTemplate(dataSource), new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        }

        @Bean
        DemoOrderConsumer demoConsumer(DemoOrderStore store, ConfirmedPublisher confirmed) {
            return new DemoOrderConsumer(store, confirmed);
        }
    }

    @Test
    void paymentFlowsThroughOutboxRabbitAndIndependentConsumerDatabase() {
        String reference = "infra-" + UUID.randomUUID();
        when(gateway.create(any())).thenReturn(new PaymentGateway.Checkout("cs_test_" + UUID.randomUUID(), "https://checkout.stripe.com/test"));
        var attempt = checkout.create("infra-demo", reference, reference, 300, "USD");
        when(gateway.retrieve(attempt.sessionId())).thenReturn(new PaymentGateway.Snapshot(attempt.sessionId(), attempt.id(), reference, 300, "usd", false, "paid", "complete"));
        verifier.verify(attempt.sessionId());
        outbox.publishBatch();
        JdbcTemplate consumerDatabase = new JdbcTemplate(demoDataSource());
        await().atMost(Duration.ofSeconds(20)).ignoreException(org.springframework.dao.EmptyResultDataAccessException.class)
            .untilAsserted(() -> assertThat(consumerDatabase.queryForObject(
                "SELECT status FROM demo_orders WHERE order_reference=?", String.class, reference)).isEqualTo("PREPARATION_ALLOWED"));
        String payload = jdbc.queryForObject("SELECT payload FROM payment_outbox WHERE payment_id=?", String.class, attempt.id());
        var result = new Gson().fromJson(payload, PaymentResult.class);
        var message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8)).setMessageId(result.eventId())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        publisher.send(RabbitTopology.EVENTS, result.eventType(), message);
        demoStore.accept(result);
        assertThat(consumerDatabase.queryForObject("SELECT count(*) FROM processed_events WHERE event_id=?", Integer.class, result.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT published FROM payment_outbox WHERE payment_id=?", Boolean.class, attempt.id())).isTrue();
    }

    @Test
    void malformedMessageIsDeadLetteredByActualBroker() {
        String messageId = UUID.randomUUID().toString();
        publisher.send(RabbitTopology.EVENTS, "payment.succeeded", MessageBuilder.withBody("malformed".getBytes(StandardCharsets.UTF_8))
                .setMessageId(messageId).setDeliveryMode(MessageDeliveryMode.PERSISTENT).build());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var dead = rabbit.receive(RabbitTopology.DEAD_QUEUE, 1000);
            assertThat(dead).isNotNull();
            assertThat(dead.getMessageProperties().getHeaders()).containsKey("x-death");
        });
    }

    @Test
    void unroutablePublicationIsNotMistakenForSuccess() {
        assertThatThrownBy(() -> publisher.send(RabbitTopology.EVENTS, "unmatched.route", MessageBuilder.withBody(new byte[0]).build()))
                .isInstanceOf(IllegalStateException.class);
    }

            @Test
            void actualRetryQueueDelaysAndRoutesBackToConsumer() {
            var result = new PaymentResult(1, UUID.randomUUID().toString(), "payment.succeeded", UUID.randomUUID().toString(),
                "retry-" + UUID.randomUUID(), 300, "USD", "SUCCEEDED");
            publisher.send(RabbitTopology.RETRY, RabbitTopology.RETRY_KEY,
                MessageBuilder.withBody(new Gson().toJson(result).getBytes(StandardCharsets.UTF_8))
                    .setMessageId(result.eventId()).setHeader("x-demo-retries", 1)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build());
            JdbcTemplate demo = new JdbcTemplate(demoDataSource());
            await().atMost(Duration.ofSeconds(15)).ignoreException(org.springframework.dao.EmptyResultDataAccessException.class)
                .untilAsserted(() -> assertThat(demo.queryForObject(
                "SELECT status FROM demo_orders WHERE order_reference=?", String.class, result.orderReference())).isEqualTo("PREPARATION_ALLOWED"));
            }

            @Test
            void consumerRejectsReusedEventIdWithChangedPayload() {
            var result = new PaymentResult(1, UUID.randomUUID().toString(), "payment.succeeded", UUID.randomUUID().toString(),
                "conflict-" + UUID.randomUUID(), 300, "USD", "SUCCEEDED");
            demoStore.accept(result);
            demoStore.accept(result);
            var conflict = new PaymentResult(1, result.eventId(), result.eventType(), result.paymentId(), result.orderReference(), 500, "USD", "SUCCEEDED");
            assertThatThrownBy(() -> demoStore.accept(conflict)).isInstanceOf(IllegalArgumentException.class);
            assertThat(new JdbcTemplate(demoDataSource()).queryForObject("SELECT amount_minor FROM demo_orders WHERE order_reference=?", Long.class,
                result.orderReference())).isEqualTo(300);
            }

    private static DriverManagerDataSource demoDataSource() {
        return new DriverManagerDataSource(environment("PAYMENT_IT_DEMO_URL", "jdbc:postgresql://localhost:15434/demo_orders"),
                "demo_orders", System.getenv("PAYMENT_IT_DEMO_PASSWORD"));
    }

    private static String environment(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }
}