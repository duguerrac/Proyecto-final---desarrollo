# TASKS: SmartLogistics Backend Implementation

> Cada tarea es completable en una sesión enfocada. Ordenadas por dependencia.

---

## Fase A: Foundation

### Task A.1 — Crear docker-compose.yml con servicios base

- **Acceptance**: `docker compose up` levanta PostgreSQL, Redis, MongoDB y NATS sin errores
- **Verify**: `docker compose ps` → 4 servicios "running"
- **Files**:
  - `smartlogistics/docker-compose.yml`
  - `smartlogistics/.env.example`

### Task A.2 — Crear script de datos semilla para PostgreSQL

- **Acceptance**: Script SQL crea tablas e inserta: 5 inventory_items, 5 spots, 10 spot_items, 10 root_points, 15 route_edges
- **Verify**: Ejecutar contra PostgreSQL local y verificar `SELECT COUNT(*)` en cada tabla
- **Files**:
  - `smartlogistics/scripts/seed-warehouse.sql`

### Task A.3 — Crear demo-flow.http para pruebas manuales

- **Acceptance**: Archivo HTTP client con requests para todo el flujo: crear orden, asignar robot, calcular ruta, completar ruta
- **Verify**: Abrir en VS Code con REST Client extension y probar cada request
- **Files**:
  - `smartlogistics/scripts/demo-flow.http`

---

## Fase B: MS-RobotStatus

### Task B.1 — Inicializar proyecto Spring Boot con dependencias

- **Acceptance**: `mvn clean compile` sin errores. Dependencias: web, data-redis, actuator, prometheus
- **Verify**: `mvn test` pasa (al menos el test de contexto de Spring)
- **Files**:
  - `smartlogistics/ms-robot-status/pom.xml`
  - `smartlogistics/ms-robot-status/Dockerfile`

### Task B.2 — Implementar modelo y repositorio Redis

- **Acceptance**: `Robot` entity con id, batteryLevel, available, currentLocation. RedisRepository guarda y consulta por id
- **Verify**: Test unitario: `redisRepository.findById("RBT-01")` retorna Optional con Robot
- **Files**:
  - `ms-robot-status/src/main/java/.../model/Robot.java`
  - `ms-robot-status/src/main/java/.../repository/RobotStatusRepository.java`

### Task B.3 — Implementar controlador REST

- **Acceptance**:
  - `GET /api/robots/{id}/status` → 200 con estado del robot
  - `GET /api/robots/ROBOT-INEXISTENTE/status` → 404
  - `POST /api/robots` → 201, crea/actualiza robot
- **Verify**: `curl localhost:8082/api/robots/RBT-01/status` → 200 JSON
- **Files**:
  - `ms-robot-status/src/main/java/.../controller/RobotStatusController.java`
  - `ms-robot-status/src/main/java/.../service/RobotStatusService.java`

### Task B.4 — Sembrar datos de robots en Redis al iniciar

- **Acceptance**: Al arrancar, se insertan 5 robots (2 con batería < 15%, 3 con >= 15%)
- **Verify**: Consultar robot con batería baja: `curl localhost:8082/api/robots/RBT-LOW/status` → batteryLevel: 10
- **Files**:
  - `ms-robot-status/src/main/java/.../config/DataInitializer.java`

---

## Fase C: MS-WarehouseCore

### Task C.1 — Inicializar proyecto Spring Boot con estructura hexagonal

- **Acceptance**: Paquetes domain/, application/, infrastructure/ creados. `mvn clean compile` sin errores
- **Verify**: `mvn test` pasa
- **Files**:
  - `smartlogistics/ms-warehouse-core/pom.xml`
  - `smartlogistics/ms-warehouse-core/Dockerfile`

### Task C.2 — Implementar capa de dominio (entidades + políticas)

