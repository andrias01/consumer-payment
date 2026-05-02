package com.co.eatupapi.config.rabbitmq.payment;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRabbitMQConfig {

    @Value("${rabbitmq.exchange.payment}")
    private String exchangeName;

    @Value("${rabbitmq.queue.payment.cashreceipt.create}")
    private String createQueueName;

    @Value("${rabbitmq.queue.payment.cashreceipt.cancel}")
    private String cancelQueueName;

    @Value("${rabbitmq.routing-key.payment.cashreceipt.create}")
    private String createRoutingKey;

    @Value("${rabbitmq.routing-key.payment.cashreceipt.cancel}")
    private String cancelRoutingKey;

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables paymentDeclarables() {
        DirectExchange paymentExchange = new DirectExchange(exchangeName);
        Queue createQueue = QueueBuilder.durable(createQueueName).build();
        Queue cancelQueue = QueueBuilder.durable(cancelQueueName).build();

        Binding createBinding = BindingBuilder.bind(createQueue).to(paymentExchange).with(createRoutingKey);
        Binding cancelBinding = BindingBuilder.bind(cancelQueue).to(paymentExchange).with(cancelRoutingKey);

        return new Declarables(paymentExchange, createQueue, cancelQueue, createBinding, cancelBinding);
    }
}
