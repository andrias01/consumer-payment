package com.co.eatupapi.config.rabbitmq.payment;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRabbitMQConfig {

    // ── Exchange ─────────────────────────────────────
    @Value("${rabbitmq.exchange.payment}")
    private String exchangeName;

    // ── CashReceipt queues & routing keys ────────────
    @Value("${rabbitmq.queue.payment.cashreceipt.create}")
    private String cashReceiptCreateQueueName;

    @Value("${rabbitmq.queue.payment.cashreceipt.cancel}")
    private String cashReceiptCancelQueueName;

    @Value("${rabbitmq.routing-key.payment.cashreceipt.create}")
    private String cashReceiptCreateRoutingKey;

    @Value("${rabbitmq.routing-key.payment.cashreceipt.cancel}")
    private String cashReceiptCancelRoutingKey;

    // ── Invoice queues & routing keys ────────────────
    @Value("${rabbitmq.queue.payment.invoice.create}")
    private String invoiceCreateQueueName;

    @Value("${rabbitmq.queue.payment.invoice.cancel}")
    private String invoiceCancelQueueName;

    @Value("${rabbitmq.queue.payment.invoice.mark-paid}")
    private String invoiceMarkPaidQueueName;

    @Value("${rabbitmq.routing-key.payment.invoice.create}")
    private String invoiceCreateRoutingKey;

    @Value("${rabbitmq.routing-key.payment.invoice.cancel}")
    private String invoiceCancelRoutingKey;

    @Value("${rabbitmq.routing-key.payment.invoice.mark-paid}")
    private String invoiceMarkPaidRoutingKey;

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables paymentDeclarables() {
        DirectExchange paymentExchange = new DirectExchange(exchangeName, true, false);

        // ── CashReceipt ───────────────────────────────
        Queue cashReceiptCreateQueue = QueueBuilder
                .durable(cashReceiptCreateQueueName)
                .build();

        Queue cashReceiptCancelQueue = QueueBuilder
                .durable(cashReceiptCancelQueueName)
                .build();

        Binding cashReceiptCreateBinding = BindingBuilder
                .bind(cashReceiptCreateQueue)
                .to(paymentExchange)
                .with(cashReceiptCreateRoutingKey);

        Binding cashReceiptCancelBinding = BindingBuilder
                .bind(cashReceiptCancelQueue)
                .to(paymentExchange)
                .with(cashReceiptCancelRoutingKey);

        // ── Invoice ───────────────────────────────────
        Queue invoiceCreateQueue = QueueBuilder
                .durable(invoiceCreateQueueName)
                .build();

        Queue invoiceCancelQueue = QueueBuilder
                .durable(invoiceCancelQueueName)
                .build();

        Queue invoiceMarkPaidQueue = QueueBuilder
                .durable(invoiceMarkPaidQueueName)
                .build();

        Binding invoiceCreateBinding = BindingBuilder
                .bind(invoiceCreateQueue)
                .to(paymentExchange)
                .with(invoiceCreateRoutingKey);

        Binding invoiceCancelBinding = BindingBuilder
                .bind(invoiceCancelQueue)
                .to(paymentExchange)
                .with(invoiceCancelRoutingKey);

        Binding invoiceMarkPaidBinding = BindingBuilder
                .bind(invoiceMarkPaidQueue)
                .to(paymentExchange)
                .with(invoiceMarkPaidRoutingKey);

        return new Declarables(
                paymentExchange,

                cashReceiptCreateQueue,
                cashReceiptCancelQueue,
                cashReceiptCreateBinding,
                cashReceiptCancelBinding,

                invoiceCreateQueue,
                invoiceCancelQueue,
                invoiceMarkPaidQueue,
                invoiceCreateBinding,
                invoiceCancelBinding,
                invoiceMarkPaidBinding
        );
    }
}