- **Acceptance**:
  - Entidades: DispatchOrder, InventoryItem, Spot, SpotItem, RootPoint, RouteEdge, RoutePlan, RouteStep
  - Value objects: OrderId, RobotId, SKU, Percentage, Location
  - Políticas: `RobotAssignmentPolicy` (battery >= 15%), `FragileProductPolicy` (reduce speed)
  - Métodos de dominio puros sin anotaciones framework
- **Verify**: Tests unitarios que validen:
  - `assignRobot` con battery >= 15 → AssignmentResult.accepted
  - `assignRobot` con battery < 15 → AssignmentResult.rejected
  - Aplicación de velocidad reducida en items frágiles
- **Files**:
  - `ms-warehouse-core/src/main/java/.../domain/model/*.java`
  - `ms-warehouse-core/src/main/java/.../domain/policy/*.java`

### Task C.3 — Definir puertos de aplicación

- **Acceptance**:
  - Puertos in: `CreateDispatchOrderUseCase`, `AssignRobotUseCase`, `CompleteRouteUseCase`
  - Puertos out: `WarehouseRepositoryPort`, `RobotStatusPort`, `RouteEventPublisherPort`
- **Verify**: Interfaces compilan sin errores
- **Files**:
  - `ms-warehouse-core/src/main/java/.../application/port/in/*.java`
  - `ms-warehouse-core/src/main/java/.../application/port/out/*.java`

### Task C.4 — Implementar servicios de aplicación

- **Acceptance**:
  - `DispatchOrderService`: crear orden, consultar detalle
  - `RobotAssignmentService`: validar batería, asignar robot, reservar stock
  - `RoutePlanningService`: calcular ruta con Dijkstra sobre grafo root_points → route_edges
  - `RouteCompletionService`: descontar stock, publicar evento route.completed, outbox
- **Verify**: Tests de integración con repositorios mockeados
- **Files**:
  - `ms-warehouse-core/src/main/java/.../application/service/*.java`

### Task C.5 — Implementar adaptador REST (controllers)

- **Acceptance**:
  - `POST /api/orders` → 201
  - `GET /api/orders/{id}` → 200
  - `POST /api/orders/{id}/assign-robot` → 200 si battery >= 15%, 409 si no
  - `POST /api/orders/{id}/route-plan` → 200 con ruta calculada
  - `POST /api/routes/{id}/complete` → 200, evento publicado en outbox
  - `GET /api/inventory/items/{sku}/spots` → 200
  - `GET /api/warehouse/graph` → 200
- **Verify**: Tests de integración @WebMvcTest o @SpringBootTest con cada endpoint
- **Files**:
  - `ms-warehouse-core/src/main/java/.../infrastructure/adapter/in/rest/*.java`

### Task C.6 — Implementar adaptador PostgreSQL (JPA repositories)

- **Acceptance**: Cada entidad de dominio tiene su correspondiente JPA entity, repository, y mapper domain ↔ entity
- **Verify**: Test de integración que inserta y consulta en PostgreSQL de prueba
- **Files**:
  - `ms-warehouse-core/src/main/java/.../infrastructure/adapter/out/postgres/*.java`

### Task C.7 — Implementar adaptador MS-RobotStatus (Feign/WebClient)

- **Acceptance**: `RobotStatusClient` consulta `GET /api/robots/{id}/status` al MS-RobotStatus
- **Verify**: Test con WireMock o similar mockea respuesta del robot status
- **Files**:
  - `ms-warehouse-core/src/main/java/.../infrastructure/adapter/out/robotstatus/RobotStatusClient.java`

### Task C.8 — Implementar adaptador NATS (event publisher) + Outbox

- **Acceptance**: `NatsRouteEventPublisher` publica evento `route.completed` al topic NATS
- **Acceptance**: Outbox pattern: evento se persiste en `outbox_event`, scheduler reintenta publicación si NATS falla
- **Verify**: Completar ruta, verificar evento en tabla outbox con status PUBLISHED
- **Files**:
  - `ms-warehouse-core/src/main/java/.../infrastructure/adapter/out/broker/NatsRouteEventPublisher.java`

