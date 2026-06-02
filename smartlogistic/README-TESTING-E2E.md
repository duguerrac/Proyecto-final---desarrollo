# 🧪 SmartLogistics — Guía de Pruebas E2E Paso a Paso

> Puertos por defecto: `ms-warehouse-core=8081` | `ms-robot-status=8082`

---

## Requisitos Previos

```bash
# 1. Levantar toda la infraestructura
cd smartlogistic
docker compose up -d --build

# 2. Verificar que los servicios están saludables
docker compose ps
```

Los servicios deben estar `healthy` o `running`:
- `smartlogistic-warehouse-core` → puerto **8081**
- `smartlogistic-robot-status` → puerto **8082**
- `smartlogistic-warehouse-postgres` → puerto **5432**
- `smartlogistic-redis` → puerto **6379**
- `smartlogistic-nats` → puerto **4222**

---

## Paso 1: Crear Layout del Almacén

> Esto genera las celdas, spots, rutas y puntos de navegación.

```bash
# Crear layout 8x8 con spots de estantería
curl -s -X POST http://localhost:8081/api/v1/warehouse/layouts \
  -H "Content-Type: application/json" \
  -d @../scripts/test-layout-payload.json | python -m json.tool
```

O manualmente:

```bash
curl -s -X POST http://localhost:8081/api/v1/warehouse/layouts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Almacén Principal",
    "rows": 8,
    "cols": 8
  }' | python -m json.tool
```

**✅ Verificar:** Debe responder con el layout creado incluyendo `id`, `status: "ACTIVE"` y la grilla de celdas.

---

## Paso 2: Listar Spots (Estantes) Disponibles

```bash
# Listar todos los spots generados
curl -s http://localhost:8081/api/v1/warehouse/spots | python -m json.tool
```

**✅ Verificar:** Debes ver spots con códigos como `SHELF-A1`, `SHELF-A2`, etc. Cada uno con `id`, `code`, `aisle`, `section`, `x`, `y`.

```bash
# Ver un spot específico
curl -s http://localhost:8081/api/v1/warehouse/spots/SHELF-A1 | python -m json.tool
```

---

## Paso 3: Verificar Catálogo de Productos (Seeder)

Los productos se seedearon automáticamente con la migración `V4__seed_inventory_items.sql`.

| ID | SKU | Nombre |
|----|-----|--------|
| 1 | SHO-001 | Nike Air Max 90 |
| 2 | SHO-002 | Adidas Ultraboost |
| 3 | SHO-003 | Puma RS-X |
| 4 | SHO-004 | Converse Chuck Taylor |
| 5 | SHO-005 | Vans Old Skool |
| 6 | SHT-001 | Camiseta Polo Classic |
| 7 | SHT-002 | Camisa Formal Manga Larga |
| 8 | SHT-003 | Camiseta Algodón Básica |
| 9 | SHT-004 | Polo Deportivo Dri-FIT |
| 10 | PNT-001 | Jeans Slim Fit |
| 11 | PNT-002 | Jogger Deportivo |
| 12 | PNT-003 | Pantalón Formal |
| 13 | JCK-001 | Chaqueta de Cuero |
| 14 | JCK-002 | Bomber Jacket |
| 15 | ACC-001 | Gorra Baseball |
| 16 | ACC-002 | Cinturón Cuero |

---

## Paso 4: Agregar Stock a los Estantes

### Agregar Nike Air Max (ID=1) al estante SHELF-A1 — 50 unidades

```bash
curl -s -X POST http://localhost:8081/api/v1/warehouse/spots/SHELF-A1/items \
  -H "Content-Type: application/json" \
  -d '{"itemId": 1, "quantityAvailable": 50}' | python -m json.tool
```

**✅ Verificar:** Response con `itemId: 1, name: "Nike Air Max 90", sku: "SHO-001", quantityAvailable: 50`

### Agregar Camiseta Polo (ID=6) al estante SHELF-A1 — 100 unidades

```bash
curl -s -X POST http://localhost:8081/api/v1/warehouse/spots/SHELF-A1/items \
  -H "Content-Type: application/json" \
  -d '{"itemId": 6, "quantityAvailable": 100}' | python -m json.tool
```

### Agregar Jeans Slim Fit (ID=10) al estante SHELF-B2 — 30 unidades

```bash
curl -s -X POST http://localhost:8081/api/v1/warehouse/spots/SHELF-B2/items \
  -H "Content-Type: application/json" \
  -d '{"itemId": 10, "quantityAvailable": 30}' | python -m json.tool
```

### Agregar Chaqueta de Cuero (ID=13) al estante SHELF-C1 — 15 unidades

```bash
curl -s -X POST http://localhost:8081/api/v1/warehouse/spots/SHELF-C1/items \
  -H "Content-Type: application/json" \
  -d '{"itemId": 13, "quantityAvailable": 15}' | python -m json.tool
```

### Agregar Adidas Ultraboost (ID=2) al estante SHELF-A2 — 40 unidades

```bash
curl -s -X POST http://localhost:8081/api/v1/warehouse/spots/SHELF-A2/items \
  -H "Content-Type: application/json" \
  -d '{"itemId": 2, "quantityAvailable": 40}' | python -m json.tool
```

