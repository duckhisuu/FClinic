package com.fclinic.notificationservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EVENTS_EXCHANGE = "clinic.events";
    public static final String NOTIFICATION_QUEUE = "notification.appointment.q";
    public static final String DLX = "clinic.dlx";
    public static final String DLQ = "notification.appointment.dlq";
    public static final String DLQ_ROUTING_KEY = "notification.appointment.dead";

    private static final String[] ROUTING_KEYS = {
            "appointment.created", "appointment.cancelled", "appointment.rescheduled", "appointment.reminder"};

    @Bean
    TopicExchange clinicEventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue notificationDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Declarables appointmentBindings(Queue notificationQueue, TopicExchange clinicEventsExchange) {
        Binding[] bindings = new Binding[ROUTING_KEYS.length];
        for (int i = 0; i < ROUTING_KEYS.length; i++) {
            bindings[i] = BindingBuilder.bind(notificationQueue).to(clinicEventsExchange).with(ROUTING_KEYS[i]);
        }
        return new Declarables(bindings);
    }

    @Bean
    Binding dlqBinding(Queue notificationDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDlq).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
    }

    @Bean
    JacksonJsonMessageConverter rabbitJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
