# Spec: SmartLogistics - Backend Microservices

## Objective

Construir el backend de **SmartLogistics**, un sistema de coordinación de almacenes autónomos con 3 microservicios que gestionan órdenes de despacho, robots autónomos y analítica logística. Las pruebas se realizan vía HTTP (Postman/curl). Sin frontend.

### Success Criteria

- Crear orden de despacho con ítems
- Asignar robot validando batería >= 15% (sync vía MS-RobotStatus)
- Rechazar asignación si batería < 15%
- Calcular ruta con Dijkstra sobre root points
- Cerrar ruta, descontar stock y publicar evento `route.completed` a NATS
- MS-LogisticsAnalytics consume el evento y lo persiste en MongoDB
- Todo el sistema levanta con `docker compose up`
- Métricas en Prometheus + dashboard en Grafana + logs en Loki

## Tech Stack

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 17+ |
| Framework | Spring Boot 3.x |
| MS Principal | MS-WarehouseCore (Arquitectura Hexagonal) |
| MS Síncrono | MS-RobotStatus (REST) |
| MS Asíncrono | MS-LogisticsAnalytics (NATS consumer) |
| Base datos Core | PostgreSQL 16 |
| Cache/Estado | Redis 7 |
| Base datos Analytics | MongoDB 7 |
| Broker | NATS 2.x |
| API Gateway | Nginx 1.27 (reverse proxy) |
| Observabilidad | Prometheus + Grafana + Loki |
| Contenedores | Docker Compose |

## Commands

```bash
# Build all services
cd ms-warehouse-core && mvn clean package -DskipTests
cd ms-robot-status && mvn clean package -DskipTests
cd ms-logistics-analytics && mvn clean package -DskipTests

# Full system
docker compose up --build

# Test endpoints
curl -X POST http://localhost:8080/api/orders ^
  -H "Content-Type: application/json" ^
  -d @scripts/demo-flow.json

# Stop and clean
docker compose down -v
```

## Project Structure

```
smartlogistics/
├── docker-compose.yml
├── .env.example
├── README.md
├── docs/
│   ├── RFC-SmartLogistics.md
│   └── C4/
├── nginx/
│   ├── Dockerfile
│   └── nginx.conf
├── ms-warehouse-core/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/smartlogistics/warehouse/
│       ├── domain/
│       │   ├── model/        # DispatchOrder, InventoryItem, Spot, SpotItem, RootPoint, RouteEdge, RoutePlan
│       │   └── policy/       # RobotAssignmentPolicy, FragileProductPolicy
│       ├── application/
│       │   ├── port/in/      # CreateDispatchOrderUseCase, AssignRobotUseCase, CompleteRouteUseCase
│       │   ├── port/out/     # WarehouseRepositoryPort, RobotStatusPort, RouteEventPublisherPort
│       │   └── service/      # DispatchOrderService, RobotAssignmentService, RoutePlanningService
│       └── infrastructure/
│           ├── adapter/in/rest/    # OrderController, RouteController
│           ├── adapter/out/postgres/ # JPA repositories, mappers
│           ├── adapter/out/robotstatus/  # Feign/WebClient to MS-RobotStatus
│           ├── adapter/out/broker/       # NATS publisher
│           └── config/
├── ms-robot-status/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/smartlogistics/robotstatus/
│       ├── controller/   # RobotStatusController
│       ├── service/      # RobotStatusService
│       ├── model/        # Robot, RobotStatus
│       └── repository/   # Redis repository
├── ms-logistics-analytics/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/smartlogistics/analytics/
│       ├── consumer/     # NATS consumer for route.completed
│       ├── service/      # AnalyticsService
│       ├── model/        # RouteEvent, CongestionSample
│       └── repository/   # MongoDB repository
├── observability/
│   ├── prometheus.yml
│   ├── loki-config.yml
│   └── grafana/provisioning/
└── scripts/
    ├── seed-warehouse.sql
    └── demo-flow.http
```

## API Contracts

