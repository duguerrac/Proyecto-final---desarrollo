# ms-robot-status — Guía de Pruebas

## 🚀 Paso 1: Levantar los servicios

```bash
cd smartlogistic
docker compose up -d
```

Espera ~15 segundos y verifica:
```bash
docker compose ps
```

Debes ver 3 containers: `nats`, `redis`, `robot-status` todos en estado **Running/Healthy**.

---

## 🧪 Paso 2: Ejecutar tests automáticos

```bash
smartlogistic\scripts\test-all.bat
```

Esto ejecuta 8 pruebas automáticamente: health check, listar robots, comandos, SSE, etc.

---

## 📋 Paso 3: Probar manualmente (cada endpoint)

### 1. Health Check
```bash
curl.exe -s http://localhost:8082/actuator/health
```
**Expected:** `{"status":"UP"}`

### 2. Listar todos los robots
```bash
curl.exe -s http://localhost:8082/api/robots
```
**Expected:** Array con 5 robots (RBT-01, RBT-02, RBT-03, RBT-04, RBT-LOW)

### 3. Robots disponibles
```bash
curl.exe -s http://localhost:8082/api/robots/available
```
**Expected:** Solo robots con `assignable: true`

### 4. Estado de un robot específico
```bash
curl.exe -s http://localhost:8082/api/robots/RBT-01/status
```
**Expected:** Objeto del robot con id, batteryLevel, operationalMode, etc.

### 5. Enviar comando GO_TO
```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command -H "Content-Type: application/json" -d "{\"type\":\"GO_TO\",\"targetLocation\":\"AISLE-A-01\"}"
```
**Expected:**
```json
{"status":"COMMAND_SENT","commandType":"GO_TO","robotId":"RBT-01","targetLocation":"AISLE-A-01",...}
```

### 6. Enviar comando PICK_UP con item
```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-02/command -H "Content-Type: application/json" -d "{\"type\":\"PICK_UP\",\"targetLocation\":\"SHELF-B-03\",\"itemSku\":\"SKU-12345\"}"
```

### 7. Enviar comando DROP_OFF
```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command -H "Content-Type: application/json" -d "{\"type\":\"DROP_OFF\",\"targetLocation\":\"PACK-STATION-01\"}"
```

### 8. Enviar comando RETURN_DOCK
```bash
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command -H "Content-Type: application/json" -d "{\"type\":\"RETURN_DOCK\"}"
```

### 9. Actualizar estado del robot (PATCH)
```bash
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-01/status -H "Content-Type: application/json" -d "{\"batteryLevel\":65,\"operationalMode\":\"MOVING\",\"currentLocation\":\"AISLE-A-01\"}"
```

### 10. Stream SSE de telemetría
```bash
curl.exe -s -N --max-time 5 http://localhost:8082/api/robots/telemetry/stream
```
**Expected:** `event:connected` + datos en tiempo real cuando lleguen eventos NATS

### 11. Stream SSE de un robot específico
```bash
curl.exe -s -N --max-time 5 http://localhost:8082/api/robots/RBT-01/telemetry/stream
```

---

## 🔍 Ver mensajes NATS en tiempo real

En otra terminal:
```bash
docker exec -it smartlogistic-nats nats sub "smartlogistic.robot.>"
```

Esto te muestra TODOS los mensajes que pasan por NATS (status updates, comandos, telemetría).

---

## 📊 Tipos de Comando Disponibles

| CommandType | Descripción |
|-------------|-------------|
| `GO_TO` | Mover robot a una ubicación |
| `PICK_UP` | Recoger item en ubicación |
| `DROP_OFF` | Dejar item en ubicación |
| `RETURN_DOCK` | Volver al dock de carga |

---

## 🏗️ Flujo de Datos

```
[Unreal Simulation]
       ↕ (NATS: smartlogistic.robot.telemetry.>)
[ms-robot-status] ←→ [Redis]
       ↕ (NATS: smartlogistic.robot.command.{id})
[REST API :8082]
       ↕
[Frontend / Postman]
```

---

## 🛑 Detener todo
```bash
cd smartlogistic
docker compose down
```

## 🔄 Rebuild después de cambios
```bash
cd smartlogistic
docker compose build --no-cache ms-robot-status
docker compose up -d ms-robot-status