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
  - `ms-identity/src/main/java/.../infrastructure/adapter/in/rest/AuthController.java`

### Task A.5.6 — Refactorizar MS-Identity a arquitectura hexagonal purista

- **Contexto**: Las tareas A.5.1-A.5.5 se implementaron inicialmente con anotaciones de framework en el modelo y sin separación de capas. Esta tarea refactoriza el código existente para cumplir la **Regla Hexagonal Purista** (domain/ y application/ con zero imports de framework).

- **Acceptance**:
  - **domain/**:
    - `model/User.java` → POJO puro sin JPA: `String id, String username, String passwordHash, Role role`
    - `model/Role.java` → enum (OPERATOR, ADMIN)
    - `exception/UserAlreadyExistsException.java` → excepción de dominio
  - **application/**:
    - `port/in/RegisterUseCase.java` → interfaz pura
    - `port/in/LoginUseCase.java` → interfaz pura
    - `port/in/ValidateTokenUseCase.java` → interfaz pura
    - `port/out/UserRepositoryPort.java` → interfaz pura (save, findByUsername, existsByUsername)
    - `port/out/TokenServicePort.java` → interfaz pura (generate, validate)
    - `service/AuthApplicationService.java` → orquesta puertos, cero imports framework
  - **infrastructure/**:
    - `adapter/in/rest/AuthController.java` → @RestController que inyecta puertos de entrada
    - `adapter/out/jpa/UserEntity.java` → @Entity aquí (no en domain)
    - `adapter/out/jpa/JpaUserRepository.java` → extends JpaRepository, implements UserRepositoryPort
    - `adapter/out/jpa/UserMapper.java` → UserEntity ↔ User
    - `adapter/out/jwt/JwtTokenServiceAdapter.java` → implementa TokenServicePort con JJWT + BCrypt
    - `config/JwtConfig.java` → propiedades JWT
    - `config/BeanConfig.java` → beans de aplicación
  - **domain/** y **application/** no contienen ningún `import` de: `org.springframework`, `jakarta`, `io.jsonwebtoken`, ni anotaciones de framework
- **Verify**:
  ```bash
  cd ms-identity
  mvn clean compile  # compila sin errores
  mvn test          # tests existentes siguen pasando
  # Verificar pureza del dominio (grep por imports prohibidos en domain/ y application/)
  powershell -Command "Get-ChildItem -Recurse -Include '*.java' src/main/java/com/smartlogistics/identity/domain,src/main/java/com/smartlogistics/identity/application | Select-String 'import org.springframework|import jakarta|import io.jsonwebtoken'"
  ```
- **Files**:
  - `ms-identity/src/main/java/.../domain/model/User.java`
  - `ms-identity/src/main/java/.../domain/model/Role.java`
  - `ms-identity/src/main/java/.../domain/exception/UserAlreadyExistsException.java`
  - `ms-identity/src/main/java/.../application/port/in/RegisterUseCase.java`
  - `ms-identity/src/main/java/.../application/port/in/LoginUseCase.java`
  - `ms-identity/src/main/java/.../application/port/in/ValidateTokenUseCase.java`
  - `ms-identity/src/main/java/.../application/port/out/UserRepositoryPort.java`
  - `ms-identity/src/main/java/.../application/port/out/TokenServicePort.java`
  - `ms-identity/src/main/java/.../application/service/AuthApplicationService.java`
  - `ms-identity/src/main/java/.../infrastructure/adapter/in/rest/AuthController.java`
  - `ms-identity/src/main/java/.../infrastructure/adapter/out/jpa/UserEntity.java`
  - `ms-identity/src/main/java/.../infrastructure/adapter/out/jpa/JpaUserRepository.java`
  - `ms-identity/src/main/java/.../infrastructure/adapter/out/jpa/UserMapper.java`
  - `ms-identity/src/main/java/.../infrastructure/adapter/out/jwt/JwtTokenServiceAdapter.java`
  - `ms-identity/src/main/java/.../infrastructure/config/JwtConfig.java`
  - `ms-identity/src/main/java/.../infrastructure/config/BeanConfig.java`

---

## Fase B: MS-RobotStatus

### Task B.1 — Inicializar proyecto Spring Boot con estructura hexagonal

- **Acceptance**: `mvn clean compile` sin errores. Paquetes domain/, application/, infrastructure/ creados. Dependencias: web, data-redis, actuator, prometheus
- **Verify**: `mvn test` pasa (al menos el test de contexto de Spring)
- **Files**:
  - `smartlogistics/ms-robot-status/pom.xml`
  - `smartlogistics/ms-robot-status/Dockerfile`

### Task B.2 — Implementar capa de dominio (modelo puro)

- **Acceptance**: `Robot` POJO puro (id, batteryLevel, available, currentLocation). `RobotStatus` value object. Sin imports de framework.
- **Verify**: Test unitario: crear Robot y verificar sus propiedades
- **Files**:
  - `ms-robot-status/src/main/java/.../domain/model/Robot.java`
  - `ms-robot-status/src/main/java/.../domain/model/RobotStatus.java`
  - `ms-robot-status/src/main/java/.../domain/exception/RobotNotFoundException.java`

### Task B.3 — Implementar puertos de aplicación y adaptador Redis

- **Acceptance**: `RobotCachePort` (puerto de salida). `RedisRobotAdapter` implementa RobotCachePort con RedisRepository. `GetRobotStatusUseCase` y `UpdateBatteryUseCase` como puertos de entrada.
- **Verify**: Test unitario: `redisRobotAdapter.findById("RBT-01")` retorna Optional con Robot
- **Files**:
  - `ms-robot-status/src/main/java/.../application/port/in/GetRobotStatusUseCase.java`
  - `ms-robot-status/src/main/java/.../application/port/in/UpdateBatteryUseCase.java`
  - `ms-robot-status/src/main/java/.../application/port/out/RobotCachePort.java`
  - `ms-robot-status/src/main/java/.../application/service/RobotStatusService.java`
  - `ms-robot-status/src/main/java/.../infrastructure/adapter/out/redis/RedisRobotAdapter.java`

### Task B.4 — Implementar controlador REST (adaptador entrada)

- **Acceptance**:
  - `GET /api/robots/{id}/status` → 200 con estado del robot
  - `GET /api/robots/ROBOT-INEXISTENTE/status` → 404
  - `POST /api/robots` → 201, crea/actualiza robot
- **Verify**: `curl localhost:8082/api/robots/RBT-01/status` → 200 JSON
- **Files**:
  - `ms-robot-status/src/main/java/.../infrastructure/adapter/in/rest/RobotStatusController.java`

### Task B.5 — Sembrar datos de robots en Redis al iniciar

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

### Task D.1 — Inicializar proyecto Spring Boot con estructura hexagonal

- **Acceptance**: Paquetes domain/, application/, infrastructure/ creados. Dependencias: web, mongodb, amqp, actuator, prometheus
- **Verify**: `mvn test` pasa
- **Files**:
  - `smartlogistic/ms-logistics-analytics/pom.xml`
  - `smartlogistic/ms-logistics-analytics/Dockerfile`

### Task D.2 — Implementar capa de dominio

- **Acceptance**: `RouteEvent` POJO puro (eventId, orderId, robotId, path, distance, duration, timestamp). `CongestionSample` POJO puro. `EventProcessingException`. Sin imports de framework.
- **Verify**: Test unitario: crear RouteEvent y verificar propiedades
- **Files**:
  - `ms-logistics-analytics/src/main/java/.../domain/model/RouteEvent.java`
  - `ms-logistics-analytics/src/main/java/.../domain/model/CongestionSample.java`
  - `ms-logistics-analytics/src/main/java/.../domain/exception/EventProcessingException.java`

### Task D.3 — Definir puertos de aplicación

- **Acceptance**: `ProcessRouteEventUseCase` (puerto entrada). `AnalyticsRepositoryPort` (puerto salida).
- **Verify**: Interfaces compilan sin errores
- **Files**:
  - `ms-logistics-analytics/src/main/java/.../application/port/in/ProcessRouteEventUseCase.java`
  - `ms-logistics-analytics/src/main/java/.../application/port/out/AnalyticsRepositoryPort.java`

### Task D.4 — Implementar servicio de aplicación

- **Acceptance**: `AnalyticsService` implementa ProcessRouteEventUseCase, orquesta AnalyticsRepositoryPort. Sin imports de framework.
- **Verify**: Test unitario con AnalyticsRepositoryPort mockeado
- **Files**:
  - `ms-logistics-analytics/src/main/java/.../application/service/AnalyticsService.java`

### Task D.5 — Implementar adaptador RabbitMQ (consumer)

- **Acceptance**: `RouteEventConsumer` con @RabbitListener(queues = "route.completed.q"). Deserializa JSON a RouteEvent y llama a ProcessRouteEventUseCase. Exchange `logistics.exchange` (topic) + queue `route.completed.q` + binding con routing key `route.completed` declarados en `AnalyticsConfig`.
- **Verify**: Completar ruta → evento aparece en MongoDB
- **Files**:
  - `ms-logistics-analytics/src/main/java/.../infrastructure/adapter/in/amqp/RouteEventConsumer.java`

### Task D.6 — Implementar adaptador MongoDB (persistencia)

- **Acceptance**: `MongoRouteEventAdapter` implementa AnalyticsRepositoryPort usando MongoRepository para persistir RouteEvent en colección `route_events`
- **Verify**: Test de integración inserta y consulta en MongoDB
- **Files**:
  - `ms-logistics-analytics/src/main/java/.../infrastructure/adapter/out/mongodb/MongoRouteEventAdapter.java`
  - `ms-logistics-analytics/src/main/java/.../infrastructure/config/AnalyticsConfig.java`

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