---

## Paso 5: Consultar Stock de un Estante

```bash
# Ver todos los items en SHELF-A1
curl -s http://localhost:8081/api/v1/warehouse/spots/SHELF-A1/items | python -m json.tool
```

**✅ Verificar:** Debe mostrar los 2 productos agregados (Nike Air Max + Camiseta Polo) con sus cantidades.

```bash
# Ver todos los spots con sus items
curl -s http://localhost:8081/api/v1/warehouse/spots | python -m json.tool
```

---

## Paso 6: Verificar Estado de Robots

```bash
# Listar todos los robots (seeded con DataInitializer)
curl -s http://localhost:8082/api/robots | python -m json.tool
```

**✅ Verificar:** Debes ver robots con IDs como `robot-001`, `robot-002`, etc. en estado `IDLE`.

```bash
# Ver un robot específico
curl -s http://localhost:8082/api/robots/robot-001 | python -m json.tool
```

---

## Paso 7: Crear una Orden de Pick

### Crear orden: Recoger 2 Nike + 1 Camiseta del estante SHELF-A1 → entregar en DOCK-1

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "pickupSpotCode": "SHELF-A1",
    "deliveryPoint": "DOCK-1",
    "lines": [
      {"sku": "SHO-001", "quantity": 2},
      {"sku": "SHT-001", "quantity": 1}
    ]
  }' | python -m json.tool
```

**✅ Verificar:** Response con:
- `id`: número generado (ej: 1)
- `status`: `"PENDING"`
- `pickupSpotCode`: `"SHELF-A1"`
- `deliveryPoint`: `"DOCK-1"`
- `lines`: array con los 2 productos
- `createdAt`: timestamp

**🔍 Qué pasa internamente:**
1. Se guarda en DB `warehouse_order` + `order_line`
2. Se publica evento NATS `order.created` con el payload JSON
3. `OrderDispatchService` en `ms-robot-status` recibe el evento
4. Busca un robot disponible (estado IDLE)
5. Si encuentra uno → envía comando `GOTO SHELF-A1` via NATS
6. Publica `order.status_changed` → `DISPATCHED`

---

## Paso 8: Crear una Segunda Orden (otro estante)

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "pickupSpotCode": "SHELF-B2",
    "deliveryPoint": "DOCK-2",
    "lines": [
      {"sku": "PNT-001", "quantity": 3}
    ]
  }' | python -m json.tool
```

---

## Paso 9: Consultar Órdenes

```bash
# Listar todas las órdenes
curl -s http://localhost:8081/api/orders | python -m json.tool

# Obtener orden por ID
curl -s http://localhost:8081/api/orders/1 | python -m json.tool

# Filtrar por estado
curl -s http://localhost:8081/api/orders/status/PENDING | python -m json.tool
curl -s http://localhost:8081/api/orders/status/DISPATCHED | python -m json.tool
```

---

## Paso 10: Actualizar Estado de una Orden

### Simular transición: PENDING → PICKING

```bash
curl -s -X PUT http://localhost:8081/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status": "PICKING", "robotId": "robot-001"}' | python -m json.tool
```

### Simular transición: PICKING → DELIVERING

```bash
curl -s -X PUT http://localhost:8081/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status": "DELIVERING"}' | python -m json.tool
```

### Simular transición: DELIVERING → COMPLETED

```bash
curl -s -X PUT http://localhost:8081/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status": "COMPLETED"}' | python -m json.tool
```

**🔍 Cada cambio de estado publica un evento NATS `order.status_changed`.**

---

## Paso 11: Recibir Paquete (Stock-In)

### Flujo de entrada de mercancía: recepción → transporte → almacenamiento en estante

```bash
# Recibir paquete: 10 unidades de Nike Air Max (SHO-001) en punto de recepción RECV-01
curl -s -X POST http://localhost:8081/api/packages/receive \
  -H "Content-Type: application/json" \
  -d '{"sku":"SHO-001","quantity":10,"receptionSpotCode":"RECV-01"}' | python -m json.tool
```

**✅ Verificar:** Response con:
- `id`: número generado
- `status`: `"RECEIVED"`
- `sku`: `"SHO-001"`
- `quantity`: 10
- `receptionSpotCode`: `"RECV-01"`
- `targetSpotCode`: spot destino (auto-asignado)

**🔍 Qué pasa internamente:**
1. Se busca el item por SKU en `inventory_item`
2. Se auto-asigna un spot destino (prefiere spot que ya tenga ese item)
3. Se crea registro en `incoming_package` con status `RECEIVED`
4. Se publica evento NATS `package.received`
5. `PackageDispatchService` en `ms-robot-status` recibe el evento
6. Busca robot disponible → envía `GOTO RECV-01` con misión `STOCK_IN`
7. Publica `package.taken` → estado cambia a `IN_TRANSIT`

### Listar paquetes

