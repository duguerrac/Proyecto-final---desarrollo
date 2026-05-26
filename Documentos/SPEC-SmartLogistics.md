# Spec: SmartLogistics — Autonomous Warehouse Management System

## Objective

Build a microservice-based architecture for a high-density distribution center that uses autonomous robots for product picking. The system must coordinate dispatch orders in real-time, validate robot availability and battery level, apply reduced speed for fragile products, and register completed routes for logistics analytics.

**Team:** 3 integrantes (Kevin Santiago Martinez Molina y equipo)
**Course:** Arquitectura de Software II
**Scenario:** Caso 9 — SmartLogistics: Gestión de Almacenes Autónomos

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Main DB | PostgreSQL 16 |
| Cache / State | Redis 7 |
| Analytics DB | MongoDB 7 |
| Broker | NATS |
| Proxy | Nginx 1.27-alpine |
| Observability | Prometheus + Grafana + Loki |
| Orchestration | Docker Compose |

## Project Structure

```
smartlogistics/
├── docker-compose.yml
├── .env.example
├── README.md
├── docs/
│   ├── RFC-SmartLogistics.md
│   ├── C4/
│   │   └── smartlogistics_c4.drawio
│   └── api-contracts/
├── nginx/
│   ├── Dockerfile
│   └── nginx.conf
├── ms-warehouse-core/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       └── main/java/com/smartlogistics/warehouse/
│           ├── domain/
│           │   ├── model/
│           │   │   ├── DispatchOrder.java
│           │   │   ├── InventoryItem.java
│           │   │   ├── Spot.java
│           │   │   ├── SpotItem.java
│           │   │   ├── RootPoint.java
│           │   │   └── RoutePlan.java
│           │   ├── policy/
│           │   │   ├── RobotAssignmentPolicy.java
│           │   │   └── FragileProductPolicy.java
│           │   └── exception/
│           ├── application/
│           │   ├── port/in/
│           │   │   ├── CreateDispatchOrderUseCase.java
│           │   │   ├── AssignRobotUseCase.java
│           │   │   └── CompleteRouteUseCase.java
│           │   ├── port/out/
│           │   │   ├── WarehouseRepositoryPort.java
│           │   │   ├── RobotStatusPort.java
│           │   │   ├── RouteEventPublisherPort.java
│           │   │   └── WebhookNotificationPort.java
│           │   └── service/
│           │       ├── DispatchOrderService.java
│           │       ├── RobotAssignmentService.java
│           │       └── RoutePlanningService.java
│           └── infrastructure/
│               ├── adapter/in/rest/
│               ├── adapter/out/postgres/
│               ├── adapter/out/robotstatus/
│               ├── adapter/out/broker/
│               ├── adapter/out/webhook/
│               └── config/
├── ms-robot-status/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── ms-logistics-analytics/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── observability/
│   ├── prometheus.yml
│   ├── loki-config.yml
│   └── grafana/provisioning/
└── scripts/
    ├── seed-warehouse.sql
    └── demo-flow.http
```

## Code Style

- **Architecture:** Hexagonal (puertos/adaptadores) — dominio NO importa frameworks
- **Domain:** Clases puras POJO, sin anotaciones JPA, sin dependencias de Spring
- **Application:** Casos de uso que orquestan puertos, sin lógica de infraestructura
- **Infrastructure:** Controladores REST, repositorios JPA, clientes HTTP, publicadores NATS
- **Naming:** Clases en PascalCase, métodos en camelCase, paquetes en minúsculas
- **Exceptions:** Dominio define excepciones de negocio; infraestructura las mapea a HTTP

```java
// Domain policy — pure business rule, no framework dependencies
public class RobotAssignmentPolicy {
    private static final double MIN_BATTERY_PERCENTAGE = 15.0;

    public boolean canAssign(RobotStatus status) {
        return status.batteryPercentage() >= MIN_BATTERY_PERCENTAGE 
            && status.isAvailable();
    }
}

// Application use case — orchestrates ports
@Component
public class AssignRobotService implements AssignRobotUseCase {
    private final WarehouseRepositoryPort warehouseRepo;
    private final RobotStatusPort robotStatusPort;
    private final RobotAssignmentPolicy policy;

    public AssignResult assign(UUID orderId, UUID robotId) {
        DispatchOrder order = warehouseRepo.findOrder(orderId);
        RobotStatus status = robotStatusPort.getRobotStatus(robotId);
        
        if (!policy.canAssign(status)) {
            return AssignResult.rejected("Battery below 15% or robot unavailable");
        }
        
        order.assignRobot(robotId);
        warehouseRepo.save(order);
        return AssignResult.success(order);
    }
}
```

## Testing Strategy

