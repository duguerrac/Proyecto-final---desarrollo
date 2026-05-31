# 🔄 Plan de Migración: NATS → RabbitMQ (Opción B)

> **Rama:** `danielzr30/robotstatus` → integrar con `origin/main`
> **Objetivo:** Eliminar NATS completamente y unificar toda la mensajería en RabbitMQ
> **Justificación:** El profesor exige RabbitMQ o Kafka como broker de mensajería

---

## 📊 Resumen de Alcance

| Componente | Cambio | Dificultad | Prioridad |
|---|---|---|---|
| `docker-compose.yml` | Eliminar NATS, mantener RabbitMQ de main | 🟢 Baja | P0 |
| `ms-warehouse-core` | Ya migrado en `origin/main` | ✅ Hecho | — |
| `ms-robot-status` (Java) | Migrar 6 adapters NATS → RabbitMQ | 🟡 Media | P0 |
| `ms-robot-status` (pom.xml) | Agregar spring-amqp dependency | 🟢 Baja | P0 |
| `Simulation` (Unreal C++) | Reescribir `NatsWebSocketClient` → `AmqpClient` | 🔴 Alta | P1 |
| `nginx.conf` | Adoptar versión mejorada de main | 🟢 Baja | P2 |
| Observabilidad | Adoptar dashboards y configs de main | 🟢 Baja | P2 |
| `nats/` config folder | Eliminar completamente | 🟢 Baja | P0 |

---

## Fase 0: Preparación — Merge de `origin/main`

### 0.1 Actualizar rama local con main
```bash
git fetch origin
git checkout danielzr30/robotstatus
git merge origin/main
```
> ⚠️ Habrá conflictos en `docker-compose.yml`, `ms-warehouse-core/` y `ms-robot-status/`. Resolver manteniendo la lógica de tu rama pero adoptando RabbitMQ de main.

### 0.2 Verificar que el proyecto compila después del merge
- [ ] `ms-warehouse-core` compila (ya usa RabbitMQ en main)
- [ ] `ms-identity` compila
- [ ] `ms-robot-status` — NO compilará aún (depende de NATS que ya no existe)
- [ ] `docker-compose.yml` tiene RabbitMQ

---

## Fase 1: Infraestructura — Eliminar NATS

### 1.1 `docker-compose.yml`
- [ ] Eliminar servicio `nats:` completo (lines 86-98)
- [ ] Eliminar `NATS_URL`, `NATS_PORT`, `NATS_MONITOR_PORT`, `NATS_WS_PORT` de variables
- [ ] Eliminar `nats` de `depends_on` en `ms-warehouse-core` y `ms-robot-status`
- [ ] Agregar `SPRING_RABBITMQ_HOST: rabbitmq` y `SPRING_RABBITMQ_PORT: 5672` a `ms-robot-status`
- [ ] Agregar `rabbitmq` a `depends_on` de `ms-robot-status`
- [ ] Eliminar volumen de `rabbitmq_data` si no está (ya está en main)
- [ ] Habilitar `ms-logistics-analytics` (tomar de main)
- [ ] Adoptar configs mejoradas de Jaeger, Grafana, Prometheus de main

### 1.2 Eliminar carpeta `smartlogistic/nats/`
- [ ] Borrar `smartlogistic/nats/nats.conf`

### 1.3 Adoptar nginx.conf de main
- [ ] Reemplazar `smartlogistic/nginx/nginx.conf` con versión de `origin/main`
- [ ] Actualizar puerto de nginx (8080 → 8086) si main lo cambió

### 1.4 Adoptar observabilidad de main
- [ ] Copiar `smartlogistic/observability/grafana/provisioning/dashboards/*.json` (5 dashboards)
- [ ] Copiar `smartlogistic/observability/grafana/provisioning/datasources/*.yml` (jaeger, loki, prometheus)
- [ ] Copiar `smartlogistic/observability/jaeger-config.yml`
- [ ] Actualizar `smartlogistic/observability/prometheus.yml` con versión de main

---

## Fase 2: ms-robot-status — Migración Java (RabbitMQ)

### 2.1 Actualizar `pom.xml`
- [ ] Agregar dependencia `spring-boot-starter-amqp`
- [ ] Eliminar cualquier dependencia NATS si existe
- [ ] Agregar `spring-rabbit-test` en scope test

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 2.2 Crear configuración RabbitMQ
- [ ] Crear `infrastructure/config/RabbitMQConfig.java`
  - Declarar exchanges: `logistics.exchange` (topic)
  - Declarar queues: `robot.status.snapshot`, `robot.command`, `robot.dispatch.order`, `robot.dispatch.package`
  - Declarar bindings entre queues y exchange con routing keys

