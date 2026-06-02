# SmartLogistics — Gestión de Almacenes Autónomos

![Java 21](https://img.shields.io/badge/Java-21-%23ED8B00?logo=openjdk)
![Spring Boot 3.3](https://img.shields.io/badge/Spring_Boot-3.3-%236DB33F?logo=springboot)
![Next.js 14](https://img.shields.io/badge/Next.js-14-%23000000?logo=nextdotjs)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-%234169E1?logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7-%23DC382D?logo=redis)
![MongoDB](https://img.shields.io/badge/MongoDB-7-%2347A248?logo=mongodb)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-%23FF6600?logo=rabbitmq)
![Docker](https://img.shields.io/badge/Docker-Compose-%232496ED?logo=docker)
![Unreal Engine 5](https://img.shields.io/badge/Unreal_Engine-5.7-%230E1128?logo=unrealengine)

Sistema de gestión logística para centros de distribución masivos que utilizan **robots autónomos** para el picking de productos. Coordina órdenes de despacho en tiempo real, valida disponibilidad y estado de carga de robots, previene colisiones en pasillos de alta densidad y registra cada movimiento para análisis de eficiencia.

Proyecto Integrador Final — **Arquitectura de Software II** (Caso de Estudio #9: SmartLogistics).

---

## Problema

Un centro de distribución masivo utiliza robots autónomos para el "picking" de productos. El sistema actual carece de coordinación en tiempo real, lo que genera:

- **Colisiones** entre robots en pasillos de alta densidad.
- **Tiempos muertos** por rutas subóptimas.
- **Falta de trazabilidad** sobre el rendimiento logístico.
- **Asignación ineficiente** de órdenes sin considerar el estado real de los robots (batería, ubicación).

### Reglas de Negocio

- No se puede asignar una orden de despacho a un robot si su nivel de batería (verificado síncronamente) es inferior al 15%.
- Los productos marcados como **"Frágiles"** deben ser transportados a velocidad reducida, configurada dinámicamente en el microservicio principal.
- Cada ruta completada debe enviarse asíncronamente a un sistema de analítica para generar mapas de calor de congestión en el almacén.

---

## Solución

Arquitectura de **microservicios** con comunicación **síncrona (REST)** para operaciones que requieren respuesta inmediata y **asíncrona (RabbitMQ)** para procesos que no deben bloquear al usuario. Todos los servicios siguen el patrón **Arquitectura Hexagonal (Puertos & Adaptadores)** con separación estricta de capas de dominio, aplicación e infraestructura.

Incluye una **simulación 3D en Unreal Engine 5.7** que visualiza la flota de robots en tiempo real, un **dashboard web en Next.js** para gestión del almacén, y un stack completo de **observabilidad** (Prometheus, Grafana, Loki, Jaeger).

---

## Arquitectura

### Diagrama de Contenedores (C4 Nivel 2)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                   NGINX API Gateway (:8086)                          │
│                        JWT Validation · CORS · Routing · SSE Proxy                   │
└──┬──────────┬──────────────────┬──────────────────┬──────────────────┬──────────────┘
   │          │                  │                  │                  │
   ▼          ▼                  ▼                  ▼                  ▼
┌──────┐ ┌──────────┐ ┌──────────────┐ ┌────────────────┐ ┌──────────────────┐
│ Web  │ │Identity  │ │Warehouse Core│ │ Robot Status   │ │Logistics         │
│ UI   │ │:8084     │ │:8081         │ │ :8082          │ │Analytics :8083   │
│(Next)│ │(REST)    │ │(REST + AMQP) │ │(REST + AMQP    │ │(AMQP Consumer)   │
│      │ │          │ │              │ │ + SSE)         │ │                  │
└──────┘ └──────┬───┘ └──────┬───────┘ └───────┬────────┘ └────────┬─────────┘
                │            │                  │                   │
                ▼            ▼                  ▼                   ▼
         ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────────┐
         │PostgreSQL│ │PostgreSQL│ │    Redis     │ │    MongoDB       │
         │ auth_db  │ │warehouse │ │ (estado de   │ │ (rutas, muestras │
         │ (:5433)  │ │_db (:5432)│ │  robots)    │ │  de congestión)  │
         └──────────┘ └──────────┘ └──────────────┘ └──────────────────┘
                              │              │
                              ▼              ▼
                    ┌───────────────────────────────────────┐
                    │         RABBITMQ (:5672)               │
                    │   Exchange: logistics.exchange         │
                    │                                       │
                    │ ┌─────────── Routing Keys ──────────┐  │
                    │ │ order.dispatched                  │  │
                    │ │ package.dispatched                │  │
                    │ │ route.completed  ◄── Analytics    │  │
                    │ │ robot.telemetry ◄── Sim/Status    │  │
                    │ │ robot.command   ──► Simulation    │  │
                    │ └───────────────────────────────────┘  │
                    └───────────────────────────────────────┘
                                      │
                                      ▼
                              ┌────────────────┐
                              │   Unreal Engine │
                              │  Simulation     │
                              │  (STOMP/WS +    │
                              │   HTTP REST)    │
                              └────────────────┘
```

### Flujo de Comunicación

| Tipo | Protocolo | Origen → Destino | Propósito |
|------|-----------|-----------------|-----------|
| **Síncrono** | REST (HTTP) | Warehouse Core → Robot Status | Verificar batería del robot antes de asignar orden |
| **Síncrono** | REST (HTTP) | Web UI → Identity | Login, registro, validación de JWT |
| **Síncrono** | REST (HTTP) | Nginx → Warehouse Core | CRUD de órdenes, inventario, rutas |
| **Asíncrono** | AMQP (RabbitMQ) | Warehouse Core → Robot Status | Notificar nueva orden despachada |
| **Asíncrono** | AMQP (RabbitMQ) | Warehouse Core → Logistics Analytics | Enviar ruta completada para mapa de calor |
| **Asíncrono** | AMQP (RabbitMQ) | Robot Status → Simulation | Enviar comandos a robots |
| **Asíncrono** | STOMP/WS (RabbitMQ) | Simulation → Robot Status | Telemetría de robots (batería, posición) |
| **Tiempo real** | SSE (HTTP) | Robot Status → Web UI | Streaming de telemetría de robots |

---

## Microservicios

| Servicio | Puerto | Base de Datos | Responsabilidad |
|----------|--------|--------------|----------------|
| **ms-warehouse-core** | `8081` | PostgreSQL (`warehouse_db`) | Orquestación central: órdenes de despacho, inventario, planificación de rutas (Dijkstra), política de productos frágiles, patrón Outbox para eventos |
| **ms-robot-status** | `8082` | Redis | Estado de flota de robots (batería, ubicación, modo operativo), telemetría vía SSE, suscripción a eventos RabbitMQ |
| **ms-logistics-analytics** | `8083` | MongoDB (`logistics_analytics`) | Consume eventos `route.completed`, persiste datos de rutas y muestras de congestión para analítica |
| **ms-identity** | `8084` | PostgreSQL (`auth_db`) | Registro, login, generación y validación de tokens JWT |

---

## Tecnologías

### Backend
- **Lenguaje:** Java 21
- **Framework:** Spring Boot 3.3.6, Spring Data JPA, Spring WebFlux, Spring AMQP, Spring Security Crypto
- **Arquitectura:** Hexagonal (Puertos & Adaptadores), Domain-Driven Design
- **Base de datos:** PostgreSQL 16, Redis 7, MongoDB 7
- **Mensajería:** RabbitMQ 3.13 con AMQP y Web STOMP
- **Migraciones:** Flyway
- **Build:** Maven con Wrapper

### Frontend
- **Framework:** Next.js 14.2 (App Router) + TypeScript
- **Estilos:** Tailwind CSS 3.4
- **Tiempo real:** Server-Sent Events (SSE)

### Infraestructura
- **Contenedores:** Docker Compose (18 servicios)
- **API Gateway:** Nginx 1.27 con autenticación JWT mediante `auth_request`
- **Red:** Bridge (`smartlogistic-net`)

### Observabilidad
- **Métricas:** Prometheus + Micrometer
- **Dashboards:** Grafana (pre-provisionados)
- **Logs:** Loki + Promtail
- **Trazabilidad distribuida:** OpenTelemetry Collector + Jaeger

### Simulación
- **Motor:** Unreal Engine 5.7
- **Comunicación:** STOMP WebSocket + HTTP REST

---

## Requisitos Previos

- [Docker](https://docs.docker.com/get-docker/) y [Docker Compose](https://docs.docker.com/compose/install/) (obligatorio)
- Java 21+ (solo para desarrollo local)
- Node.js 20+ y npm (solo para desarrollo del frontend)
- Git

---

## Inicio Rápido

### 1. Clonar el repositorio

```bash
git clone <url-del-repositorio>
cd "Proyecto final"
```

### 2. Configurar variables de entorno

```bash
cd smartlogistic
cp .env.example .env
```

Edita `.env` si necesitas cambiar puertos o credenciales (los valores por defecto funcionan para desarrollo local).

### 3. Iniciar todos los servicios

```bash
docker compose up --build -d
```

Esto construye e inicia los 18 servicios. La primera vez tomará varios minutos (descarga imágenes y compila los microservicios Java).

### 4. Verificar que todo esté funcionando

```bash
docker compose ps
```

Todos los servicios deben estar en estado `Up`.

### 5. Acceder a las interfaces

| Interfaz | URL | Credenciales |
|----------|-----|-------------|
| **Web UI** | [http://localhost:8086](http://localhost:8086) | Registrarse o `admin@test.com` / `Admin123!` |
| **Grafana** | [http://localhost:3001](http://localhost:3001) | `admin` / `admin` |
| **Jaeger** | [http://localhost:16686](http://localhost:16686) | — |
| **RabbitMQ UI** | [http://localhost:15672](http://localhost:15672) | `guest` / `guest` |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | — |

### 6. Detener todo

```bash
docker compose down
```

Para eliminar también los volúmenes de bases de datos:

```bash
docker compose down -v
```

---

## Desarrollo Local

### Compilar un microservicio individual

```bash
cd smartlogistic/ms-warehouse-core
./mvnw clean package -DskipTests
```

### Ejecutar sin Docker

Cada microservicio necesita su base de datos corriendo. Puedes iniciar solo las DBs con:

```bash
docker compose up -d warehouse-db auth-db robot-redis analytics-db rabbitmq
```

Luego ejecutar cada microservicio con Maven:

```bash
cd smartlogistic/ms-warehouse-core
./mvnw spring-boot:run
```

### Frontend (desarrollo)

```bash
cd smartlogistic-web
npm install
npm run dev
```

El frontend en modo desarrollo corre en `http://localhost:3000`.

---

## Observabilidad

### Grafana
Dashboards pre-configurados para monitorear:
- **Visión general** del sistema (KPIs globales)
- **ms-warehouse-core** (órdenes, rutas, inventario)
- **ms-robot-status** (flota, batería, telemetría)
- **ms-logistics-analytics** (eventos procesados, congestión)
- **ms-identity** (usuarios, autenticación)

### Jaeger
Trazabilidad distribuida extrema a extremo. Cada request que cruza múltiples servicios (ej. crear orden → validar robot → planificar ruta) queda registrada con tiempos por span.

### Logs
Loki + Promtail agregan logs de todos los contenedores, consultables desde la interfaz de Grafana (Explore).

---

## Simulación Unreal Engine 5

El proyecto incluye una simulación 3D en `Simulation/` que:

- Construye proceduralmente el almacén desde el layout del backend.
- Conecta a RabbitMQ vía STOMP WebSocket para recibir eventos en tiempo real.
- Reporta telemetría (batería, posición, estado) al ms-robot-status.
- Recibe comandos de navegación y asignación de órdenes.

Para abrir la simulación, abre `Simulation/Simulation.uproject` con Unreal Engine 5.7.

---

## Estructura del Proyecto

```
Proyecto final/
├── smartlogistic/                      # Backend + infraestructura
│   ├── docker-compose.yml              # Orquestación completa (18 servicios)
│   ├── .env.example                    # Variables de entorno
│   ├── nginx/                          # API Gateway
│   ├── rabbitmq/                       # Configuración del broker
│   ├── observability/                  # Prometheus, Grafana, Loki, Jaeger, OTEL
│   ├── ms-identity/                    # Microservicio de autenticación
│   ├── ms-warehouse-core/              # Microservicio principal
│   ├── ms-robot-status/                # Microservicio de estado de robots
│   └── ms-logistics-analytics/         # Microservicio de analítica
├── smartlogistic-web/                  # Frontend Next.js
├── Simulation/                         # Simulación Unreal Engine 5
├── docs/                               # Diagramas C4 (PNG + DrawIO)
└── Documentos/                         # Informes académicos
```

---

## Decisiones Arquitectónicas (RFC)

### Atributos de Calidad
1. **Disponibilidad** — La validación de batería y asignación de robots no debe depender de logs o analítica asíncrona.
2. **Latencia** — La verificación de batería debe ser < 200ms para no retrasar la asignación.
3. **Integridad** — Cada evento de ruta debe registrarse; si el log falla, la operación continúa pero se genera alerta.

### Trade-offs
- **Consistencia eventual** en la analítica (MongoDB): Se sacrifica consistencia inmediata por disponibilidad; los mapas de calor se actualizan con mínima latencia pero pueden tener rezagos.
- **Redis como caché volátil** para estado de robots: Se pierde el estado si Redis se cae, pero la ganancia en velocidad de lectura/escritura es crítica para decisiones en tiempo real.
- **Base de datos poliglota**: Cada motor se eligió por su fortaleza específica (PostgreSQL para transacciones, Redis para velocidad, MongoDB para documentos de analítica), aumentando la complejidad operativa.

### Patrones Implementados
- **Arquitectura Hexagonal** en todos los microservicios
- **Outbox Pattern** en ms-warehouse-core para entrega confiable de eventos
- **Saga Coreográfica** vía RabbitMQ para coordinación entre servicios
- **API Gateway** con JWT validation para seguridad uniforme
- **SSE** para actualizaciones en tiempo real en la UI

---

## Licencia

Proyecto académico — Universidad, Arquitectura de Software II.
