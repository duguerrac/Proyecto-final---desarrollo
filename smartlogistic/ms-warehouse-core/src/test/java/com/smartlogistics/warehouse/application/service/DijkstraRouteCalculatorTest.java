package com.smartlogistics.warehouse.application.service;

import com.smartlogistics.warehouse.domain.exception.RoutePlanningException;
import com.smartlogistics.warehouse.domain.model.RootPoint;
import com.smartlogistics.warehouse.domain.model.RouteEdge;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DijkstraRouteCalculatorTest {
    private final DijkstraRouteCalculator calculator = new DijkstraRouteCalculator();

    @Test
    void calculatesShortestPathThroughPickupAndExit() {
        List<RootPoint> points = List.of(
                point(1L, "RP-START", false),
                point(2L, "RP-A", false),
                point(3L, "RP-B", false),
                point(4L, "RP-EXIT", false)
        );
        List<RouteEdge> edges = List.of(
                edge(1L, 2L, "1"),
                edge(2L, 4L, "1"),
                edge(1L, 3L, "10"),
                edge(3L, 4L, "10")
        );

        DijkstraRouteCalculator.RouteCalculation result = calculator.calculate(points, edges, "RP-START", List.of("RP-A"), "RP-EXIT");

        assertThat(result.totalDistance()).isEqualByComparingTo("2");
        assertThat(result.steps()).extracting("rootPointCode").containsExactly("RP-START", "RP-A", "RP-EXIT");
    }

    @Test
    void failsWhenTargetIsBlocked() {
        List<RootPoint> points = List.of(point(1L, "RP-START", false), point(2L, "RP-EXIT", true));

        assertThatThrownBy(() -> calculator.calculate(points, List.of(edge(1L, 2L, "1")), "RP-START", List.of(), "RP-EXIT"))
                .isInstanceOf(RoutePlanningException.class);
    }

    private RootPoint point(Long id, String code, boolean blocked) {
        return new RootPoint(id, code, BigDecimal.ZERO, BigDecimal.ZERO, 1, "INTERNAL", blocked);
    }

    private RouteEdge edge(Long source, Long target, String weight) {
        return new RouteEdge(source, target, new BigDecimal(weight), true, new BigDecimal(weight));
    }
}
