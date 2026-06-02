package com.smartlogistics.warehouse.domain.model;

import java.math.BigDecimal;

/**
 * Represents a directed (optionally bidirectional) edge between two
 * root_points in the warehouse navigation graph.
 *
 * @param sourceId      ID of the source root_point
 * @param targetId      ID of the target root_point
 * @param distance      physical distance between the two points
 * @param bidirectional if true, the edge can be traversed in both directions
 * @param weight        cost weight for pathfinding (default 1.0)
 */
public record RouteEdge(
        Long sourceId,
        Long targetId,
        BigDecimal distance,
        boolean bidirectional,
        BigDecimal weight
) {}