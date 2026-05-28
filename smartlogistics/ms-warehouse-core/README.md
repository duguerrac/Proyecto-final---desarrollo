# MS Warehouse Core

Microservicio central de bodega para SmartLogistics. Gestiona ordenes de despacho, inventario por spot, asignacion de robots, calculo de rutas y publicacion del evento `route.completed`.

## Stack

- Java 21
- Spring Boot 3
- Maven Wrapper
- Spring Data JPA
- PostgreSQL
- Flyway
- NATS

## Ejecutar Localmente

```bash
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Por defecto usa:

- API: `http://localhost:8081`
- PostgreSQL: `jdbc:postgresql://localhost:5432/warehouse_db`
- Robot Status: `http://localhost:8082`
- NATS: `nats://localhost:4222`

## Variables

- `SERVER_PORT`: puerto HTTP, default `8081`
- `SPRING_DATASOURCE_URL`: URL JDBC de PostgreSQL
- `SPRING_DATASOURCE_USERNAME`: usuario de PostgreSQL
- `SPRING_DATASOURCE_PASSWORD`: password de PostgreSQL
- `ROBOT_STATUS_URL`: base URL de `ms-robot-status`
- `ROBOT_STATUS_TIMEOUT_MS`: timeout del cliente Robot Status
- `NATS_URL`: URL de NATS
- `WAREHOUSE_ID`: identificador de bodega
- `WAREHOUSE_START_ROOT_POINT`: root point inicial, default `RP-START`
- `WAREHOUSE_EXIT_ROOT_POINT`: root point de salida, default `RP-EXIT`

## Endpoints

- `POST /api/orders`: crea orden de despacho
- `GET /api/orders/{id}`: consulta orden
- `POST /api/orders/{id}/assign-robot`: valida robot y reserva stock
- `POST /api/orders/{id}/route-plan`: calcula ruta y crea plan
- `POST /api/routes/{id}/complete`: completa ruta, descuenta stock y publica evento
- `GET /api/inventory/items/{sku}/spots`: consulta disponibilidad por spot
- `GET /api/warehouse/graph`: consulta grafo de bodega

## Reglas De Negocio

- El stock se reserva al asignar robot.
- El stock se descuenta al completar ruta.
- La disponibilidad se calcula como `quantity_available - quantity_reserved`.
- Las rutas usan los puntos con stock ya reservado para la orden.
- Las ordenes con productos fragiles usan `speedFactor = 0.50`; las demas usan `1.00`.

## Evento `route.completed`

Se publica en NATS usando el subject `route.completed`.

Campos principales:

- `eventId`
- `eventType`
- `occurredAt`
- `orderId`
- `robotId`
- `warehouseId`
- `fragileItems`
- `totalDistanceMeters`
- `durationSeconds`
- `path[{rootPointId,x,y}]`

El `path` respeta el orden de `route_step.sequence` y `rootPointId` contiene el codigo del root point, por ejemplo `RP-START`.

## Outbox

- Al completar una ruta se guarda primero un evento `PENDING`.
- Si NATS publica correctamente, el evento pasa a `PUBLISHED`.
- Si NATS falla, el evento queda `PENDING`, incrementa `retry_count` y puede ser reintentado por el scheduler.

## Migraciones

Flyway crea y carga el esquema inicial desde:

- `src/main/resources/db/migration/V1__create_warehouse_schema.sql`
- `src/main/resources/db/migration/V2__seed_warehouse_data.sql`