### MS-WarehouseCore (via Nginx `/api/`)

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/orders` | Crear orden con ítems |
| `GET` | `/api/orders/{id}` | Detalle de orden |
| `POST` | `/api/orders/{id}/assign-robot` | Asignar robot (valida batería >= 15%) |
| `POST` | `/api/orders/{id}/route-plan` | Calcular ruta Dijkstra |
| `POST` | `/api/routes/{id}/complete` | Cerrar ruta, descontar stock, publicar evento |
| `GET` | `/api/inventory/items/{sku}/spots` | Stock por ubicación |
| `GET` | `/api/warehouse/graph` | Grafo navegable (root points + edges) |

### MS-RobotStatus (interno, no público)

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/robots/{id}/status` | Estado del robot (batería, disponible) |
| `POST` | `/api/robots` | Registrar/actualizar robot (simulación) |

### Evento NATS: `route.completed`

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

### PostgreSQL (MS-WarehouseCore)

| Tabla | Propósito | Campos clave |
|---|---|---|
| `inventory_item` | Producto físico | id, sku, name, fragile, default_speed_limit |
| `spot` | Ubicación física | id, code, aisle, section, level, root_point_id |
| `spot_item` | Stock por ubicación | spot_id, item_id, quantity_available |
| `dispatch_order` | Orden de despacho | id, created_at, status, assigned_robot_id |
| `order_item` | Ítems por orden | order_id, item_id, requested_quantity |
| `root_point` | Punto navegable | id, code, x, y, z_level, type, blocked |
| `route_edge` | Conexión entre puntos | source_id, target_id, distance, bidirectional, weight |
| `route_plan` | Ruta calculada | id, order_id, robot_id, status, total_distance |
| `route_step` | Paso de ruta | route_plan_id, sequence, root_point_id, action |
| `outbox_event` | Evento pendiente/publicado | id, aggregate_id, type, payload, status, retry_count |

### Redis (MS-RobotStatus)

```
Clave: robot:{robotId}:status
Tipo: Hash
Campos: batteryLevel, available, currentLocation, operationalMode
```

### MongoDB (MS-LogisticsAnalytics)

```
Colección: route_events
Documento: payload completo del evento route.completed
```

## Code Style

```java
// Domain — zero framework annotations, pure Java
public class DispatchOrder {
    private OrderId id;
    private List<OrderItem> items;
    private OrderStatus status;
    private RobotId assignedRobotId;

    public AssignmentResult assignRobot(RobotStatus robot, FragileProductPolicy fragilePolicy) {
        if (robot.batteryLevel() < 15) {
            return AssignmentResult.rejected("Battery below 15%");
        }
        this.assignedRobotId = robot.id();
        this.status = OrderStatus.ASSIGNED;
        return AssignmentResult.accepted(robot.id());
    }
}

// Application — port interface
public interface AssignRobotUseCase {
    AssignRobotResponse execute(AssignRobotCommand command);
}

// Infrastructure — adapter implements port
@RestController
class OrderController {
    private final CreateDispatchOrderUseCase createOrderUseCase;
    private final AssignRobotUseCase assignRobotUseCase;
}
```

## Testing Strategy

- **Unit tests** (JUnit 5 + Mockito): Domain policies, use cases
- **Integration tests** (@SpringBootTest): REST controllers, repositories
- **Coverage**: >= 70% en domain y application layers
- **Comando**: `mvn test` en cada módulo

## Boundaries

### Always do
- Validar batería >= 15% antes de asignar robot
- Registrar en outbox antes de publicar evento a NATS
- Ejecutar `mvn test` antes de cada commit
- Mantener dominio libre de anotaciones de framework

### Ask first
- Cambios en esquema de base de datos
- Nuevas dependencias en pom.xml
- Cambios en contratos de API o eventos
- Cambios en docker-compose.yml o redes

### Never do
- Reglas de negocio en controladores REST
- Lógica de dominio en adaptadores de infraestructura
- Commits sin pasar tests
- Secretos o credenciales en código

## Decisiones Cerradas

| Decisión | Opción elegida |
|---|---|
| Broker | NATS |
| Analytics DB | MongoDB |
| API Gateway | Nginx (incluido) |
| Frontend | Fuera de scope |
| Algoritmo de ruta | Dijkstra |
| Webhook ERP | No incluido en MVP. Documentado como puerto hexagonal |
| Descuento de stock | Reservar al asignar, descontar al completar |
