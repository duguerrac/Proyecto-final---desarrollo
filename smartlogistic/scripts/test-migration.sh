#!/bin/bash
# ============================================
# 🧪 Script de Prueba - Migración RabbitMQ
# ============================================
# Ejecutar DESPUÉS de: docker-compose up -d
# Esperar ~90 segundos antes de correr esto.
# Uso: bash scripts/test-migration.sh

set -e
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ $1${NC}"; }
fail() { echo -e "${RED}❌ $1${NC}"; }
info() { echo -e "${YELLOW}ℹ️  $1${NC}"; }

echo "=========================================="
echo "  🧪 Test: Migración NATS → RabbitMQ"
echo "=========================================="
echo ""

# ---- 1. Contenedores ----
echo "--- 📦 1. Estado de Contenedores ---"
for svc in rabbitmq warehouse-db robot-redis ms-warehouse-core ms-robot-status ms-identity nginx; do
  STATUS=$(docker inspect --format='{{.State.Status}}' smartlogistic-${svc/rabbitmq/rabbitmq} 2>/dev/null || echo "not found")
  # Fix container names
  case $svc in
    rabbitmq)      CNAME="smartlogistic-rabbitmq" ;;
    warehouse-db)  CNAME="smartlogistic-warehouse-postgres" ;;
    robot-redis)   CNAME="smartlogistic-redis" ;;
    ms-warehouse-core) CNAME="smartlogistic-warehouse-core" ;;
    ms-robot-status) CNAME="smartlogistic-robot-status" ;;
    ms-identity)   CNAME="smartlogistic-identity" ;;
    nginx)         CNAME="smartlogistic-nginx" ;;
  esac
  STATUS=$(docker inspect --format='{{.State.Status}}' "$CNAME" 2>/dev/null || echo "not found")
  if [ "$STATUS" = "running" ]; then
    pass "$CNAME → running"
  else
    fail "$CNAME → $STATUS"
  fi
done
echo ""

# ---- 2. RabbitMQ ----
echo "--- 🐰 2. RabbitMQ ---"

# Check RabbitMQ is responsive
if curl -s -u guest:guest http://localhost:15672/api/overview > /dev/null 2>&1; then
  pass "RabbitMQ Management API responde"
else
  fail "RabbitMQ Management API NO responde"
fi