---

## Fase D: MS-LogisticsAnalytics

### Task D.1 — Inicializar proyecto Spring Boot

- **Acceptance**: Dependencias: web, mongodb, nats-spring, actuator, prometheus
- **Verify**: `mvn test` pasa
- **Files**:
  - `smartlogistics/ms-logistics-analytics/pom.xml`
  - `smartlogistics/ms-logistics-analytics/Dockerfile`

### Task D.2 — Implementar consumer NATS y persistencia MongoDB

- **Acceptance**: Suscriptor al topic `route.completed`. Deserializa JSON a `RouteEvent` document. Persiste en MongoDB colección `route_events`
- **Verify**: Completar ruta → evento aparece en MongoDB
- **Files**:
  - `ms-logistics-analytics/src/main/java/.../consumer/NatsRouteEventConsumer.java`
  - `ms-logistics-analytics/src/main/java/.../model/RouteEvent.java`
  - `ms-logistics-analytics/src/main/java/.../repository/RouteEventRepository.java`
  - `ms-logistics-analytics/src/main/java/.../service/AnalyticsService.java`

---

## Fase E: Nginx Gateway

### Task E.1 — Configurar Nginx como reverse proxy

- **Acceptance**: Nginx en puerto 8080. `/api/` → proxy_pass a `ms-warehouse-core:8081`
- **Verify**: `curl localhost:8080/api/orders` → misma respuesta que directo a puerto 8081
- **Files**:
  - `smartlogistics/nginx/nginx.conf`
  - `smartlogistics/nginx/Dockerfile`

### Task E.2 — Integrar Nginx en docker-compose.yml

- **Acceptance**: `docker compose up` incluye nginx. Red interna conecta nginx → warehouse-core
- **Verify**: `docker compose ps` → nginx running
- **Files**:
  - `smartlogistics/docker-compose.yml` (actualizar)

---

## Fase F: Observabilidad

### Task F.1 — Configurar Prometheus

- **Acceptance**: Prometheus scrapea targets: warehouse-core:8081, robot-status:8082, analytics:8083
- **Verify**: `curl localhost:9090/api/v1/targets` → todos UP
- **Files**:
  - `smartlogistics/observability/prometheus.yml`

### Task F.2 — Configurar Loki para logs

- **Acceptance**: Loki recibe logs de los contenedores Docker
- **Verify**: Grafana datasource Loki configurado y muestra logs
- **Files**:
  - `smartlogistics/observability/loki-config.yml`

### Task F.3 — Configurar Grafana con dashboards

- **Acceptance**: Grafana inicia con Prometheus y Loki como datasources. Dashboard pre-configurado con:
  - HTTP request rate por endpoint
  - Latencia p99
  - Conteo de robots rechazados por batería
  - Rutas completadas en el tiempo
- **Verify**: `localhost:3000` → dashboard listo sin configuración manual
- **Files**:
  - `smartlogistics/observability/grafana/provisioning/datasources/prometheus.yml`
  - `smartlogistics/observability/grafana/provisioning/datasources/loki.yml`
  - `smartlogistics/observability/grafana/provisioning/dashboards/smartlogistics.json`

### Task F.4 — Agregar métricas Actuator en cada MS

- **Acceptance**: Cada microservicio expone `/actuator/prometheus` con métricas HTTP
- **Verify**: `curl localhost:8081/actuator/prometheus` → métricas visibles
- **Files**:
  - `ms-warehouse-core/pom.xml` (agregar actuator + micrometer)
  - `ms-robot-status/pom.xml`
  - `ms-logistics-analytics/pom.xml`
  - `application.properties` en cada MS

---

## Bonus (si sobra tiempo)

### Task G.1 — Jaeger para trazabilidad distribuida

### Task G.2 — Endpoints simulados de ERP vía NATS consumer adicional

### Task G.3 — Pruebas de carga con k6 o vegeta
