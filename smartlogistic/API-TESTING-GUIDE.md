# SmartLogistic — Guía Completa de Pruebas API

> Guía de prueba de todos los flujos del sistema usando `curl`.

---

## 📋 Tabla de Contenidos

- [1. Levantar el Sistema](#1-levantar-el-sistema)
- [2. ms-robot-status (Puerto 8082)](#2-ms-robot-status-puerto-8082)
- [3. ms-warehouse-core (Puerto 8081)](#3-ms-warehouse-core-puerto-8081)
- [4. ms-identity (Puerto 8080)](#4-ms-identity-puerto-8080)
- [5. Flujos Integrados E2E](#5-flujos-integrados-e2e)
- [6. Monitoreo y Observabilidad](#6-monitoreo-y-observabilidad)
- [7. Verificación NATS](#7-verificación-nats)

---

## 1. Levantar el Sistema

```bash
cd smartlogistic
docker compose up -d
```

Esperar ~20 segundos y verificar:

```bash
docker compose ps
```

Todos los servicios deben estar **Running/Healthy**.

---

## 2. ms-robot-status (Puerto 8082)

### 2.1 Health Check

```bash
curl.exe -s http://localhost:8082/actuator/health | python -m json.tool
```

**Expected:**
```json
{ "status": "UP" }
```

---

### 2.2 Listar todos los robots

```bash
curl.exe -s http://localhost:8082/api/robots | python -m json.tool
```

**Expected:** Array con 5 robots (RBT-01, RBT-02, RBT-03, RBT-04, RBT-LOW).

**Validar:**
- Cada robot tiene: `id`, `batteryLevel`, `operationalMode`, `assignable`, `currentLocation`
- `operationalMode` es uno de: `IDLE`, `MOVING`, `CHARGING`, `PICKING`, `DROPPING`, `ERROR`
- `batteryLevel` está entre 0 y 100

---

### 2.3 Robots disponibles (assignable = true)

```bash
curl.exe -s http://localhost:8082/api/robots/available | python -m json.tool
```

**Expected:** Solo robots con `assignable: true`.

**Validar:**
- RBT-LOW debería tener `assignable: false` (batería baja)
- Todos los demás deberían estar disponibles

---

### 2.4 Estado de un robot específico

```bash
curl.exe -s http://localhost:8082/api/robots/RBT-01/status | python -m json.tool
```

**Expected:**
```json
{
  "id": "RBT-01",
  "batteryLevel": 100,
  "operationalMode": "IDLE",
  "assignable": true,
  "currentLocation": "DOCK-01"
}
```

**Probar robot inexistente:**
```bash
curl.exe -s http://localhost:8082/api/robots/RBT-99/status
```

**Expected:** `404 Not Found`

---

### 2.5 Enviar comando GO_TO

```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"AISLE-A-01\"}" | python -m json.tool
```

**Expected:**
```json
{
  "status": "COMMAND_SENT",
  "commandType": "GO_TO",
  "robotId": "RBT-01",
  "targetLocation": "AISLE-A-01"
}
```

---

### 2.6 Enviar comando PICK_UP

```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-02/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"PICK_UP\",\"targetLocation\":\"SHELF-B-03\",\"itemSku\":\"SKU-12345\"}" | python -m json.tool
```

**Expected:**
```json
{
  "status": "COMMAND_SENT",
  "commandType": "PICK_UP",
  "robotId": "RBT-02",
  "targetLocation": "SHELF-B-03",
  "itemSku": "SKU-12345"
}
```

---

### 2.7 Enviar comando DROP_OFF

```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"DROP_OFF\",\"targetLocation\":\"PACK-STATION-01\"}" | python -m json.tool
```

**Expected:**
```json
{
  "status": "COMMAND_SENT",
  "commandType": "DROP_OFF",
  "robotId": "RBT-01",
  "targetLocation": "PACK-STATION-01"
}
```

---

### 2.8 Enviar comando RETURN_DOCK

```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-03/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"RETURN_DOCK\"}" | python -m json.tool
```

**Expected:**
```json
{
  "status": "COMMAND_SENT",
  "commandType": "RETURN_DOCK",
  "robotId": "RBT-03"
}
```

---

### 2.9 Enviar comando a robot inexistente (Error)

```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-99/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"AISLE-A-01\"}"
```

**Expected:** `404 Not Found` o mensaje de error indicando robot no encontrado.

---

### 2.10 Enviar comando inválido (Error)

```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"INVALID_COMMAND\"}"
```

**Expected:** `400 Bad Request` o error de validación.

---

### 2.11 Actualizar estado del robot (PATCH)

```bash
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-01/status ^
  -H "Content-Type: application/json" ^
  -d "{\"batteryLevel\":65,\"operationalMode\":\"MOVING\",\"currentLocation\":\"AISLE-A-01\"}" | python -m json.tool
```

**Expected:** Robot actualizado con los nuevos valores.

**Verificar que persistió:**
```bash
curl.exe -s http://localhost:8082/api/robots/RBT-01/status | python -m json.tool
```

**Expected:** `batteryLevel: 65`, `operationalMode: "MOVING"`, `currentLocation: "AISLE-A-01"`

---

### 2.12 Actualizar parcialmente (solo batería)

```bash
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-02/status ^
  -H "Content-Type: application/json" ^
  -d "{\"batteryLevel\":30}" | python -m json.tool
```

**Expected:** Solo `batteryLevel` cambió, los demás campos permanecen iguales.

---

### 2.13 Stream SSE — Telemetría global

```bash
curl.exe -s -N --max-time 10 http://localhost:8082/api/robots/telemetry/stream
```

**Expected:**
```
event: connected
data: {"message":"Connected to robot telemetry stream"}
```

Luego, si hay eventos NATS, llegarán datos adicionales en tiempo real.

---

### 2.14 Stream SSE — Telemetría de un robot específico

```bash
curl.exe -s -N --max-time 10 http://localhost:8082/api/robots/RBT-01/telemetry/stream
```

**Expected:** Eventos SSE filtrados solo para RBT-01.

---

## 3. ms-warehouse-core (Puerto 8081)

### 3.1 Health Check

```bash
curl.exe -s http://localhost:8081/actuator/health | python -m json.tool
```

**Expected:** `{ "status": "UP" }`

---

### 3.2 Obtener el layout del warehouse

```bash
curl.exe -s http://localhost:8081/api/warehouse/layout | python -m json.tool
```

**Expected:** Estructura del warehouse con spots, aisles, docks, etc.

---

### 3.3 Listar spots

```bash
curl.exe -s http://localhost:8081/api/warehouse/spots | python -m json.tool
```

**Expected:** Array de spots con sus propiedades (tipo, ubicación, estado).

---

### 3.4 Obtener spot por ID

```bash
curl.exe -s http://localhost:8081/api/warehouse/spots/SPOT-01 | python -m json.tool
```

---

### 3.5 Calcular ruta (Route Planning)

```bash
curl.exe -s -X POST http://localhost:8081/api/routes/calculate ^
  -H "Content-Type: application/json" ^
  -d "{\"fromSpotId\":\"DOCK-01\",\"toSpotId\":\"SHELF-A-03\"}" | python -m json.tool
```

**Expected:** Objeto con la ruta calculada, lista de spots intermedios, distancia total.

---

### 3.6 Calcular ruta con puntos inválidos

```bash
curl.exe -s -X POST http://localhost:8081/api/routes/calculate ^
  -H "Content-Type: application/json" ^
  -d "{\"fromSpotId\":\"INVALID\",\"toSpotId\":\"ALSO-INVALID\"}"
```

**Expected:** `404 Not Found` o error indicando spots no encontrados.

---

### 3.7 Listar inventory items

```bash
curl.exe -s http://localhost:8081/api/warehouse/inventory | python -m json.tool
```

**Expected:** Array de items en inventario.

---

### 3.8 Crear/Actualizar inventory item

```bash
curl.exe -s -X POST http://localhost:8081/api/warehouse/inventory ^
  -H "Content-Type: application/json" ^
  -d "{\"sku\":\"SKU-TEST-001\",\"spotId\":\"SHELF-A-01\",\"quantity\":10,\"name\":\"Test Item\"}" | python -m json.tool
```

---

### 3.9 Órdenes

```bash
curl.exe -s http://localhost:8081/api/orders | python -m json.tool
```

---

### 3.10 Crear orden

```bash
curl.exe -s -X POST http://localhost:8081/api/orders ^
  -H "Content-Type: application/json" ^
  -d "{\"orderId\":\"ORD-TEST-001\",\"items\":[{\"sku\":\"SKU-12345\",\"quantity\":2}]}" | python -m json.tool
```

---

## 4. ms-identity (Puerto 8080)

### 4.1 Health Check

```bash
curl.exe -s http://localhost:8080/actuator/health | python -m json.tool
```

**Expected:** `{ "status": "UP" }`

---

### 4.2 Login

```bash
curl.exe -s -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}" | python -m json.tool
```

**Expected:** Token JWT o sesión activa.

---

### 4.3 Login con credenciales inválidas

```bash
curl.exe -s -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"admin\",\"password\":\"wrong\"}"
```

**Expected:** `401 Unauthorized`

---

### 4.4 Registro (si aplica)

```bash
curl.exe -s -X POST http://localhost:8080/api/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"testuser\",\"email\":\"test@test.com\",\"password\":\"Test1234\"}" | python -m json.tool
```

---

### 4.5 Verificar token / Perfil

```bash
curl.exe -s http://localhost:8080/api/auth/me ^
  -H "Authorization: Bearer <TOKEN_OBTENIDO_EN_LOGIN>" | python -m json.tool
```

---

## 5. Flujos Integrados E2E

### Flujo 1: Picking completo (orden → robot →_pickup → entrega)

```bash
echo "=== PASO 1: Verificar robots disponibles ==="
curl.exe -s http://localhost:8082/api/robots/available | python -m json.tool

echo ""
echo "=== PASO 2: Enviar robot a recoger item ==="
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"SHELF-A-01\"}" | python -m json.tool

echo ""
echo "=== PASO 3: Recoger item ==="
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"PICK_UP\",\"targetLocation\":\"SHELF-A-01\",\"itemSku\":\"SKU-12345\"}" | python -m json.tool

echo ""
echo "=== PASO 4: Llevar a estación de packing ==="
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"PACK-STATION-01\"}" | python -m json.tool

echo ""
echo "=== PASO 5: Dejar item ==="
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"DROP_OFF\",\"targetLocation\":\"PACK-STATION-01\"}" | python -m json.tool

echo ""
echo "=== PASO 6: Volver al dock ==="
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"RETURN_DOCK\"}" | python -m json.tool

echo ""
echo "=== PASO 7: Verificar estado final ==="
curl.exe -s http://localhost:8082/api/robots/RBT-01/status | python -m json.tool
```

---

### Flujo 2: Simulación de batería baja

```bash
echo "=== PASO 1: Poner batería en nivel crítico ==="
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-04/status ^
  -H "Content-Type: application/json" ^
  -d "{\"batteryLevel\":5,\"operationalMode\":\"CHARGING\"}" | python -m json.tool

echo ""
echo "=== PASO 2: Verificar que ya no está disponible ==="
curl.exe -s http://localhost:8082/api/robots/available | python -m json.tool

echo ""
echo "=== PASO 3: Intentar enviar comando (debería fallar o ser rechazado) ==="
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-04/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"AISLE-B-02\"}"
```

---

### Flujo 3: Múltiples robots concurrentes

```bash
echo "=== Enviar 3 robots a diferentes ubicaciones ==="

echo "Robot 1 → Aisle A"
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"AISLE-A-01\"}"

echo ""
echo "Robot 2 → Aisle B"
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-02/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"AISLE-B-02\"}"

echo ""
echo "Robot 3 → Aisle C"
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-03/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"AISLE-C-03\"}"

echo ""
echo "=== Verificar estado de todos los robots ==="
curl.exe -s http://localhost:8082/api/robots | python -m json.tool
```

---

### Flujo 4: Route Planning + Robot Dispatch

```bash
echo "=== PASO 1: Calcular ruta óptima ==="
curl.exe -s -X POST http://localhost:8081/api/routes/calculate ^
  -H "Content-Type: application/json" ^
  -d "{\"fromSpotId\":\"DOCK-01\",\"toSpotId\":\"SHELF-B-03\"}" | python -m json.tool

echo ""
echo "=== PASO 2: Enviar robot por la ruta ==="
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"GO_TO\",\"targetLocation\":\"SHELF-B-03\"}" | python -m json.tool

echo ""
echo "=== PASO 3: Monitorear telemetría ==="
curl.exe -s -N --max-time 5 http://localhost:8082/api/robots/RBT-01/telemetry/stream
```

---

## 6. Monitoreo y Observabilidad

### 6.1 Prometheus Metrics — Robot Status

```bash
curl.exe -s http://localhost:8082/actuator/prometheus | findstr robot
```

### 6.2 Prometheus Metrics — Warehouse Core

```bash
curl.exe -s http://localhost:8081/actuator/prometheus | findstr warehouse
```

### 6.3 Info del servicio

```bash
curl.exe -s http://localhost:8082/actuator/info | python -m json.tool
curl.exe -s http://localhost:8081/actuator/info | python -m json.tool
curl.exe -s http://localhost:8080/actuator/info | python -m json.tool
```

### 6.4 Env vars / Config

```bash
curl.exe -s http://localhost:8082/actuator/env | python -m json.tool
```

---

## 7. Verificación NATS

### 7.1 Suscribirse a todos los mensajes de robots

```bash
docker exec -it smartlogistic-nats nats sub "smartlogistic.robot.>"
```

### 7.2 Suscribirse solo a comandos

```bash
docker exec -it smartlogistic-nats nats sub "smartlogistic.robot.command.>"
```

### 7.3 Suscribirse solo a telemetría

```bash
docker exec -it smartlogistic-nats nats sub "smartlogistic.robot.telemetry.>"
```

### 7.4 Suscribirse a eventos de warehouse

```bash
docker exec -it smartlogistic-nats nats sub "smartlogistic.warehouse.>"
```

### 7.5 Publicar un mensaje de prueba (simular telemetría)

```bash
docker exec -it smartlogistic-nats nats pub "smartlogistic.robot.telemetry.RBT-01" "{\"robotId\":\"RBT-01\",\"batteryLevel\":88,\"operationalMode\":\"MOVING\",\"currentLocation\":\"AISLE-A-02\"}"
```

**Luego verificar en el SSE stream que el dato llegó:**
```bash
curl.exe -s -N --max-time 5 http://localhost:8082/api/robots/RBT-01/telemetry/stream
```

---

## 📊 Resumen de Endpoints por Servicio

| Servicio | Puerto | Endpoint | Método | Descripción |
|----------|--------|----------|--------|-------------|
| **robot-status** | 8082 | `/actuator/health` | GET | Health check |
| **robot-status** | 8082 | `/api/robots` | GET | Listar todos los robots |
| **robot-status** | 8082 | `/api/robots/available` | GET | Robots disponibles |
| **robot-status** | 8082 | `/api/robots/{id}/status` | GET | Estado de un robot |
| **robot-status** | 8082 | `/api/robots/{id}/status` | PATCH | Actualizar estado |
| **robot-status** | 8082 | `/api/robots/{id}/command` | POST | Enviar comando |
| **robot-status** | 8082 | `/api/robots/telemetry/stream` | GET(SSE) | Stream telemetría global |
| **robot-status** | 8082 | `/api/robots/{id}/telemetry/stream` | GET(SSE) | Stream telemetría robot |
| **warehouse-core** | 8081 | `/actuator/health` | GET | Health check |
| **warehouse-core** | 8081 | `/api/warehouse/layout` | GET | Layout del warehouse |
| **warehouse-core** | 8081 | `/api/warehouse/spots` | GET | Listar spots |
| **warehouse-core** | 8081 | `/api/warehouse/inventory` | GET | Listar inventario |
| **warehouse-core** | 8081 | `/api/routes/calculate` | POST | Calcular ruta |
| **warehouse-core** | 8081 | `/api/orders` | GET | Listar órdenes |
| **warehouse-core** | 8081 | `/api/orders` | POST | Crear orden |
| **identity** | 8080 | `/actuator/health` | GET | Health check |
| **identity** | 8080 | `/api/auth/login` | POST | Login |
| **identity** | 8080 | `/api/auth/register` | POST | Registro |
| **identity** | 8080 | `/api/auth/me` | GET | Perfil (con token) |

---

## 📋 Tipos de Comando Robot

| CommandType | Requiere targetLocation | Requiere itemSku | Descripción |
|-------------|------------------------|------------------|-------------|
| `GO_TO` | ✅ | ❌ | Mover robot a ubicación |
| `PICK_UP` | ✅ | ✅ | Recoger item en ubicación |
| `DROP_OFF` | ✅ | ❌ | Dejar item en ubicación |
| `RETURN_DOCK` | ❌ | ❌ | Volver al dock de carga |

---

## 📋 Operational Modes

| Mode | Descripción |
|------|-------------|
| `IDLE` | Robot en espera |
| `MOVING` | Robot en movimiento |
| `CHARGING` | Robot cargando batería |
| `PICKING` | Robot recogiendo item |
| `DROPPING` | Robot dejando item |
| `ERROR` | Robot en estado de error |

---

## 🛑 Detener todo

```bash
cd smartlogistic
docker compose down
```

## 🔄 Rebuild después de cambios

```bash
cd smartlogistic
docker compose build --no-cache
docker compose up -d