```bash
# Todos los paquetes
curl -s http://localhost:8081/api/packages | python -m json.tool

# Por estado
curl -s http://localhost:8081/api/packages/status/RECEIVED | python -m json.tool
curl -s http://localhost:8081/api/packages/status/IN_TRANSIT | python -m json.tool
curl -s http://localhost:8081/api/packages/status/DELIVERED | python -m json.tool

# Por ID
curl -s http://localhost:8081/api/packages/1 | python -m json.tool
```

### Simular entrega completa del robot

Cuando el robot llega al spot destino, publica `package.delivered`:

```bash
# Simular: robot entrega paquete #1 en spot destino
docker exec smartlogistic-nats nats pub package.delivered '{"packageId":1}'
```

**🔍 Resultado:** El stock se incrementa automáticamente en el spot destino.

### Verificar stock actualizado

```bash
# El stock del spot destino debe haber incrementado
curl -s http://localhost:8081/api/v1/warehouse/spots | python -m json.tool
```

### Script de prueba rápida

```bash
scripts\test-package.bat
```

---

## Paso 12: Enviar Comando Directo a un Robot

```bash
# Enviar robot a un spot específico
curl -s -X POST http://localhost:8082/api/robots/robot-001/command \
  -H "Content-Type: application/json" \
  -d '{
    "command": "GOTO",
    "target": "SHELF-A1"
  }' | python -m json.tool
```

---

## Paso 13: Monitorear Telemetría SSE

```bash
# Stream de telemetría de robots (abrir en otra terminal)
curl -s -N http://localhost:8082/api/robots/telemetry
```

---

## Diagrama de Flujo Completo

```
┌─────────────────────────────────────────────────────────────┐
│  PASO 1-2: Layout & Spots                                   │
│  POST /api/v1/warehouse/layouts → Genera spots SHELF-*      │
│  GET  /api/v1/warehouse/spots    → Lista estantes           │
├─────────────────────────────────────────────────────────────┤
│  PASO 3-5: Inventario                                       │
│  POST /api/v1/warehouse/spots/{code}/items → Agregar stock  │
│  GET  /api/v1/warehouse/spots/{code}/items → Ver stock      │
├─────────────────────────────────────────────────────────────┤
│  PASO 6: Robots                                             │
│  GET /api/robots → Lista robots (IDLE)                      │
│  Puerto 8082 (ms-robot-status)                              │
├─────────────────────────────────────────────────────────────┤
│  PASO 7-9: Órdenes                                          │
│  POST /api/orders              → Crea orden + NATS event    │
│  GET  /api/orders              → Lista todas                │
│  GET  /api/orders/status/{s}   → Filtra por estado         │
├─────────────────────────────────────────────────────────────┤
│  PASO 10: Ciclo de vida                                     │
│  PENDING → DISPATCHED → PICKING → DELIVERING → COMPLETED   │
│  PUT /api/orders/{id}/status    → Actualiza + NATS event   │
├─────────────────────────────────────────────────────────────┤
│  PASO 11: Paquetes (Stock-In)                               │
│  POST /api/packages/receive → Crea paquete + NATS event     │
│  GET  /api/packages          → Lista paquetes               │
│  GET  /api/packages/status/{s} → Por estado                 │
├─────────────────────────────────────────────────────────────┤
│  EVENTOS NATS                                               │
│  order.created       → OrderDispatchService → asigna robot  │
│  order.status_changed → Notifica cambio de estado           │
│  package.received    → PackageDispatchService → asigna robot│
│  package.taken       → Marca paquete IN_TRANSIT             │
│  package.delivered   → Incrementa stock en spot destino     │
│  robot.command.{id}  → Envía GOTO al robot                  │
└─────────────────────────────────────────────────────────────┘
```

---

## Datos de Prueba Rápida (Copy-Paste)

```bash
# === Setup completo en 4 comandos ===

# 1. Agregar stock
curl -s -X POST http://localhost:8081/api/v1/warehouse/spots/SHELF-A1/items \
  -H "Content-Type: application/json" \
  -d '{"itemId": 1, "quantityAvailable": 50}'

curl -s -X POST http://localhost:8081/api/v1/warehouse/spots/SHELF-A1/items \
  -H "Content-Type: application/json" \
  -d '{"itemId": 6, "quantityAvailable": 100}'

# 2. Crear orden
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"pickupSpotCode":"SHELF-A1","deliveryPoint":"DOCK-1","lines":[{"sku":"SHO-001","quantity":2},{"sku":"SHT-001","quantity":1}]}'

# 3. Verificar
curl -s http://localhost:8081/api/orders | python -m json.tool

# 4. Avanzar estado
curl -s -X PUT http://localhost:8081/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"PICKING","robotId":"robot-001"}'
```

---

## Troubleshooting

| Problema | Solución |
|----------|----------|
| `Connection refused` en 8081 | `docker compose logs ms-warehouse-core` |
| `Connection refused` en 8082 | `docker compose logs ms-robot-status` |
| Spot no encontrado | Verificar que el layout se creó y los spots existen con `GET /api/v1/warehouse/spots` |
| Item no encontrado | El `itemId` debe ser numérico (1-16), no el SKU |
| Orden no se despacha | Verificar robots IDLE: `GET http://localhost:8082/api/robots` |
| Error 500 al crear orden | `docker compose logs ms-warehouse-core --tail=50` |