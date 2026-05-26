# Diagrama de Arquitectura — SmartLogistic

> Relaciones entre microservicios, bases de datos, broker y observabilidad.

## Diagrama de Contenedores (C4 Nivel 2)

```mermaid
graph TB
    subgraph "Capa de Entrada"
        NGINX["🔄 Nginx Reverse Proxy<br/>:8080"]
    end

    subgraph "Capa Síncrona (REST)"
        WCORE["🏗️ MS-WarehouseCore<br/>Hexagonal / :8081"]
        ROBOT["🤖 MS-RobotStatus<br/>Redis / :8082"]
    end

    subgraph "Capa Asíncrona (Eventos)"
        NATS["📨 NATS Broker<br/>:4222"]
        ANALYTICS["📊 MS-LogisticsAnalytics<br/>MongoDB / :8083"]
    end

    subgraph "Persistencia"
        PG[("🐘 PostgreSQL<br/>warehouse-db:5432")]
        RD[("⚡ Redis<br/>robot-redis:6379")]
        MG[("🍃 MongoDB<br/>analytics-db:27017")]
    end

    subgraph "Observabilidad"
        PROM["📈 Prometheus<br/>:9090"]
        GRAF["📉 Grafana<br/>:3000"]
        LOKI["📋 Loki<br/>:3100"]
    end

    subgraph "Cliente"
        CLIENT["👤 Postman / curl"]
    end

    CLIENT -->|"HTTP :8080/api/*"| NGINX
    NGINX -->|"/api/orders"| WCORE

    WCORE -->|"GET /api/robots/{id}/status<br/>Validar batería >= 15%"| ROBOT
    ROBOT -->|"Cache/Estado"| RD

    WCORE -->|"INSERT/SELECT/UPDATE"| PG

    WCORE -->|"📤 route.completed<br/>(Outbox Pattern)"| NATS
    NATS -->|"📥 Consume route.completed"| ANALYTICS
    ANALYTICS -->|"INSERT"| MG

    WCORE -.->|"/actuator/prometheus"| PROM
    ROBOT -.->|"/actuator/prometheus"| PROM
    ANALYTICS -.->|"/actuator/prometheus"| PROM

    PROM -.->|"datasource"| GRAF
    LOKI -.->|"datasource"| GRAF

    WCORE -.->|"stdout → driver"| LOKI
    ROBOT -.->|"stdout → driver"| LOKI
    ANALYTICS -.->|"stdout → driver"| LOKI
```

## Diagrama de Secuencia — Flujo Principal

```mermaid
sequenceDiagram
    participant C as Cliente (curl)
    participant N as Nginx (:8080)
    participant W as MS-WarehouseCore (:8081)
    participant R as MS-RobotStatus (:8082)
    participant P as PostgreSQL
    participant NAT as NATS
    participant A as MS-LogisticsAnalytics (:8083)
    participant M as MongoDB

    C->>N: POST /api/orders
    N->>W: reenvía
    W->>P: INSERT dispatch_order
    W-->>C: 201 Created

    C->>N: POST /api/orders/{id}/assign-robot
    N->>W: reenvía
    W->>R: GET /api/robots/{id}/status
    R-->>W: batteryLevel: 85 (>= 15%)
    W->>P: UPDATE order.status = ASSIGNED
    W-->>C: 200 OK (robot assigned)

    alt Batería < 15%
        R-->>W: batteryLevel: 10
        W-->>C: 409 Conflict (batería insuficiente)
    end

    C->>N: POST /api/routes/{id}/complete
    N->>W: reenvía
    W->>P: UPDATE stock (descontar)
    W->>P: INSERT outbox_event (route.completed)
    W->>N: publish route.completed
    W-->>C: 200 OK

    NAT->>A: consume route.completed
    A->>M: INSERT route_event
```

## Matriz de Comunicación entre Componentes

| Comunicación | Origen → Destino | Protocolo | Propósito |
|---|---|---|---|
| **Síncrona** | MS-WarehouseCore → MS-RobotStatus | REST (Feign/WebClient) | Validar batería >= 15% antes de asignar robot |
| **Asíncrona** | MS-WarehouseCore → NATS | NATS publish | Publicar evento `route.completed` |
| **Asíncrona** | NATS → MS-LogisticsAnalytics | NATS subscribe | Consumir rutas para mapas de calor |
| **Síncrona** | MS-WarehouseCore → PostgreSQL | JDBC/JPA | Persistir órdenes, inventario, rutas |
| **Síncrona** | MS-RobotStatus → Redis | Lettuce/Redis client | Cachear estado operativo de robots |
| **Síncrona** | MS-LogisticsAnalytics → MongoDB | Spring Data MongoDB | Persistir eventos de rutas |
| **Outbox** | MS-WarehouseCore → PostgreSQL → NATS | Patrón Outbox | Garantizar entrega del evento aunque NATS falle |
| **Síncrona** | Cliente → Nginx → MS-WarehouseCore | REST HTTP | API pública del sistema |

## Puertos Expuestos

| Componente | Puerto host | Puerto contenedor | Visibilidad |
|---|---|---|---|
| Nginx | 8080 | 80 | Público (API) |
| MS-WarehouseCore | 8081 | 8081 | Interna (vía Nginx) |
| MS-RobotStatus | 8082 | 8082 | Interna |
| MS-LogisticsAnalytics | 8083 | 8083 | Interna |
| PostgreSQL | 5432 | 5432 | Interna |
| Redis | 6379 | 6379 | Interna |
| MongoDB | 27017 | 27017 | Interna |
| NATS | 4222 | 4222 | Interna |
| Prometheus | 9090 | 9090 | Administrativa |
| Grafana | 3000 | 3000 | Administrativa |
| Loki | 3100 | 3100 | Administrativa |
