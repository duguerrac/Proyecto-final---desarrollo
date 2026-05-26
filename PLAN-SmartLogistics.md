# Plan: SmartLogistics Backend Implementation

## Dependencias entre Componentes

```
Phase A: Foundation (Docker Compose + DBs + NATS)
    └── Fase base, nada depende de ella
            │
Phase B: MS-RobotStatus (Redis + REST)
    └── Depende de: Phase A (Redis, red Docker)
    └── Es independiente, puede ejecutarse en paralelo con C
            │
Phase C: MS-WarehouseCore (Hexagonal + PostgreSQL) ◄── sync ── Phase B
    └── Depende de: Phase A (PostgreSQL, NATS), Phase B (consulta REST)
    └── Puede empezar con RobotStatusPort mockeado mientras B no esté listo
            │
Phase D: MS-LogisticsAnalytics (NATS + MongoDB) ◄── event ── Phase C
    └── Depende de: Phase A (MongoDB, NATS)
    └── Depende del schema del evento route.completed (definido en C)
            │
Phase E: Nginx Gateway ──── proxy to ──── Phase C
    └── Depende de: Phase C (API expuesta)
            │
Phase F: Observabilidad ──── all services running ────
    └── Depende de: Todas las fases anteriores activas
```

## Fases de Implementación

### Fase A: Foundation (Corto — 1 sesión)
**Responsable:** Integrante 2

**Entregables:**
- `docker-compose.yml` con servicios base:
  - PostgreSQL (warehouse-db, puerto 5432)
  - Redis (robot-redis, puerto 6379)
  - MongoDB (analytics-db, puerto 27017)
  - NATS (nats, puerto 4222)
- Volúmenes persistentes para cada DB
- Red compartida para todos los servicios
- Archivo `.env.example` con variables de entorno
- Script `scripts/seed-warehouse.sql` con datos semilla

**Verificación:**
```
docker compose up
docker compose ps  # Todos los servicios "running"
```

---

### Fase B: MS-RobotStatus (Medio — 1-2 sesiones)
**Responsable:** Integrante 3

**Entregables:**
- Proyecto Spring Boot con:
  - `Robot` entity (id, name, batteryLevel, available, currentLocation, operationalMode)
  - `RobotStatusController` (GET /api/robots/{id}/status, POST /api/robots)
  - `RobotStatusService` con lógica de simulación
  - Redis repository para cache de estado
- Dockerfile
- Pom.xml con dependencias: spring-boot-starter-web, spring-boot-starter-data-redis, prometheus actuator
- Estados simulados: 5 robots con batería variable (2 con < 15%, 3 con >= 15%)

**Verificación:**
```bash
curl http://localhost:8082/api/robots/RBT-01/status → 200, batteryLevel: valor
curl http://localhost:8082/api/robots/RBT-LOW/status → 200, batteryLevel: 10
```

---

### Fase C: MS-WarehouseCore (Largo — 3-4 sesiones)
**Responsable:** Integrante 1

**Entregables:**

**Capa Domain:**
- `DispatchOrder`, `InventoryItem`, `Spot`, `SpotItem`, `RootPoint`, `RouteEdge`, `RoutePlan`, `RouteStep` — entidades puras sin framework
- `RobotAssignmentPolicy` — valida batteryLevel >= 15, reject si no cumple
- `FragileProductPolicy` — reduce speedLimit si item fragile

**Capa Application:**
- Puertos de entrada: `CreateDispatchOrderUseCase`, `AssignRobotUseCase`, `CompleteRouteUseCase`
- Puertos de salida: `WarehouseRepositoryPort`, `RobotStatusPort`, `RouteEventPublisherPort`
- Servicios: `DispatchOrderService`, `RobotAssignmentService`, `RoutePlanningService` (Dijkstra)

**Capa Infrastructure:**
- `OrderController`, `RouteController` (REST endpoints según spec)
- JPA repositories (`DispatchOrderJpaRepository`, `InventoryItemJpaRepository`)
- `RobotStatusClient` (Feign/WebClient a MS-RobotStatus)
- `NatsRouteEventPublisher` (publica route.completed a NATS)
- Outbox pattern: `OutboxEvent` entity + scheduler que publica eventos pendientes