# Check exchanges
EXCHANGES=$(curl -s -u guest:guest http://localhost:15672/api/exchanges/%2F 2>/dev/null)
if echo "$EXCHANGES" | grep -q "logistics.exchange"; then
  pass "Exchange 'logistics.exchange' existe"
else
  fail "Exchange 'logistics.exchange' NO existe"
fi

# Check queues
QUEUES=$(curl -s -u guest:guest http://localhost:15672/api/queues/%2F 2>/dev/null)
for Q in robot.status.snapshot robot.status.update robot.command robot.dispatch.order robot.dispatch.package robot.telemetry; do
  if echo "$QUEUES" | grep -q "$Q"; then
    pass "Queue '$Q' existe"
  else
    fail "Queue '$Q' NO existe"
  fi
done
echo ""

# ---- 3. ms-robot-status ----
echo "--- 🤖 3. ms-robot-status (Redis + API) ---"

# Health check
HEALTH=$(curl -s http://localhost:8082/actuator/health 2>/dev/null)
if echo "$HEALTH" | grep -q "UP"; then
  pass "Health check UP"
else
  fail "Health check NO responde: $HEALTH"
fi

# Get all robots
ROBOTS=$(curl -s http://localhost:8082/api/robots 2>/dev/null)
if echo "$ROBOTS" | grep -q "RBT-01"; then
  pass "DataInitializer seeded robots (RBT-01 encontrado)"
  echo "$ROBOTS" | python3 -m json.tool 2>/dev/null || echo "$ROBOTS"
else
  fail "No se encontraron robots: $ROBOTS"
fi

# Update telemetry (simulate UE5)
echo ""
info "Enviando telemetría de prueba..."
TEL_RESULT=$(curl -s -w "\n%{http_code}" -X PUT http://localhost:8082/api/robots/RBT-01/telemetry \
  -H "Content-Type: application/json" \
  -d '{"batteryLevel":77,"currentLocation":"RP-R02-C05","operationalMode":"MOVING"}' 2>/dev/null)
HTTP_CODE=$(echo "$TEL_RESULT" | tail -1)
if [ "$HTTP_CODE" = "200" ]; then
  pass "PUT /api/robots/RBT-01/telemetry → 200"
else
  fail "PUT /api/robots/RBT-01/telemetry → $HTTP_CODE"
fi

# Verify update
UPDATED=$(curl -s http://localhost:8082/api/robots/RBT-01 2>/dev/null)
if echo "$UPDATED" | grep -q "MOVING"; then
  pass "RBT-01 actualizado a MOVING"
else
  fail "RBT-01 no se actualizó: $UPDATED"
fi
echo ""

# ---- 4. ms-warehouse-core ----
echo "--- 🏭 4. ms-warehouse-core (PostgreSQL + API) ---"

# Health check
WH_HEALTH=$(curl -s http://localhost:8081/actuator/health 2>/dev/null)
if echo "$WH_HEALTH" | grep -q "UP"; then
  pass "Health check UP"
else
  fail "Health check NO responde: $WH_HEALTH"
fi

# Get spots
SPOTS=$(curl -s http://localhost:8081/api/spots 2>/dev/null)
if echo "$SPOTS" | grep -q "SP-"; then
  pass "Spots encontrados"
  SPOT_COUNT=$(echo "$SPOTS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "?")
  info "Total spots: $SPOT_COUNT"
else
  fail "No se encontraron spots: $SPOTS"
fi

# Get root points
POINTS=$(curl -s http://localhost:8081/api/routes/root-points 2>/dev/null)
if echo "$POINTS" | grep -q "RP-"; then
  pass "Root points encontrados"
  RP_COUNT=$(echo "$POINTS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "?")
  info "Total root points: $RP_COUNT"
else
  fail "No se encontraron root points: $POINTS"
fi

# Test route calculation
ROUTE=$(curl -s -w "\n%{http_code}" http://localhost:8081/api/routes/from/RP-R00-C00/to/RP-R05-C09 2>/dev/null)
ROUTE_CODE=$(echo "$ROUTE" | tail -1)
if [ "$ROUTE_CODE" = "200" ]; then
  pass "GET /api/routes/from/RP-R00-C00/to/RP-R05-C09 → 200"
else
  fail "GET /api/routes → $ROUTE_CODE"
fi
echo ""

# ---- 5. Crear Orden (test RabbitMQ end-to-end) ----
echo "--- 📦 5. Crear Orden (test RabbitMQ publisher) ---"
ORDER_RESULT=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{"sku":"SKU-ELEC-001","quantity":2}],
    "targetSpotCode":"SP-A1-01",
    "priority":"NORMAL"
  }' 2>/dev/null)
ORDER_CODE=$(echo "$ORDER_RESULT" | tail -1)
if [ "$ORDER_CODE" = "200" ] || [ "$ORDER_CODE" = "201" ]; then
  pass "POST /api/orders → $ORDER_CODE"
else
  fail "POST /api/orders → $ORDER_CODE"
fi
echo ""

# ---- 6. Sin NATS ----
echo "--- 🚫 6. Verificar NATS eliminado ---"
NATS_CONTAINER=$(docker ps -a --filter "name=nats" --format "{{.Names}}" 2>/dev/null)
if [ -z "$NATS_CONTAINER" ]; then
  pass "No hay contenedores NATS"
else
  fail "Contenedor NATS encontrado: $NATS_CONTAINER"
fi

# Check no NATS in docker-compose
if grep -q "nats:" smartlogistic/docker-compose.yml 2>/dev/null; then
  fail "docker-compose.yml todavía tiene servicio nats"
else
  pass "docker-compose.yml no tiene servicio nats"
fi
echo ""

# ---- 7. Nginx Gateway ----
echo "--- 🌐 7. Nginx Gateway ---"
NGINX_HEALTH=$(curl -s -w "%{http_code}" http://localhost:8086/api/robots -o /dev/null 2>/dev/null)
if [ "$NGINX_HEALTH" = "200" ]; then
  pass "Nginx → /api/robots → 200"
else
  fail "Nginx → /api/robots → $NGINX_HEALTH"
fi
echo ""

echo "=========================================="
echo "  🏁 Test completado"
echo "=========================================="
echo ""
echo "🔗 URLs útiles:"
echo "   RabbitMQ UI:  http://localhost:15672 (guest/guest)"
echo "   Jaeger UI:    http://localhost:16686"
echo "   Grafana:      http://localhost:3001 (admin/admin)"
echo "   Nginx (API):  http://localhost:8086"
echo "   Warehouse:    http://localhost:8081"
echo "   Robot Status: http://localhost:8082"
echo "   Identity:     http://localhost:8084"