package com.smartlogistics.warehouse.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange:logistics.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue.package-taken:package.taken}")
    private String packageTakenQueue;

    @Value("${rabbitmq.queue.package-delivered:package.delivered}")
    private String packageDeliveredQueue;

    @Value("${rabbitmq.routing-key.route-completed:route.completed}")
    private String routeCompletedKey;

    // ─── Exchange ───

    @Bean
    public TopicExchange logisticsExchange() {
        return new TopicExchange(exchange);
    }

    // ─── Queues ───

    @Bean
    public Queue packageTakenQueue() {
        return QueueBuilder.durable(packageTakenQueue).build();
    }

    @Bean
    public Queue packageDeliveredQueue() {
        return QueueBuilder.durable(packageDeliveredQueue).build();
    }

    @Bean
    public Queue routeCompletedQueue() {
        return QueueBuilder.durable("route.completed").build();
    }

    @Bean
    public Queue robotStatusUpdateQueue() {
        return QueueBuilder.durable("robot.status.update.wh").build();
    }

    // ─── Bindings ───

    @Bean
    public Binding packageTakenBinding(Queue packageTakenQueue, TopicExchange logisticsExchange) {
        return BindingBuilder.bind(packageTakenQueue).to(logisticsExchange).with("package.taken");
    }

    @Bean
    public Binding packageDeliveredBinding(Queue packageDeliveredQueue, TopicExchange logisticsExchange) {
        return BindingBuilder.bind(packageDeliveredQueue).to(logisticsExchange).with("package.delivered");
    }

    @Bean
    public Binding routeCompletedBinding(Queue routeCompletedQueue, TopicExchange logisticsExchange) {
        return BindingBuilder.bind(routeCompletedQueue).to(logisticsExchange).with(routeCompletedKey);
    }

    @Bean
    public Binding robotStatusUpdateBinding(Queue robotStatusUpdateQueue, TopicExchange logisticsExchange) {
        return BindingBuilder.bind(robotStatusUpdateQueue).to(logisticsExchange).with("robot.status.update");
    }
}