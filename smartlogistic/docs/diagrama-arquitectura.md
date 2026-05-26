# Diagrama de Arquitectura — SmartLogistic

> Relaciones entre microservicios, bases de datos, broker y observabilidad.

## Diagrama de Contenedores (C4 Nivel 2)

```mermaid
graph TB
    subgraph "Capa de Entrada"
        NGINX["🔄 Nginx Reverse Proxy<br/>:8080"]
    end

    subgraph "Capa Síncrona (REST)"
        IDENTITY["🔐 MS-Identity<br/>JWT / :8084"]
        WCORE["🏗️ MS-WarehouseCore<br/>Hexagonal / :8081"]
        ROBOT["🤖 MS-RobotStatus<br/>Redis / :8082"]
    end

    subgraph "Capa Asíncrona (Eventos)"
        RMQ["📨 RabbitMQ<br/>Exchange: logistics.exchange<br/>Queue: route.completed.q"]
        ANALYTICS["📊 MS-LogisticsAnalytics<br/>MongoDB / :8083"]
    end

    subgraph "Persistencia"
        AUTH_PG[("🐘 PostgreSQL Auth<br/>auth-db:5433")]
        WH_PG[("🐘 PostgreSQL Warehouse<br/>warehouse-db:5432")]
        RD[("⚡ Redis<br/>robot-redis:6379")]
        MG[("🍃 MongoDB<br/>analytics-db:27017")]
    end

    subgraph "Trazabilidad"
        JAEGER["🔍 Jaeger<br/>:16686"]
    end

    subgraph "Observabilidad"
        PROM["📈 Prometheus<br/>:9090"]
        GRAF["📉 Grafana<br/>:3000"]
        LOKI["📋 Loki<br/>:3100"]
    end

    subgraph "Cliente"
        CLIENT["👤 Postman / curl"]
    end

    CLIENT -->|"HTTP :8080/api/auth/*"| NGINX
    CLIENT -->|"HTTP :8080/api/* (Bearer JWT)"| NGINX

    NGINX -->|"/api/auth/* (público)"| IDENTITY
    NGINX -->|"auth_request (JWT)"| IDENTITY
    NGINX -->|"/api/* (JWT válido)"| WCORE

    IDENTITY -->|"SELECT/INSERT"| AUTH_PG

    WCORE -->|"GET /api/robots/{id}/status"| ROBOT
    ROBOT -->|"Cache/Estado"| RD

    WCORE -->|"INSERT/SELECT/UPDATE"| WH_PG

    WCORE -->|"📤 route.completed<br/>Exchange: logistics.exchange"| RMQ
    RMQ -->|"Queue: route.completed.q"| ANALYTICS
    ANALYTICS -->|"INSERT"| MG

    WCORE -.->|"/actuator/prometheus"| PROM
    ROBOT -.->|"/actuator/prometheus"| PROM
    ANALYTICS -.->|"/actuator/prometheus"| PROM
    IDENTITY -.->|"/actuator/prometheus"| PROM

    PROM -.->|"datasource"| GRAF
    LOKI -.->|"datasource"| GRAF

    WCORE -.->|"stdout → driver"| LOKI
    ROBOT -.->|"stdout → driver"| LOKI
    ANALYTICS -.->|"stdout → driver"| LOKI
    IDENTITY -.->|"stdout → driver"| LOKI

    WCORE -.->|"OTLP :4318"| JAEGER
    ROBOT -.->|"OTLP :4318"| JAEGER
    ANALYTICS -.->|"OTLP :4318"| JAEGER
    IDENTITY -.->|"OTLP :4318"| JAEGER
```

## Diagrama de Secuencia — Flujo Principal

