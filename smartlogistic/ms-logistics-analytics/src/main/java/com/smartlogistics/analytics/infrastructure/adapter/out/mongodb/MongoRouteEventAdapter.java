package com.smartlogistics.analytics.infrastructure.adapter.out.mongodb;

import com.smartlogistics.analytics.application.port.out.AnalyticsRepositoryPort;
import com.smartlogistics.analytics.domain.model.RouteEvent;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoRouteEventAdapter implements AnalyticsRepositoryPort {

    private final MongoTemplate mongoTemplate;

    public MongoRouteEventAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(RouteEvent event) {
        RouteEventDocument doc = new RouteEventDocument();
        doc.setEventId(event.getEventId());
        doc.setEventType(event.getEventType());
        doc.setOrderId(event.getOrderId());
        doc.setRobotId(event.getRobotId());
        doc.setWarehouseId(event.getWarehouseId());
        doc.setFragileItems(event.isFragileItems());
        doc.setPath(event.getPath());
        doc.setDistance(event.getDistance());
        doc.setDuration(event.getDuration());
        doc.setTimestamp(event.getTimestamp());
        mongoTemplate.save(doc, "route_events");
    }
}
