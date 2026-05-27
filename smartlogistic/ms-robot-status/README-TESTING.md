# 🧪 ms-robot-status — Guía de Testing

## Requisitos Previos

```bash
# Levantar infraestructura (Redis + NATS + ms-robot-status)
cd smartlogistic
docker compose up -d

# Verificar que los servicios están corriendo
docker compose ps
```

| Servicio | Puerto | URL |
|----------|--------|-----|
| ms-robot-status | 8082 | `http://localhost:8082` |
| Redis | 6379 | `localhost:6379` |
| NATS | 4222 | `nats://localhost:4222` |

---

## 📮 Colección Postman

### Importar en Postman
Crear una nueva colección con variable `{{base_url}}` = `http://localhost:8082`

---

### 1. Obtener todos los robots

```
GET {{base_url}}/api/robots
```

**Response 200:**
```json
[
  {
    "id": "RBT-01",
    "name": "Robot Alpha",
    "batteryLevel": 85,
    "available": true,
    "currentLocation": "DOCK-01",
    "operationalMode": "IDLE",
    "assignable": true
  },
  ...
]
```

---

### 2. Obtener estado de un robot específico

```
GET {{base_url}}/api/robots/RBT-01/status
```

**Response 200:**
```json
{
  "id": "RBT-01",
  "name": "Robot Alpha",
  "batteryLevel": 85,
  "available": true,
  "currentLocation": "DOCK-01",
  "operationalMode": "IDLE",
  "assignable": true
}
```

**Response 404** (robot no existe):
```json
{
  "error": "Robot not found: RBT-99"
}
```

---

### 3. Obtener robots disponibles (assignable)

```
GET {{base_url}}/api/robots/available
```

**Response 200:** Solo robots con `assignable: true` (battery ≥ 15% y available = true)

---

### 4. Crear o actualizar un robot completo

```
POST {{base_url}}/api/robots
Content-Type: application/json
```

**Body:**
```json
{
  "id": "RBT-06",
  "name": "Robot Foxtrot",
  "batteryLevel": 100,
  "available": true,
  "currentLocation": "WAREHOUSE-A",
  "operationalMode": "IDLE"
}
```

**Response 201:**
```json
{
  "id": "RBT-06",
  "name": "Robot Foxtrot",
  "batteryLevel": 100,
  "available": true,
  "currentLocation": "WAREHOUSE-A",
  "operationalMode": "IDLE",
  "assignable": true
}
```

> 💡 Si el robot ya existe, lo sobrescribe completamente.

---

### 5. Actualizar estado parcialmente (PATCH) ⭐

```
PATCH {{base_url}}/api/robots/RBT-01/status
Content-Type: application/json
```

**Body (campos opcionales — solo los que quieras cambiar):**
```json
{
  "operationalMode": "MOVING",
  "batteryLevel": 60,
  "currentLocation": "AISLE-C-05"
}
```

**Response 200:**
```json
{
  "id": "RBT-01",
  "name": "Robot Alpha",
  "batteryLevel": 60,
  "available": true,
  "currentLocation": "AISLE-C-05",
  "operationalMode": "MOVING",
  "assignable": true
}
```

> 🔔 **Este es el endpoint clave** — cada PATCH publica un evento NATS que UE5 recibe en tiempo real.

---

## 🎮 Valores Válidos

### `operationalMode`
| Valor | Descripción | Color en UE5 |
|-------|-------------|--------------|
| `IDLE` | En espera | 🟦 Azul |
| `MOVING` | En movimiento | 🟩 Verde |
| `PICKING` | Recogiendo carga | 🟨 Amarillo |
| `CHARGING` | Cargando batería | 🟧 Naranja |
| `OFFLINE` | Fuera de línea | ⬛ Gris |

### `batteryLevel`
- Rango: `0` – `100`
- `assignable = false` cuando battery < 15%

### Robots precargados (seed)
| ID | Nombre | Battery | Modo | Location |
|----|--------|---------|------|----------|
| RBT-01 | Robot Alpha | 85% | IDLE | DOCK-01 |
| RBT-02 | Robot Beta | 72% | IDLE | AISLE-A-03 |
| RBT-03 | Robot Gamma | 8% | CHARGING | CHARGE-STATION |
| RBT-04 | Robot Delta | 50% | IDLE | DOCK-02 |
| RBT-LOW | Robot Low Battery | 10% | IDLE | AISLE-B-01 |

---

## 🖥️ Testing con cURL (PowerShell)