```mermaid
sequenceDiagram
    participant C as Cliente (curl)
    participant N as Nginx (:8080)
    participant I as MS-Identity (:8084)
    participant W as MS-WarehouseCore (:8081)
    participant R as MS-RobotStatus (:8082)
    participant WH_PG as PostgreSQL Warehouse
    participant RMQ as RabbitMQ
    participant A as MS-LogisticsAnalytics (:8083)
    participant M as MongoDB

    C->>N: POST /api/auth/login
    N->>I: reenvía (público)
    I-->>C: 200 + JWT token

    C->>N: POST /api/orders (Authorization: Bearer JWT)
    N->>I: auth_request (valida JWT)
    I-->>N: 200 + X-Auth-User
    N->>W: POST /api/orders
    W->>WH_PG: INSERT dispatch_order
    W-->>N: 201 Created
    N-->>C: 201 Created

    C->>N: POST /api/orders/{id}/assign-robot (Bearer JWT)
    N->>I: auth_request
    I-->>N: 200
    N->>W: reenvía
    W->>R: GET /api/robots/{id}/status
    R-->>W: batteryLevel: 85
    W->>WH_PG: UPDATE order.status = ASSIGNED
    W-->>N: 200 OK
    N-->>C: 200 OK

    alt Batería < 15%
        R-->>W: batteryLevel: 10
        W-->>N: 409 Conflict
        N-->>C: 409 Conflict
    end

    C->>N: POST /api/routes/{id}/complete (Bearer JWT)
    N->>I: auth_request
    I-->>N: 200
    N->>W: reenvía
    W->>WH_PG: UPDATE stock (descontar)
    W->>WH_PG: INSERT outbox_event
    W->>RMQ: publish route.completed
    W-->>N: 200 OK
    N-->>C: 200 OK

    RMQ->>A: consume route.completed
    A->>M: INSERT route_event
```

## Matriz de Comunicación entre Componentes

| Comunicación | Origen → Destino | Protocolo | Propósito |
|---|---|---|---|
| **Síncrona** | Cliente → Nginx → MS-Identity | REST HTTP | Login, register (público) |
| **Síncrona** | Cliente → Nginx → MS-Identity | REST HTTP (auth_request) | Validar JWT antes de cada request protegido |
| **Síncrona** | Nginx → MS-WarehouseCore | REST HTTP | API protegida (órdenes, inventario, rutas) |
| **Síncrona** | MS-WarehouseCore → MS-RobotStatus | REST (Feign/WebClient) | Validar batería >= 15% antes de asignar robot |
| **Asíncrona** | MS-WarehouseCore → RabbitMQ | AMQP | Publicar evento `route.completed` |
| **Asíncrona** | RabbitMQ → MS-LogisticsAnalytics | AMQP (@RabbitListener) | Consumir rutas para mapas de calor |
| **Síncrona** | MS-Identity → PostgreSQL auth-db | JDBC/JPA | Persistir usuarios |
| **Síncrona** | MS-WarehouseCore → PostgreSQL warehouse-db | JDBC/JPA | Persistir órdenes, inventario, rutas |
| **Síncrona** | MS-RobotStatus → Redis | Lettuce/Redis client | Cachear estado operativo de robots |
| **Síncrona** | MS-LogisticsAnalytics → MongoDB | Spring Data MongoDB | Persistir eventos de rutas |
| **Outbox** | MS-WarehouseCore → PostgreSQL → RabbitMQ | Patrón Outbox | Garantizar entrega del evento aunque RabbitMQ falle |
| **Trazas** | Todos los MS → Jaeger | OpenTelemetry OTLP | Trazabilidad distribuida |

## Puertos Expuestos

| Componente | Puerto host | Puerto contenedor | Visibilidad |
|---|---|---|---|
| Nginx | 8080 | 80 | Público (API) |
| MS-Identity | 8084 | 8084 | Interna (vía Nginx) |
| MS-WarehouseCore | 8081 | 8081 | Interna (vía Nginx) |
| MS-RobotStatus | 8082 | 8082 | Interna |
| MS-LogisticsAnalytics | 8083 | 8083 | Interna |
| PostgreSQL Auth | 5433 | 5432 | Interna |
| PostgreSQL Warehouse | 5432 | 5432 | Interna |
| Redis | 6379 | 6379 | Interna |
| MongoDB | 27017 | 27017 | Interna |
| RabbitMQ | 5672 | 5672 | Interna (AMQP) |
| RabbitMQ Management | 15672 | 15672 | Administrativa |
| Prometheus | 9090 | 9090 | Administrativa |
| Grafana | 3000 | 3000 | Administrativa |
| Loki | 3100 | 3100 | Administrativa |
| Jaeger UI | 16686 | 16686 | Administrativa |
| Jaeger OTLP | 4318 | 4318 | Interna (trazas) |