### 2.3 Migrar Adapters (Inbound — Consumers)

#### 2.3.1 `NatsTelemetrySubscriber` → `RabbitTelemetrySubscriber`
- [ ] Archivo actual: `infrastructure/adapter/in/nats/NatsTelemetrySubscriber.java`
- [ ] Nuevo archivo: `infrastructure/adapter/in/amqp/RabbitTelemetrySubscriber.java`
- [ ] Cambiar `@NatsSubscriber` → `@RabbitListener(queues = "robot.telemetry")`
- [ ] Usar `@Service` + Spring AMQP
- [ ] Misma lógica de parsing JSON, diferente transporte

#### 2.3.2 `NatsOrderDispatchSubscriber` → `RabbitOrderDispatchSubscriber`
- [ ] Archivo actual: `infrastructure/adapter/in/nats/NatsOrderDispatchSubscriber.java`
- [ ] Nuevo archivo: `infrastructure/adapter/in/amqp/RabbitOrderDispatchSubscriber.java`
- [ ] Cambiar a `@RabbitListener(queues = "robot.dispatch.order")`
- [ ] Routing key: `order.dispatched`

#### 2.3.3 `NatsPackageDispatchSubscriber` → `RabbitPackageDispatchSubscriber`
- [ ] Archivo actual: `infrastructure/adapter/in/nats/NatsPackageDispatchSubscriber.java`
- [ ] Nuevo archivo: `infrastructure/adapter/in/amqp/RabbitPackageDispatchSubscriber.java`
- [ ] Cambiar a `@RabbitListener(queues = "robot.dispatch.package")`
- [ ] Routing key: `package.dispatched`

### 2.4 Migrar Adapters (Outbound — Publishers)

#### 2.4.1 `NatsRobotEventPublisher` → `RabbitRobotEventPublisher`
- [ ] Archivo actual: `infrastructure/adapter/out/nats/NatsRobotEventPublisher.java`
- [ ] Nuevo archivo: `infrastructure/adapter/out/rabbitmq/RabbitRobotEventPublisher.java`
- [ ] Inyectar `RabbitTemplate`
- [ ] Publicar a exchange `logistics.exchange` con routing key `robot.status.update`
- [ ] Publicar batch a routing key `robot.status.batch`

#### 2.4.2 `NatsRobotCommandPublisher` → `RabbitRobotCommandPublisher`
- [ ] Archivo actual: `infrastructure/adapter/out/nats/NatsRobotCommandPublisher.java`
- [ ] Nuevo archivo: `infrastructure/adapter/out/rabbitmq/RabbitRobotCommandPublisher.java`
- [ ] Publicar comandos a routing key `robot.command`

#### 2.4.3 `NatsRobotDispatchAdapter` → `RabbitRobotDispatchAdapter`
- [ ] Archivo actual: `infrastructure/adapter/out/nats/NatsRobotDispatchAdapter.java`
- [ ] Nuevo archivo: `infrastructure/adapter/out/rabbitmq/RabbitRobotDispatchAdapter.java`
- [ ] Publicar dispatch requests

### 2.5 Actualizar Puertos (Ports)
- [ ] Revisar `TelemetryStreamPort.java` — mantener interfaz, solo cambiar implementación
- [ ] Revisar `RobotDispatchPort.java` — mantener interfaz, solo cambiar implementación
- [ ] Revisar `RobotEventPort.java` — mantener interfaz, solo cambiar implementación

### 2.6 Eliminar código NATS
- [ ] Borrar carpeta `infrastructure/adapter/in/nats/` (todos los archivos)
- [ ] Borrar carpeta `infrastructure/adapter/out/nats/` (todos los archivos)
- [ ] Eliminar imports de NATS en todo el proyecto

### 2.7 Actualizar `application.properties`
- [ ] Eliminar `nats.url` y `nats.subject`
- [ ] Agregar:
```properties
spring.rabbitmq.host=${SPRING_RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${SPRING_RABBITMQ_PORT:5672}
spring.rabbitmq.username=${SPRING_RABBITMQ_USER:guest}
spring.rabbitmq.password=${SPRING_RABBITMQ_PASS:guest}
```

### 2.8 Verificar
- [ ] `ms-robot-status` compila sin errores
- [ ] No quedan imports de NATS en el proyecto
- [ ] Todos los adapters usan Spring AMQP (`RabbitTemplate`, `@RabbitListener`)

---