| Level | Scope | Tool |
|-------|-------|------|
| Unit | Domain policies & entities | JUnit 5 + AssertJ |
| Integration | Application services + adapters | SpringBootTest + Testcontainers |
| API | REST endpoints | MockMvc |
| Event | NATS publishing/consuming | Embedded NATS or Testcontainers |
| E2E | Docker Compose full stack | demo-flow.http |

**Coverage target:** Domain 90%+, Application 80%+

## API Contracts

### MS-WarehouseCore (via Nginx `/api/`)

| Method | Endpoint | Responsibility |
|--------|----------|---------------|
| POST | `/api/orders` | Create dispatch order with items |
| GET | `/api/orders/{id}` | Get order detail with items & route |
| POST | `/api/orders/{id}/assign-robot` | Validate battery & assign robot |
| POST | `/api/orders/{id}/route-plan` | Calculate route via Dijkstra |
| POST | `/api/routes/{id}/complete` | Complete route, deduct stock, publish event |
| GET | `/api/inventory/items/{sku}/spots` | Check item availability by location |
| GET | `/api/warehouse/map` | Get spots, root points, and edges |

### MS-RobotStatus (internal)

| Method | Endpoint | Responsibility |
|--------|----------|---------------|
| GET | `/api/robots/{robotId}/status` | Return availability, battery, location |
| POST | `/api/robots/{robotId}/status` | Update robot status (for testing) |

## Event Schema: `route.completed`

```json
{
  "eventId": "uuid",
  "eventType": "route.completed",
  "occurredAt": "2026-05-24T10:15:00Z",
  "orderId": "ORD-001",
  "robotId": "RBT-02",
  "warehouseId": "WH-01",
  "fragileItems": true,
  "totalDistanceMeters": 128.4,
  "durationSeconds": 420,
  "path": [
    { "rootPointId": "RP-START", "x": 0.0, "y": 0.0 },
    { "rootPointId": "RP-A1-03", "x": 12.5, "y": 4.0 }
  ]
}
```

## Data Model

| Table | Purpose |
|-------|---------|
| `inventory_item` | Product: id, sku, name, fragile, default_speed_limit |
| `spot` | Physical location: id, code, aisle, section, level, root_point_id |
| `spot_item` | Item quantity per spot: spot_id, item_id, quantity_available |
| `dispatch_order` | Order header: id, created_at, status, assigned_robot_id |
| `order_item` | Order lines: order_id, item_id, requested_quantity |
| `root_point` | Navigable graph node: id, code, x, y, z_level, type, blocked |
| `route_edge` | Graph edge: source_id, target_id, distance, bidirectional, weight |
| `route_plan` | Calculated route: id, order_id, robot_id, status, total_distance |
| `route_step` | Route step: route_plan_id, sequence, root_point_id, action |
| `outbox_event` | Event outbox: id, aggregate_id, type, payload, status, retry_count |

## Boundaries

### Always do
- Run tests before commits
- Follow hexagonal architecture (domain → app → infra)
- Validate all inputs at controller layer
- Use the outbox pattern for critical events (route.completed)
- Log with correlationId across services
- Expose Prometheus metrics for every endpoint

### Ask first
- Adding new dependencies to pom.xml
- Changing the route.completed event schema
- Modifying Docker Compose service definitions
- Adding/modifying database migrations
- Changing the assignment flow (sync/asynchronous boundary)

### Never do
- Put business rules in controllers or repositories
- Commit secrets, passwords, or .env files
- Skip the outbox for route.completed events
- Remove or skip tests without team approval
- Expose internal microservices directly (always through Nginx)

## Success Criteria

- [ ] `docker compose up --build` starts all services successfully
- [ ] POST `/api/orders` creates a dispatch order → 201
- [ ] POST `/api/orders/{id}/assign-robot` validates battery via RobotStatus → rejects if < 15%
- [ ] POST `/api/routes/{id}/complete` deducts stock and publishes `route.completed` to NATS
- [ ] `route.completed` event is persisted in MongoDB (analytics)
- [ ] Fragile products trigger reduced speed policy in the route plan
- [ ] If RobotStatus is down, assignment is rejected gracefully (no orphan orders)
- [ ] If Analytics is down, route completion succeeds and event is queued in outbox
- [ ] Prometheus scrapes metrics from all services
- [ ] Grafana dashboard shows: orders by status, routes completed, robot rejections
- [ ] Loki centralizes logs from all containers
- [ ] Nginx serves as single entry point (port 8080)
- [ ] Seed data populates warehouse with sample robots, spots, and root points

## Open Questions

Resueltas durante la aprobación del spec:
- Broker: NATS
- Analytics DB: MongoDB (políglota)
- Frontend: Separado (backend-only spec)
- Observabilidad: Prometheus + Grafana + Loki
- Stock: Reservar al asignar, descontar al completar ruta
- Algoritmo de ruteo: Dijkstra para MVP
