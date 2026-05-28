# MS-WarehouseCore - Plan Operativo de Cierre

Este plan aterriza lo requerido en `PLAN-SmartLogistics.md`, `SPEC-SmartLogistics.md`, `TASKS-SmartLogistics.md`, `Documentos/SPEC-SmartLogistics.md` y el diagrama C4 para dejar `MS-WarehouseCore` listo para integrarse con los microservicios de los compañeros.

## Decisiones Obligatorias

- Usar Maven y Spring Boot.
- Mantener carpeta `smartlogistics/`.
- Mantener paquete base `com.smartlogistics.warehouse`.
- Mantener arquitectura hexagonal: dominio puro, aplicación con puertos, infraestructura con adaptadores.
- Usar PostgreSQL para Warehouse.
- Usar Spring Data JPA en infraestructura, porque el plan y las tareas lo piden explicitamente.
- Usar WebClient hacia `MS-RobotStatus`.
- Usar NATS para publicar `route.completed`.
- Usar Outbox antes de publicar a NATS.
- Mantener endpoints de `SPEC-SmartLogistics.md`.

## Fase 1 - Preparación Maven

Objetivo: que cualquier integrante pueda compilar sin Maven global.

Tareas:

- Agregar Maven Wrapper en `smartlogistics/ms-warehouse-core/`.
- Ajustar `pom.xml` para incluir:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-webflux`
  - `spring-boot-starter-actuator`
  - `micrometer-registry-prometheus`
  - `postgresql`
  - `flyway-core`
  - `flyway-database-postgresql`
  - `jnats`
  - `spring-boot-starter-test`

Criterios de aceptacion:

- `./mvnw test` funciona en Linux/Mac.
- `./mvnw.cmd test` funciona en Windows.
- El proyecto no depende de Maven instalado globalmente.

## Fase 2 - Migraciones PostgreSQL Con Flyway

Objetivo: que Warehouse cree y mantenga su schema, no depender solo de `scripts/seed-warehouse.sql`.

Tareas:

- Crear `src/main/resources/db/migration/V1__create_warehouse_schema.sql`.
- Crear `src/main/resources/db/migration/V2__seed_warehouse_data.sql`.
- `V1` debe crear:
  - `inventory_item`
  - `spot`
  - `spot_item`
  - `dispatch_order`
  - `order_item`
  - `root_point`
  - `route_edge`
  - `route_plan`
  - `route_step`
  - `outbox_event`
- `V2` debe insertar:
  - 5 `inventory_item`
  - 5 `spot`
  - 10 `spot_item`
  - 10 `root_point`
  - 15 `route_edge`
- Hacer todos los inserts idempotentes con `ON CONFLICT DO NOTHING`.
- Agregar `UNIQUE(source_id, target_id)` en `route_edge`.
- Agregar `quantity_reserved INT NOT NULL DEFAULT 0` en `spot_item`.
- Mantener `smartlogistics/scripts/seed-warehouse.sql` porque es entregable de Fase A, pero tomar Flyway como fuente confiable del microservicio.

Criterios de aceptacion:

- La base se crea al arrancar `ms-warehouse-core` contra PostgreSQL vacio.
- El seed no duplica registros.
- Conteos esperados:
  - `inventory_item = 5`
  - `spot = 5`
  - `spot_item = 10`
  - `root_point = 10`
  - `route_edge = 15`

## Fase 3 - Migrar Persistencia A Spring Data JPA

Objetivo: alinear la infraestructura con `PLAN` y `TASKS`.

Tareas:

- Reemplazar el adaptador JDBC por adaptador JPA.
- Crear entidades en `infrastructure/adapter/out/postgres/entity/`:
  - `InventoryItemJpaEntity`
  - `SpotJpaEntity`
  - `SpotItemJpaEntity`
  - `DispatchOrderJpaEntity`
  - `OrderItemJpaEntity`
  - `RootPointJpaEntity`
  - `RouteEdgeJpaEntity`
  - `RoutePlanJpaEntity`
  - `RouteStepJpaEntity`
  - `OutboxEventJpaEntity`
- Crear repositorios en `infrastructure/adapter/out/postgres/repository/`:
  - `InventoryItemJpaRepository`
  - `SpotJpaRepository`
  - `SpotItemJpaRepository`
  - `DispatchOrderJpaRepository`
  - `OrderItemJpaRepository`
  - `RootPointJpaRepository`
  - `RouteEdgeJpaRepository`
  - `RoutePlanJpaRepository`
  - `RouteStepJpaRepository`
  - `OutboxEventJpaRepository`
- Crear mapper `WarehouseJpaMapper`.
- Mantener dominio sin anotaciones JPA.
- El adaptador JPA debe implementar `WarehouseRepositoryPort` y `OutboxEventRepositoryPort`.

Criterios de aceptacion:

- No queda lógica SQL manual en servicios de aplicación.
- Las entidades JPA coinciden con Flyway.
- Dominio sigue puro.

## Fase 4 - Reserva Y Descuento De Stock

Objetivo: cumplir decision cerrada de la spec: reservar al asignar, descontar al completar.

Tareas:

- Agregar a `WarehouseRepositoryPort`:
  - `reserveStockForOrder(Long orderId)`
  - `decrementReservedStockForOrder(Long orderId)` o adaptar `decrementStockForOrder`.
- En `assign-robot`:
  - consultar robot
  - validar bateria y disponibilidad
  - reservar stock
  - asignar robot
- En `complete`:
  - descontar `quantity_available`
  - reducir `quantity_reserved`
  - completar ruta y orden
- Disponibilidad real:
  - `quantity_available - quantity_reserved`

Criterios de aceptacion:

- Si no hay stock suficiente, `POST /api/orders/{id}/assign-robot` devuelve `409`.
- No hay doble reserva del mismo stock.
- Al completar ruta, stock disponible baja y reserva se libera.

## Fase 5 - Dijkstra Y Ruta

Objetivo: asegurar que ruta cumpla el caso SmartLogistics.

Tareas:

- Mantener `DijkstraRouteCalculator`.
- Verificar que use:
  - `root_point`
  - `route_edge`
  - `route_edge.weight`
  - `bidirectional`
  - `blocked = false`
- Ruta esperada:
  - inicia en `RP-START`
  - pasa por root points de picking
  - termina en `RP-EXIT`

Criterios de aceptacion:

- `POST /api/orders/{id}/route-plan` responde ruta ordenada.
- Si no hay ruta, devuelve `409`.
- Tiene test con grafo semilla o equivalente.

## Fase 6 - Politica De Fragiles

Objetivo: demostrar que productos fragiles reducen velocidad.

Tareas:

- Agregar campo observable a `RoutePlan` y `route_plan`, recomendado:
  - `speed_factor DECIMAL(5,2) NOT NULL DEFAULT 1.0`
- Si la orden tiene item fragil:
  - `speed_factor = 0.50`
- Si no tiene fragiles:
  - `speed_factor = 1.00`
- Incluir `speedFactor` en `RoutePlanResponse`.
- No cambiar evento `route.completed` salvo acuerdo con Analytics.

Criterios de aceptacion:

- Test prueba que orden con producto fragil genera `speedFactor = 0.50`.
- La respuesta de ruta muestra el valor.

## Fase 7 - Evento route.completed Estricto

Objetivo: que `MS-LogisticsAnalytics` pueda consumir el evento sin adaptaciones.

Tareas:

- Alinear payload con `SPEC-SmartLogistics.md`:

```json
{
  "eventId": "uuid",
  "eventType": "route.completed",
  "occurredAt": "2026-05-24T10:15:00Z",
  "orderId": "1",
  "robotId": "RBT-01",
  "warehouseId": "WH-01",
  "fragileItems": true,
  "totalDistanceMeters": 128.4,
  "durationSeconds": 420,
  "path": [
    { "rootPointId": "RP-START", "x": 0.0, "y": 0.0 }
  ]
}
```

- `rootPointId` debe ser codigo de root point, no ID numerico.
- `path` debe conservar orden de `route_step.sequence`.
- Evitar campos extra en el evento.

Criterios de aceptacion:

- Evento guardado en outbox coincide con contrato.
- Evento publicado a NATS usa subject `route.completed`.

## Fase 8 - Outbox Con Reintento

Objetivo: completar ruta aunque NATS falle.

Tareas:

- Guardar evento en `outbox_event` con `PENDING` antes de publicar.
- Intentar publicar.
- Si publica, marcar `PUBLISHED`.
- Si falla, mantener `PENDING` e incrementar `retry_count`.
- Scheduler reintenta eventos `PENDING`.

Criterios de aceptacion:

- Si NATS cae, `POST /api/routes/{id}/complete` responde exitosamente.
- El evento queda pendiente en outbox.
- Scheduler puede reintentarlo.

## Fase 9 - REST API

Objetivo: mantener contratos publicados.

Endpoints obligatorios:

- `POST /api/orders`
- `GET /api/orders/{id}`
- `POST /api/orders/{id}/assign-robot`
- `POST /api/orders/{id}/route-plan`
- `POST /api/routes/{id}/complete`
- `GET /api/inventory/items/{sku}/spots`
- `GET /api/warehouse/graph`

Codigos esperados:

- `201` orden creada.
- `200` operacion exitosa.
- `400` request invalido.
- `404` recurso no encontrado.
- `409` conflicto de negocio.

Criterios de aceptacion:

- Controladores no tienen reglas de negocio.
- Errores de dominio se traducen en `GlobalExceptionHandler`.

## Fase 10 - Integracion Con MS-RobotStatus

Objetivo: validar robot de forma sincrona.

Contrato esperado:

```http
GET /api/robots/{robotId}/status
```

Respuesta esperada:

```json
{
  "robotId": "RBT-01",
  "batteryLevel": 80,
  "available": true,
  "currentLocation": "RP-START",
  "operationalMode": "AUTO"
}
```

Reglas:

- `batteryLevel < 15` devuelve `409`.
- `available = false` devuelve `409`.
- timeout o caida de RobotStatus no asigna robot.

Criterios de aceptacion:

- Warehouse no asigna robot si RobotStatus falla.
- Warehouse no crea asignaciones huerfanas.

## Fase 11 - Tests

Objetivo: validar sin Docker local y permitir validacion completa en equipo/CI.

Tests sin Docker:

- `RobotAssignmentPolicyTest`
- `FragileProductPolicyTest`
- `DispatchOrderTest`
- `DijkstraRouteCalculatorTest`
- `WarehouseApplicationServiceTest` con mocks
- test de completar ruta con NATS fallando y outbox pendiente

Tests con Docker/Testcontainers, si se ejecutan en otra maquina:

- migraciones Flyway contra PostgreSQL
- repositorios JPA
- flujo crear orden, reservar, planear ruta y completar

Criterios de aceptacion:

- `./mvnw test` pasa sin Docker.
- Tests de integracion pueden quedar en perfil separado si requieren Docker.

## Fase 12 - README Del Microservicio

Objetivo: que tus companeros sepan correr e integrar Warehouse.

Crear `smartlogistics/ms-warehouse-core/README.md` con:

- Responsabilidad del microservicio.
- Arquitectura.
- Endpoints.
- Variables de entorno.
- Contrato RobotStatus.
- Evento `route.completed`.
- Comandos de test.
- Comandos Docker para maquina con recursos.
- Queries de verificacion DB.

Queries esperadas:

```sql
SELECT COUNT(*) FROM inventory_item; -- 5
SELECT COUNT(*) FROM spot; -- 5
SELECT COUNT(*) FROM spot_item; -- 10
SELECT COUNT(*) FROM root_point; -- 10
SELECT COUNT(*) FROM route_edge; -- 15
```

## Fase 13 - Validacion Final

Sin Docker:

```bash
cd smartlogistics/ms-warehouse-core
./mvnw test
```

Windows:

```bash
cd smartlogistics/ms-warehouse-core
.\mvnw.cmd test
```

Con Docker en equipo de companeros:

```bash
cd smartlogistics
docker compose up --build warehouse-db nats ms-robot-status ms-warehouse-core
```

Endpoints a validar:

- `GET http://localhost:8081/actuator/health`
- `POST http://localhost:8081/api/orders`
- `GET http://localhost:8081/api/orders/{id}`
- `POST http://localhost:8081/api/orders/{id}/assign-robot`
- `POST http://localhost:8081/api/orders/{id}/route-plan`
- `POST http://localhost:8081/api/routes/{id}/complete`
- `GET http://localhost:8081/api/inventory/items/SKU-ELEC-001/spots`
- `GET http://localhost:8081/api/warehouse/graph`

## Orden De Implementacion

1. Maven Wrapper.
2. Dependencias JPA + Flyway.
3. Migraciones `V1` y `V2`.
4. Entidades JPA.
5. Repositorios Spring Data JPA.
6. Mapper JPA/dominio.
7. Adaptador PostgreSQL JPA.
8. Reserva de stock.
9. Speed factor para fragiles.
10. Evento `route.completed` estricto.
11. Outbox con reintentos.
12. Tests unitarios y de aplicacion.
13. README.
14. `./mvnw test`.

## Definicion De Terminado

`MS-WarehouseCore` esta terminado cuando:

- Compila con Maven Wrapper.
- Usa arquitectura hexagonal real.
- Usa JPA en infraestructura.
- Flyway crea schema y seed.
- Crea ordenes.
- Asigna robots validando bateria.
- Rechaza bateria menor a 15%.
- Reserva stock al asignar.
- Calcula rutas con Dijkstra.
- Aplica politica de fragiles.
- Completa rutas descontando stock.
- Guarda evento en outbox antes de publicar.
- Publica `route.completed` en NATS.
- Expone Actuator/Prometheus.
- Tests pasan sin Docker local.