## Fase 3: ms-warehouse-core — Limpieza

### 3.1 Eliminar NATS residual (si quedó del merge)
- [ ] Borrar `infrastructure/config/NatsConfig.java`
- [ ] Borrar cualquier archivo en `adapter/out/nats/`
- [ ] Eliminar `nats.url` y `nats.subject` de `application.yml`
- [ ] Verificar que `RabbitRouteEventPublisher` es el único publisher activo

### 3.2 Adoptar cambios de main
- [ ] `RabbitRouteEventPublisher.java` (ya viene de main)
- [ ] `OutboxPublisherScheduler.java` actualizado
- [ ] `application.yml` con config RabbitMQ
- [ ] `pom.xml` con `spring-boot-starter-amqp`

---

## Fase 4: Simulation (Unreal Engine 5) — 🔴 Mayor esfuerzo

> **Nota:** Esta es la parte más compleja. La simulación implementa un cliente NATS con TCP raw socket. Se necesita una solución equivalente para RabbitMQ.

### 4.1 Opciones para RabbitMQ en UE5

| Opción | Descripción | Viabilidad |
|---|---|---|
| **A. RabbitMQ HTTP API** | Usar REST API del management plugin (puerto 15672) | 🟡 Más simple, pero polling (no real-time) |
| **B. RabbitMQ Web STOMP** | STOMP sobre WebSocket (plugin `rabbitmq_web_stomp`) | 🟢 Buen balance, tiempo real |
| **C. AMQP C++ client** | Librería AMQP nativa integrada en UE5 | 🔴 Complejo pero más robusto |
| **D. Bridge HTTP/SSE** | El backend expone SSE, la simulación consume via HTTP | 🟢 Simple, ya tienes `SseTelemetryAdapter` |

**Recomendación:** Opción **D (SSE/HTTP)** — Ya tienes `SseTelemetryAdapter` en ms-robot-status. La simulación puede usar HTTP REST para enviar telemetría y SSE para recibir comandos, eliminando la necesidad de un broker directo en UE5.

### 4.2 Si se elige Opción D (SSE/HTTP):

#### 4.2.1 Crear `HttpRobotClient` (reemplaza `NatsWebSocketClient`)
- [ ] Nuevo archivo: `Simulation/Source/Simulation/HttpRobotClient.h`
- [ ] Nuevo archivo: `Simulation/Source/Simulation/HttpRobotClient.cpp`
- [ ] Implementar usando `FHttpModule` de UE5:
  - `POST /api/robots/{id}/telemetry` — enviar datos de telemetría
  - `GET /api/robots/{id}/commands` (SSE o polling) — recibir comandos
  - `GET /api/robots` — obtener estado de todos los robots

#### 4.2.2 Actualizar `RobotManager`
- [ ] Cambiar referencia de `UNatsWebSocketClient` → `UHttpRobotClient`
- [ ] Actualizar bindings de delegates

#### 4.2.3 Actualizar `WarehouseRobot`
- [ ] Cambiar publish de telemetría (NATS → HTTP POST)
- [ ] Cambiar recepción de comandos (NATS subscribe → HTTP polling/SSE)

#### 4.2.4 Eliminar archivos NATS
- [ ] Borrar `Simulation/Source/Simulation/NatsWebSocketClient.h`
- [ ] Borrar `Simulation/Source/Simulation/NatsWebSocketClient.cpp`
- [ ] Eliminar referencias en `Build.cs` si las hay

### 4.3 Si se elige Opción B (Web STOMP):

#### 4.3.1 Habilitar plugin en RabbitMQ
- [ ] Agregar `rabbitmq_web_stomp` al docker-compose:
```yaml
rabbitmq:
  image: rabbitmq:3.13-management-alpine
  ports:
    - "15674:15674"  # Web STOMP
```
- [ ] Crear `StompClient` en UE5 usando WebSocket + STOMP protocol
- [ ] Mapear subjects NATS → STOMP destinations

### 4.4 Si se elige Opción A (HTTP API polling):
- [ ] Implementar polling cada N ms al endpoint REST
- [ ] Simple pero mayor latencia

---

## Fase 5: ms-robot-status — Exponer endpoints para la simulación

> Si se elige SSE/HTTP para la simulación, se necesitan nuevos endpoints REST.

### 5.1 Actualizar `RobotStatusController`
- [ ] `POST /api/robots/{id}/telemetry` — recibir telemetría de la simulación
- [ ] `GET /api/robots/{id}/commands` — SSE stream de comandos para la simulación
- [ ] `POST /api/robots/{id}/dispatch` — recibir solicitudes de dispatch

