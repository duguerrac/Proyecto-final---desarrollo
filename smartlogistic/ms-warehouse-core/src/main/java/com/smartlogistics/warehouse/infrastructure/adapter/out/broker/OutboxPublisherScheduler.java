package com.smartlogistics.warehouse.infrastructure.adapter.out.broker;

import com.smartlogistics.warehouse.application.port.out.OutboxEventRepositoryPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisherScheduler {
    private final OutboxEventRepositoryPort outboxRepository;
    private final RabbitRouteEventPublisher publisher;

    public OutboxPublisherScheduler(OutboxEventRepositoryPort outboxRepository, RabbitRouteEventPublisher publisher) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher-delay-ms:10000}")
    public void publishPending() {
        for (OutboxEventRepositoryPort.OutboxMessage message : outboxRepository.findPending(10)) {
            try {
                publisher.publishPayload(message.payload());
                outboxRepository.markPublished(message.id());
            } catch (RuntimeException ex) {
                outboxRepository.markFailed(message.id());
            }
        }
    }
}
