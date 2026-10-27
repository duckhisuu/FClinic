package com.fclinic.appointmentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class RabbitMQConfig {
  @Bean
  public TopicExchange clinicEvents() {
    return ExchangeBuilder.topicExchange("clinic.events").durable(true).build();
  }

  @Bean
  public Queue appointmentNotifications() {
    return QueueBuilder.durable("fclinic.appointment.notifications").build();
  }

  @Bean
  public Declarables appointmentBindings(
      TopicExchange clinicEvents, Queue appointmentNotifications) {
    return new Declarables(
        BindingBuilder.bind(appointmentNotifications).to(clinicEvents).with("appointment.created"),
            BindingBuilder.bind(appointmentNotifications)
                .to(clinicEvents)
                .with("appointment.cancelled"),
        BindingBuilder.bind(appointmentNotifications)
                .to(clinicEvents)
                .with("appointment.rescheduled"),
            BindingBuilder.bind(appointmentNotifications)
                .to(clinicEvents)
                .with("appointment.reminder"));
  }

  @Bean
  public RabbitTemplate appointmentRabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMandatory(true);
    return template;
  }
}
