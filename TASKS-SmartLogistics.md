# TASKS: SmartLogistics Backend Implementation

> Cada tarea es completable en una sesión enfocada. Ordenadas por dependencia.

---

## Fase A: Foundation

### Task A.1 — Crear docker-compose.yml con servicios base

- **Acceptance**: `docker compose up` levanta PostgreSQL (warehouse-db + auth-db), Redis, MongoDB y RabbitMQ sin errores
- **Verify**: `docker compose ps` → 6 servicios "running"
- **Files**:
  - `smartlogistic/docker-compose.yml`
  - `smartlogistic/.env.example`

### Task A.2 — Crear script de datos semilla para PostgreSQL

- **Acceptance**: Script SQL crea tablas e inserta: 5 inventory_items, 5 spots, 10 spot_items, 10 root_points, 15 route_edges
- **Verify**: Ejecutar contra PostgreSQL local y verificar `SELECT COUNT(*)` en cada tabla
- **Files**:
  - `smartlogistics/scripts/seed-warehouse.sql`

### Task A.3 — Crear demo-flow.http para pruebas manuales

- **Acceptance**: Archivo HTTP client con requests para todo el flujo: register, login, crear orden, asignar robot, calcular ruta, completar ruta
- **Verify**: Abrir en VS Code con REST Client extension y probar cada request
- **Files**:
  - `smartlogistics/scripts/demo-flow.http`

---

## Fase A.5: MS-Identity (Auth con JWT)

### Task A.5.1 — Agregar auth-db al docker-compose.yml

- **Acceptance**: `docker compose up` levanta PostgreSQL auth-db en puerto 5433
- **Verify**: `curl localhost:5433` no existe, pero `docker compose ps` muestra auth-db running
- **Files**:
  - `smartlogistics/docker-compose.yml` (agregar servicio auth-db)

### Task A.5.2 — Inicializar proyecto Spring Boot ms-identity

- **Acceptance**: `mvn clean compile` sin errores. Dependencias: web, data-jpa, postgresql, jjwt, spring-security-crypto, actuator, prometheus
- **Verify**: `mvn test` pasa
- **Files**:
  - `smartlogistic/ms-identity/pom.xml`
  - `smartlogistic/ms-identity/Dockerfile`

### Task A.5.3 — Implementar User entity y repositorio

- **Acceptance**: Entidad `User` con id, username, password_hash, role. `UserRepository` con findByUsername
- **Verify**: Test de integración: inserta usuario en H2/PostgreSQL, consulta por username
- **Files**:
  - `ms-identity/src/main/java/.../model/User.java`
  - `ms-identity/src/main/java/.../repository/UserRepository.java`

### Task A.5.4 — Implementar AuthService y JwtService

- **Acceptance**:
  - `register()`: hashea password con BCrypt, persiste usuario, retorna User
  - `login()`: valida password contra hash, genera JWT con HMAC-SHA256 (claims: username, role, exp 24h)
  - `validateToken()`: verifica firma, expiración, retorna username si válido
- **Verify**: Tests unitarios:
  - register → usuario creado con password hasheado (no texto plano)
  - login con credenciales correctas → JWT válido
  - login con password incorrecto → excepción
  - validateToken con JWT válido → username
  - validateToken con JWT expirado → excepción
- **Files**:
  - `ms-identity/src/main/java/.../service/AuthService.java`
  - `ms-identity/src/main/java/.../service/JwtService.java`
  - `ms-identity/src/main/java/.../config/JwtConfig.java`
  - `ms-identity/src/main/java/.../dto/LoginRequest.java`
  - `ms-identity/src/main/java/.../dto/RegisterRequest.java`
  - `ms-identity/src/main/java/.../dto/AuthResponse.java`

### Task A.5.5 — Implementar AuthController

- **Acceptance**:
  - `POST /api/auth/register` → 201 + usuario creado
  - `POST /api/auth/login` → 200 + { "token": "eyJ..." }
  - `POST /api/auth/validate` → 200 + header X-Auth-User si token válido
  - `POST /api/auth/validate` → 401 si token inválido/expirado
- **Verify**:
  ```bash
  curl -X POST localhost:8084/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"test\",\"password\":\"pass\",\"role\":\"OPERATOR\"}" → 201
  curl -X POST localhost:8084/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"test\",\"password\":\"pass\"}" → 200 + token
  curl -X POST localhost:8084/api/auth/validate -H "Authorization: Bearer <token>" → 200
  curl -X POST localhost:8084/api/auth/validate -H "Authorization: Bearer INVALIDO" → 401
  ```
- **Files**:
  - `ms-identity/src/main/java/.../controller/AuthController.java`

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

### Task C.8 — Implementar adaptador RabbitMQ (event publisher) + Outbox

