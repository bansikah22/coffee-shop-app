package io.github.tandapnoelbansikah.payment.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import io.github.tandapnoelbansikah.payment.demo.DemoOrderConsumer;
import io.github.tandapnoelbansikah.payment.demo.DemoOrderStore;
import io.github.tandapnoelbansikah.payment.domain.PaymentResult;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class DeliveryTests {
    @Test
    void negativeConfirmNeverCountsAsPublication() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(false, "unavailable"));
            return null;
        }).when(template).send(anyString(), anyString(), any(), any(CorrelationData.class));
        assertThatThrownBy(() -> new ConfirmedPublisher(template).send("exchange", "key", MessageBuilder.withBody(new byte[0]).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acknowledgesOnlyAfterStoreCommit() throws Exception {
        DemoOrderStore store = mock(DemoOrderStore.class);
        ConfirmedPublisher publisher = mock(ConfirmedPublisher.class);
        Channel channel = mock(Channel.class);
        var event = new PaymentResult(1, UUID.randomUUID().toString(), "payment.succeeded", UUID.randomUUID().toString(), "order-1", 300, "USD", "SUCCEEDED");
        var message = MessageBuilder.withBody(new Gson().toJson(event).getBytes(StandardCharsets.UTF_8)).setMessageId(event.eventId()).build();
        message.getMessageProperties().setDeliveryTag(7);
        new DemoOrderConsumer(store, publisher).receive(message, channel);
        var order = inOrder(store, channel);
        order.verify(store).accept(event);
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void transientFailureConfirmsDelayedRetryBeforeAcknowledging() throws Exception {
        DemoOrderStore store = mock(DemoOrderStore.class);
        doThrow(new IllegalStateException("database offline")).when(store).accept(any());
        ConfirmedPublisher publisher = mock(ConfirmedPublisher.class);
        Channel channel = mock(Channel.class);
        var event = new PaymentResult(1, UUID.randomUUID().toString(), "payment.succeeded", UUID.randomUUID().toString(), "order-1", 300, "USD", "SUCCEEDED");
        var message = MessageBuilder.withBody(new Gson().toJson(event).getBytes(StandardCharsets.UTF_8)).setMessageId(event.eventId()).build();
        message.getMessageProperties().setDeliveryTag(8);
        new DemoOrderConsumer(store, publisher).receive(message, channel);
        var order = inOrder(publisher, channel);
        order.verify(publisher).send(eq(RabbitTopology.RETRY), eq(RabbitTopology.RETRY_KEY), any());
        order.verify(channel).basicAck(8, false);
        message.getMessageProperties().setHeader("x-demo-retries", 3);
        new DemoOrderConsumer(store, publisher).receive(message, channel);
        verify(channel).basicReject(8, false);
    }

    @Test
    void malformedEventGoesToDeadLetterWithoutAcknowledgment() throws Exception {
        Channel channel = mock(Channel.class);
        var message = MessageBuilder.withBody("invalid-json".getBytes(StandardCharsets.UTF_8)).build();
        new DemoOrderConsumer(mock(DemoOrderStore.class), mock(ConfirmedPublisher.class)).receive(message, channel);
        verify(channel).basicReject(0, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}