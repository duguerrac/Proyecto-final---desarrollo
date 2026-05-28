package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres;

import com.smartlogistics.warehouse.application.port.out.OutboxEventRepositoryPort;
import com.smartlogistics.warehouse.domain.model.OutboxEventStatus;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.OutboxEventJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresOutboxEventRepositoryAdapter implements OutboxEventRepositoryPort {
    private final OutboxEventJpaRepository outboxEventRepository;

    public PostgresOutboxEventRepositoryAdapter(OutboxEventJpaRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public Long save(String aggregateId, String eventType, String payload, OutboxEventStatus status) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setAggregateId(aggregateId);
        entity.setEventType(eventType);
        entity.setPayload(payload);
        entity.setStatus(status.name());
        entity.setRetryCount(0);
        return outboxEventRepository.saveAndFlush(entity).getId();
    }

    @Override
    public void markPublished(Long id) {
        OutboxEventJpaEntity entity = outboxEventRepository.findById(id).orElseThrow();
        entity.setStatus(OutboxEventStatus.PUBLISHED.name());
        entity.setLastAttemptAt(Instant.now());
        outboxEventRepository.save(entity);
    }

    @Override
    public void markFailed(Long id) {
        OutboxEventJpaEntity entity = outboxEventRepository.findById(id).orElseThrow();
        entity.setStatus(OutboxEventStatus.PENDING.name());
        entity.setRetryCount(entity.getRetryCount() + 1);
        entity.setLastAttemptAt(Instant.now());
        outboxEventRepository.save(entity);
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        return outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING.name()).stream()
                .limit(limit)
                .map(entity -> new OutboxMessage(entity.getId(), entity.getAggregateId(), entity.getEventType(), entity.getPayload()))
                .toList();
    }
}