### 5.2 Usar `SseTelemetryAdapter` existente
- [ ] Ya tienes `infrastructure/adapter/out/sse/SseTelemetryAdapter.java`
- [ ] Conectarlo al nuevo endpoint SSE de comandos

---

## Fase 6: Testing y Verificación

### 6.1 Tests unitarios
- [ ] Tests para `RabbitRobotEventPublisher`
- [ ] Tests para `RabbitTelemetrySubscriber`
- [ ] Tests para `RabbitOrderDispatchSubscriber`
- [ ] Tests para `RabbitPackageDispatchSubscriber`

### 6.2 Tests de integración
- [ ] Verificar que RabbitMQ recibe mensajes de warehouse-core
- [ ] Verificar que ms-robot-status consume de RabbitMQ
- [ ] Verificar que ms-logistics-analytics consume de RabbitMQ
- [ ] Verificar comunicación simulación ↔ backend

### 6.3 Build Docker completo
```bash
cd smartlogistic
docker-compose build
docker-compose up -d
```
- [ ] Todos los contenedores levantan sin errores
- [ ] No hay referencias a NATS en ningún log
- [ ] RabbitMQ management UI (http://localhost:15672) muestra queues y exchanges

---

## 📁 Archivos a Eliminar

```
smartlogistic/nats/nats.conf
smartlogistic/ms-robot-status/src/main/java/.../infrastructure/adapter/in/nats/
  ├── NatsTelemetrySubscriber.java
  ├── NatsOrderDispatchSubscriber.java
  └── NatsPackageDispatchSubscriber.java
smartlogistic/ms-robot-status/src/main/java/.../infrastructure/adapter/out/nats/
  ├── NatsRobotDispatchAdapter.java
  ├── NatsRobotEventPublisher.java
  └── NatsRobotCommandPublisher.java
smartlogistic/ms-warehouse-core/src/main/java/.../infrastructure/config/NatsConfig.java
Simulation/Source/Simulation/NatsWebSocketClient.h
Simulation/Source/Simulation/NatsWebSocketClient.cpp
```

## 📁 Archivos a Crear

```
smartlogistic/ms-robot-status/src/main/java/.../infrastructure/config/RabbitMQConfig.java
smartlogistic/ms-robot-status/src/main/java/.../infrastructure/adapter/in/amqp/
  ├── RabbitTelemetrySubscriber.java
  ├── RabbitOrderDispatchSubscriber.java
  └── RabbitPackageDispatchSubscriber.java
smartlogistic/ms-robot-status/src/main/java/.../infrastructure/adapter/out/rabbitmq/
  ├── RabbitRobotEventPublisher.java
  ├── RabbitRobotCommandPublisher.java
  └── RabbitRobotDispatchAdapter.java
Simulation/Source/Simulation/HttpRobotClient.h          # Si Opción D
Simulation/Source/Simulation/HttpRobotClient.cpp         # Si Opción D
```

---

## 🗓️ Orden de Ejecución Sugerido

1. **Fase 0** — Merge de origin/main (15 min)
2. **Fase 1** — Limpiar infra (docker-compose, nats config) (30 min)
3. **Fase 3** — Limpiar warehouse-core (15 min)
4. **Fase 2** — Migrar ms-robot-status Java (2-3 horas)
5. **Fase 4** — Migrar Simulation UE5 (3-5 horas, depende de opción elegida)
6. **Fase 5** — Endpoints para simulación (1 hora)
7. **Fase 6** — Testing (1-2 horas)

**Total estimado:** 8-12 horas de trabajo

---

## 🎯 Decisión pendiente: ¿Cómo conectar la simulación UE5?

> **Esta es la decisión más importante del plan.**

| Criterio | SSE/HTTP (D) | Web STOMP (B) | AMQP nativo (C) |
|---|---|---|---|
| Latencia | ~100-500ms | ~10-50ms | ~1-10ms |
| Complejidad | 🟢 Baja | 🟡 Media | 🔴 Alta |
| Tiempo real | Parcial (polling) | ✅ Sí | ✅ Sí |
| Dependencies | Ninguna | WebSocket | Librería C++ |
| Recomendación | ✅ Para este proyecto | Alternativa | Overkill |

**Recomendación final:** Usar **SSE/HTTP (Opción D)** ya que:
- Ya tienes `SseTelemetryAdapter` implementado
- UE5 tiene `FHttpModule` built-in (sin dependencias externas)
- Para un proyecto universitario, la latencia es aceptable
- Minimiza el riesgo de bugs en la capa de transporte