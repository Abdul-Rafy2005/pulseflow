package com.pulseflow.backend.queue;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines all RabbitMQ infrastructure for PulseFlow:
 *   Exchange : pulseflow.events.exchange  (topic)
 *   Routing  : event.created
 *   Queue    : events.queue  (durable, DLQ-backed)
 *   DLQ      : events.dlq   (durable)
 *
 * Consistent with PRD §8. The DLQ itself is a plain queue so messages
 * parked there are inspectable via the management UI. Phase 5 will add
 * retry policy; for now max-retries are on the consumer side.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_NAME   = "pulseflow.events.exchange";
    public static final String QUEUE_NAME      = "events.queue";
    public static final String DLQ_NAME        = "events.dlq";
    public static final String ROUTING_KEY     = "event.created";
    public static final String DLX_HEADER      = "x-dead-letter-exchange";
    public static final String DLX_ROUTING_KEY = "x-dead-letter-routing-key";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue eventsQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument(DLX_HEADER, "")            // default exchange
                .withArgument(DLX_ROUTING_KEY, DLQ_NAME)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding eventsBinding(Queue eventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(eventsQueue).to(eventsExchange).with(ROUTING_KEY);
    }

    /**
     * Use JSON serialization for all AMQP messages so EventMessage records
     * survive the wire as readable JSON (and won't break if the Java class
     * changes between producer/consumer deployments).
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