**Dockerfile + pom.xml**

**Verificación:**
```bash
# Crear orden
curl -X POST http://localhost:8081/api/orders -H "Content-Type: application/json" -d "{\"items\":[{\"sku\":\"SKU-001\",\"quantity\":2}]}"

# Asignar robot con batería suficiente
curl -X POST http://localhost:8081/api/orders/ORD-001/assign-robot -H "Content-Type: application/json" -d "{\"robotId\":\"RBT-01\"}" → 200

# Asignar robot con batería baja
curl -X POST http://localhost:8081/api/orders/ORD-001/assign-robot -H "Content-Type: application/json" -d "{\"robotId\":\"RBT-LOW\"}" → 409
```

---

### Fase D: MS-LogisticsAnalytics (Medio — 1-2 sesiones)
**Responsable:** Integrante 2

**Entregables:**
- Proyecto Spring Boot con:
  - `NatsRouteEventConsumer` — suscriptor a topic `route.completed`
  - `RouteEvent` document (MongoDB) con eventId, orderId, robotId, path, distance, duration, timestamp
  - `RouteEventRepository` (MongoRepository)
  - `AnalyticsService` — procesa y persiste eventos
- Dockerfile + pom.xml

**Verificación:**
```bash
# Completar ruta en WarehouseCore
curl -X POST http://localhost:8081/api/routes/RTP-001/complete

# Verificar evento en MongoDB (vía Mongo Express o CLI)
docker exec -it mongodb mongosh logistics_analytics --eval "db.route_events.find()"
```

---

### Fase E: Nginx Gateway (Corto — 1 sesión)
**Responsable:** Integrante 3

**Entregables:**
- `nginx/nginx.conf`:
  - Server en puerto 80
  - Location `/api/` → proxy_pass a ms-warehouse-core:8081
  - CORS headers para desarrollo
  - Timeouts configurados
- `nginx/Dockerfile` (imagen nginx:1.27-alpine con config)
- Actualización de `docker-compose.yml` para incluir nginx

**Verificación:**
```bash
curl http://localhost:8080/api/orders → 200 (misma respuesta que directo a :8081)
```

---

### Fase F: Observabilidad (Medio — 1-2 sesiones)
**Responsable:** Integrante 2

**Entregables:**
- `observability/prometheus.yml`:
  - Scrape targets: warehouse-core:8081, robot-status:8082, analytics:8083
  - Scrape interval: 15s
- `observability/loki-config.yml`:
  - Loki en modo simple sin autenticación
- `observability/grafana/provisioning/datasources/`:
  - Prometheus datasource
  - Loki datasource
- `observability/grafana/provisioning/dashboards/`:
  - Dashboard con: HTTP request rate, latency p99, battery rejections, routes completed
- Configuración de `management.endpoints.web.exposure.include=prometheus,health` en cada MS

**Verificación:**
```bash
curl localhost:9090/api/v1/targets → targets UP
Abrir http://localhost:3000 → Grafana, datasources configurados
```

---

## Asignación por Integrante

| Integrante | Fases | Qué sustenta |
|---|---|---|
| **Integrante 1** | C (WarehouseCore) | Hexagonal, dominio, políticas de negocio, Dijkstra, outbox |
| **Integrante 2** | A, D, F | Docker Compose, NATS, MongoDB, Prometheus/Grafana/Loki |
| **Integrante 3** | B, E | REST con Redis, Nginx, documentación APIs |

## Riesgos y Mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| Arquitectura hexagonal queda en solo carpetas | Alta | Crítico | Code review forzado: dominio no importa frameworks |
| Eventos NATS no llegan a Analytics | Media | Alto | Outbox pattern + verificación manual con NATS CLI |
| Dijkstra sin datos semilla no se puede probar | Media | Alto | Script seed-warehouse.sql incluido desde Fase A |
| Tiempo insuficiente para observabilidad | Media | Medio | Desde Fase A agregar actuator/prometheus en cada pom.xml |
| Integración entre 3 personas conflictiva | Alta | Alto | Contratos API congelados en spec; demo-flow.http como prueba única |
| MS-RobotStatus no responde y WarehouseCore falla | Media | Medio | Timeouts cortos + fallar con 409 (no asignar) ante timeout |