- **Acceptance**: `RabbitRouteEventPublisher` publica evento `route.completed` al exchange `logistics.exchange` con routing key `route.completed`
- **Acceptance**: Outbox pattern: evento se persiste en `outbox_event`, scheduler reintenta publicación si RabbitMQ falla
- **Verify**: Completar ruta, verificar evento en tabla outbox con status PUBLISHED
- **Files**:
  - `ms-warehouse-core/src/main/java/.../infrastructure/adapter/out/broker/RabbitRouteEventPublisher.java`
  - `ms-warehouse-core/src/main/java/.../infrastructure/config/RabbitConfig.java`

---

## Fase D: MS-LogisticsAnalytics

### Task D.1 — Inicializar proyecto Spring Boot

- **Acceptance**: Dependencias: web, mongodb, amqp, actuator, prometheus
- **Verify**: `mvn test` pasa
- **Files**:
  - `smartlogistic/ms-logistics-analytics/pom.xml`
  - `smartlogistic/ms-logistics-analytics/Dockerfile`

### Task D.2 — Implementar consumer RabbitMQ y persistencia MongoDB

- **Acceptance**: Consumer `@RabbitListener(queues = "route.completed.q")`. Exchange `logistics.exchange` (topic) + queue `route.completed.q` + binding declarados en `RabbitConfig`. Deserializa JSON a `RouteEvent` document. Persiste en MongoDB colección `route_events`
- **Verify**: Completar ruta → evento aparece en MongoDB
- **Files**:
  - `ms-logistics-analytics/src/main/java/.../consumer/RouteEventConsumer.java`
  - `ms-logistics-analytics/src/main/java/.../config/RabbitConfig.java`
  - `ms-logistics-analytics/src/main/java/.../model/RouteEvent.java`
  - `ms-logistics-analytics/src/main/java/.../repository/RouteEventRepository.java`
  - `ms-logistics-analytics/src/main/java/.../service/AnalyticsService.java`

---

## Fase E: Nginx Gateway

### Task E.1 — Configurar Nginx como reverse proxy con auth_request JWT

- **Acceptance**: Nginx en puerto 8080.
  - `/api/auth/` → proxy_pass directo a `ms-identity:8084` (sin auth)
  - `/api/` → auth_request a `/api/auth/validate` + proxy_pass a `ms-warehouse-core:8081`
  - `=/api/auth/validate` → endpoint interno (internal), proxy_pass a `ms-identity:8084/api/auth/validate`
  - Request sin JWT → 401
  - Request con JWT válido → pasa a WarehouseCore
- **Verify**:
  ```bash
  curl localhost:8080/api/orders → 401
  TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"operador","password":"123456"}' | jq -r '.token')
  curl localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" → 200
  ```
- **Files**:
  - `smartlogistics/nginx/nginx.conf`
  - `smartlogistics/nginx/Dockerfile`

### Task E.2 — Integrar Nginx en docker-compose.yml

- **Acceptance**: `docker compose up` incluye nginx. Red interna conecta nginx → ms-identity y warehouse-core
- **Verify**: `docker compose ps` → nginx running
- **Files**:
  - `smartlogistics/docker-compose.yml` (actualizar)

---

## Fase F: Observabilidad

### Task F.1 — Configurar Prometheus

- **Acceptance**: Prometheus scrapea targets: identity:8084, warehouse-core:8081, robot-status:8082, analytics:8083
- **Verify**: `curl localhost:9090/api/v1/targets` → todos UP
- **Files**:
  - `smartlogistic/observability/prometheus.yml`

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
  - `smartlogistic/observability/grafana/provisioning/datasources/prometheus.yml`
  - `smartlogistic/observability/grafana/provisioning/datasources/loki.yml`
  - `smartlogistic/observability/grafana/provisioning/dashboards/smartlogistics.json`

### Task F.4 — Agregar métricas Actuator en cada MS

- **Acceptance**: Cada microservicio expone `/actuator/prometheus` con métricas HTTP
- **Verify**: `curl localhost:8081/actuator/prometheus` → métricas visibles
- **Files**:
  - `ms-identity/pom.xml` (agregar actuator + micrometer)
  - `ms-warehouse-core/pom.xml` (agregar actuator + micrometer)
  - `ms-robot-status/pom.xml`
  - `ms-logistics-analytics/pom.xml`
  - `application.properties` en cada MS

### Task F.5 — Configurar Jaeger para trazabilidad distribuida

- **Acceptance**: Servicio `jaeger` en docker-compose (image: `jaegertracing/all-in-one`, puertos 16686 + 4318). Cada MS envía trazas via OpenTelemetry OTLP
- **Verify**:
  ```bash
  docker compose up
  curl localhost:16686/api/services → lista "smartlogistic-*" services
  ```
  Abrir `http://localhost:16686` → buscar traza del flujo orden → asignar robot → completar ruta
- **Files**:
  - `smartlogistic/docker-compose.yml` (agregar jaeger service)
  - `pom.xml` de cada MS (agregar micrometer-tracing-bridge-otel, opentelemetry-exporter-otlp)
  - `application.properties` de cada MS (agregar tracing config)

---

## Bonus (si sobra tiempo)

### Task G.1 — Pruebas de carga con k6 o vegeta

### Task G.2 — Endpoints simulados de ERP vía RabbitMQ consumer adicional
