package com.smartlogistics.robotstatus.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for ms-robot-status.
 * Declares exchanges, queues, and bindings replacing NATS subjects.
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchange ──────────────────────────────────────────
    public static final String EXCHANGE = "logistics.exchange";

    // ── Queue names ───────────────────────────────────────
    public static final String QUEUE_ROBOT_TELEMETRY = "robot.telemetry";
    public static final String QUEUE_ROBOT_COMMAND = "robot.command";
    public static final String QUEUE_ROBOT_DISPATCH_ORDER = "robot.dispatch.order";
    public static final String QUEUE_ROBOT_DISPATCH_PACKAGE = "robot.dispatch.package";
    public static final String QUEUE_ROBOT_STATUS_UPDATE = "robot.status.update";
    public static final String QUEUE_ROBOT_STATUS_BATCH = "robot.status.batch";

    // ── Routing keys ──────────────────────────────────────
    public static final String RK_ROBOT_TELEMETRY = "robot.telemetry";
    public static final String RK_ROBOT_COMMAND = "robot.command";
    public static final String RK_ORDER_DISPATCHED = "order.dispatched";
    public static final String RK_PACKAGE_DISPATCHED = "package.dispatched";
    public static final String RK_ROBOT_STATUS_UPDATE = "robot.status.update";
    public static final String RK_ROBOT_STATUS_BATCH = "robot.status.batch";

    @Bean
    public TopicExchange logisticsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    // ── Inbound queues (consumed by ms-robot-status) ──────

    @Bean
    public Queue robotTelemetryQueue() {
        return QueueBuilder.durable(QUEUE_ROBOT_TELEMETRY).build();
    }

    @Bean
    public Queue robotDispatchOrderQueue() {
        return QueueBuilder.durable(QUEUE_ROBOT_DISPATCH_ORDER).build();
    }

    @Bean
    public Queue robotDispatchPackageQueue() {
        return QueueBuilder.durable(QUEUE_ROBOT_DISPATCH_PACKAGE).build();
    }

    // ── Outbound queues (published by ms-robot-status) ────

    @Bean
    public Queue robotStatusUpdateQueue() {
        return QueueBuilder.durable(QUEUE_ROBOT_STATUS_UPDATE).build();
    }

    @Bean
    public Queue robotStatusBatchQueue() {
        return QueueBuilder.durable(QUEUE_ROBOT_STATUS_BATCH).build();
    }

    @Bean
    public Queue robotCommandQueue() {
        return QueueBuilder.durable(QUEUE_ROBOT_COMMAND).build();
    }

    // ── Bindings ──────────────────────────────────────────

    @Bean
    public Binding telemetryBinding() {
        return BindingBuilder.bind(robotTelemetryQueue())
                .to(logisticsExchange()).with(RK_ROBOT_TELEMETRY);
    }

    @Bean
    public Binding dispatchOrderBinding() {
        return BindingBuilder.bind(robotDispatchOrderQueue())
                .to(logisticsExchange()).with(RK_ORDER_DISPATCHED);
    }

    @Bean
    public Binding dispatchPackageBinding() {
        return BindingBuilder.bind(robotDispatchPackageQueue())
                .to(logisticsExchange()).with(RK_PACKAGE_DISPATCHED);
    }

    @Bean
    public Binding statusUpdateBinding() {
        return BindingBuilder.bind(robotStatusUpdateQueue())
                .to(logisticsExchange()).with(RK_ROBOT_STATUS_UPDATE);
    }

    @Bean
    public Binding statusBatchBinding() {
        return BindingBuilder.bind(robotStatusBatchQueue())
                .to(logisticsExchange()).with(RK_ROBOT_STATUS_BATCH);
    }

    @Bean
    public Binding commandBinding() {
        return BindingBuilder.bind(robotCommandQueue())
                .to(logisticsExchange()).with(RK_ROBOT_COMMAND);
    }
}