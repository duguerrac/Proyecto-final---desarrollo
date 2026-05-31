package com.smartlogistics.warehouse.application.port.out;

import com.smartlogistics.warehouse.domain.model.OutboxEventStatus;
import java.util.List;

public interface OutboxEventRepositoryPort {
    Long save(String aggregateId, String eventType, String payload, OutboxEventStatus status);
    void markPublished(Long id);
    void markFailed(Long id);
    List<OutboxMessage> findPending(int limit);

    record OutboxMessage(Long id, String aggregateId, String eventType, String payload) {
    }
}