```powershell
# GET all robots
curl.exe -s http://localhost:8082/api/robots

# GET specific robot
curl.exe -s http://localhost:8082/api/robots/RBT-01/status

# GET available robots
curl.exe -s http://localhost:8082/api/robots/available

# POST new robot
curl.exe -s -X POST http://localhost:8082/api/robots -H "Content-Type: application/json" -d '{\"id\":\"RBT-06\",\"name\":\"Robot Foxtrot\",\"batteryLevel\":100,\"available\":true,\"currentLocation\":\"DOCK-03\",\"operationalMode\":\"IDLE\"}'

# PATCH: Cambiar a MOVING
curl.exe -s -X PATCH "http://localhost:8082/api/robots/RBT-01/status" -H "Content-Type: application/json" -d '{\"operationalMode\":\"MOVING\",\"batteryLevel\":60}'

# PATCH: Cambiar a PICKING
curl.exe -s -X PATCH "http://localhost:8082/api/robots/RBT-02/status" -H "Content-Type: application/json" -d '{\"operationalMode\":\"PICKING\",\"batteryLevel\":45}'

# PATCH: Batería baja
curl.exe -s -X PATCH "http://localhost:8082/api/robots/RBT-01/status" -H "Content-Type: application/json" -d '{\"batteryLevel\":5}'

# PATCH: Múltiples campos
curl.exe -s -X PATCH "http://localhost:8082/api/robots/RBT-03/status" -H "Content-Type: application/json" -d '{\"operationalMode\":\"IDLE\",\"batteryLevel\":95,\"currentLocation\":\"DOCK-01\"}'
```

---

## 🖥️ Testing con cURL (Git Bash / Linux)

```bash
# GET all
curl -s http://localhost:8082/api/robots | jq .

# PATCH
curl -s -X PATCH http://localhost:8082/api/robots/RBT-01/status \
  -H "Content-Type: application/json" \
  -d '{"operationalMode":"MOVING","batteryLevel":60}' | jq .
```

---

## 🖥️ Testing con HTTPie

```bash
# Install: pip install httpie

# GET all
http GET :8082/api/robots

# PATCH
http PATCH :8082/api/robots/RBT-01/status operationalMode=MOVING batteryLevel:=60
```

---

## 🔔 Eventos NATS

Cada operación que modifica un robot (POST o PATCH) publica un evento en NATS:

**Subject:** `smartlogistic.robot.status.update`
```json
{
  "event": "STATUS_UPDATE",
  "timestamp": "2026-05-27T18:20:07.838Z",
  "source": "ms-robot-status",
  "robot": {
    "id": "RBT-01",
    "name": "Robot Alpha",
    "batteryLevel": 60,
    "available": true,
    "currentLocation": "DOCK-01",
    "operationalMode": "MOVING",
    "assignable": true
  }
}
```

**Al iniciar** se publica un batch snapshot en `smartlogistic.robot.status.batch`:
```json
{
  "event": "STATUS_BATCH",
  "timestamp": "2026-05-27T18:00:00.000Z",
  "source": "ms-robot-status",
  "count": 5,
  "robots": [ ... ]
}
```

---

## 🔍 Verificar NATS directamente

```bash
# Suscribirse a eventos desde CLI
docker exec -it smartlogistic-nats-1 nats sub "smartlogistic.robot.status.>"

# Publicar un evento de prueba
docker exec -it smartlogistic-nats-1 nats pub smartlogistic.robot.status.update '{"event":"STATUS_UPDATE","robot":{"id":"TEST","name":"Test","batteryLevel":50,"available":true,"currentLocation":"TEST","operationalMode":"IDLE","assignable":true}}'
```

---

## 🎯 Flujo de Testing Recomendado

1. **GET /api/robots** → Verificar 5 robots seedeados
2. **PATCH RBT-01 → MOVING** → Verificar cambio + evento NATS
3. **PATCH RBT-02 → PICKING** → Verificar en UE5 (cambia color)
4. **PATCH RBT-03 → batteryLevel: 5** → Verificar assignable = false
5. **GET /api/robots/available** → Solo robots con battery ≥ 15%
6. **POST nuevo robot RBT-06** → Verificar que aparece
7. **GET /api/robots/RBT-99/status** → Verificar 404

---

## 🏥 Health Check

```bash
# Actuator health
curl.exe -s http://localhost:8082/actuator/health

# Prometheus metrics
curl.exe -s http://localhost:8082/actuator/prometheus