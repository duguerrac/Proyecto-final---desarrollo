package com.smartlogistics.analytics.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsConfig {

    public static final String EXCHANGE_NAME = "logistics.exchange";
    public static final String QUEUE_NAME = "route.completed.q";
    public static final String ROUTING_KEY = "route.completed";

    @Bean
    public TopicExchange logisticsExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue routeCompletedQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public Binding binding(Queue routeCompletedQueue, TopicExchange logisticsExchange) {
        return BindingBuilder
                .bind(routeCompletedQueue)
                .to(logisticsExchange)
                .with(ROUTING_KEY);
    }
}
