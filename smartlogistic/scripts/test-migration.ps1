# ============================================
# 🧪 Script de Prueba - Migración RabbitMQ (PowerShell)
# ============================================
# Ejecutar DESPUÉS de: docker-compose up -d
# Esperar ~90 segundos antes de correr esto.
# Uso: powershell -ExecutionPolicy Bypass -File scripts/test-migration.ps1

$ErrorActionPreference = "SilentlyContinue"

function Pass($msg) { Write-Host "✅ $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "❌ $msg" -ForegroundColor Red }
function Info($msg) { Write-Host "ℹ️  $msg" -ForegroundColor Yellow }

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  🧪 Test: Migración NATS → RabbitMQ" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# ---- 1. Contenedores ----
Write-Host "--- 📦 1. Estado de Contenedores ---" -ForegroundColor White
$containers = @(
    @{Name="smartlogistic-rabbitmq"; Svc="rabbitmq"},
    @{Name="smartlogistic-warehouse-postgres"; Svc="warehouse-db"},
    @{Name="smartlogistic-redis"; Svc="redis"},
    @{Name="smartlogistic-warehouse-core"; Svc="ms-warehouse-core"},
    @{Name="smartlogistic-robot-status"; Svc="ms-robot-status"},
    @{Name="smartlogistic-identity"; Svc="ms-identity"},
    @{Name="smartlogistic-nginx"; Svc="nginx"}
)

foreach ($c in $containers) {
    $status = docker inspect --format='{{.State.Status}}' $c.Name 2>$null
    if ($status -eq "running") {
        Pass "$($c.Name) → running"
    } else {
        Fail "$($c.Name) → $status"
    }
}
Write-Host ""

# ---- 2. RabbitMQ ----
Write-Host "--- 🐰 2. RabbitMQ ---" -ForegroundColor White

$rmqOverview = curl -s -u guest:guest http://localhost:15672/api/overview 2>$null
if ($LASTEXITCODE -eq 0 -or $rmqOverview) {
    Pass "RabbitMQ Management API responde"
} else {
    Fail "RabbitMQ Management API NO responde"
}

$exchanges = curl -s -u guest:guest http://localhost:15672/api/exchanges/%2F 2>$null
if ($exchanges -match "logistics.exchange") {
    Pass "Exchange 'logistics.exchange' existe"
} else {
    Fail "Exchange 'logistics.exchange' NO existe (puede que ms-robot-status no haya arrancado aún)"
}

$queues = curl -s -u guest:guest http://localhost:15672/api/queues/%2F 2>$null
$queueNames = @("robot.status.snapshot", "robot.status.update", "robot.command", "robot.dispatch.order", "robot.dispatch.package", "robot.telemetry")
foreach ($q in $queueNames) {
    if ($queues -match $q) {
        Pass "Queue '$q' existe"
    } else {
        Fail "Queue '$q' NO existe"
    }
}
Write-Host ""

# ---- 3. ms-robot-status ----
Write-Host "--- 🤖 3. ms-robot-status (Redis + API) ---" -ForegroundColor White

$health = curl -s http://localhost:8082/actuator/health 2>$null
if ($health -match "UP") {
    Pass "Health check UP"
} else {
    Fail "Health check NO responde (puede estar arrancando): $health"
}

$robots = curl -s http://localhost:8082/api/robots 2>$null
if ($robots -match "RBT-01") {
    Pass "DataInitializer seeded robots (RBT-01 encontrado)"
    Info "Respuesta: $($robots.Substring(0, [Math]::Min(200, $robots.Length)))..."
} else {
    Fail "No se encontraron robots: $robots"
}

# Update telemetry
Write-Host ""
Info "Enviando telemetría de prueba..."
$telResponse = curl -s -o NUL -w "%{http_code}" -X PUT http://localhost:8082/api/robots/RBT-01/telemetry `
    -H "Content-Type: application/json" `
    -d '{"batteryLevel":77,"currentLocation":"RP-R02-C05","operationalMode":"MOVING"}' 2>$null
if ($telResponse -eq "200") {
    Pass "PUT /api/robots/RBT-01/telemetry → 200"
} else {
    Fail "PUT /api/robots/RBT-01/telemetry → $telResponse"
}

# Verify update
$updated = curl -s http://localhost:8082/api/robots/RBT-01 2>$null
if ($updated -match "MOVING") {
    Pass "RBT-01 actualizado a MOVING"
} else {
    Fail "RBT-01 no se actualizó: $updated"
}
Write-Host ""

# ---- 4. ms-warehouse-core ----
Write-Host "--- 🏭 4. ms-warehouse-core (PostgreSQL + API) ---" -ForegroundColor White

$whHealth = curl -s http://localhost:8081/actuator/health 2>$null
if ($whHealth -match "UP") {
    Pass "Health check UP"
} else {
    Fail "Health check NO responde: $whHealth"
}

$spots = curl -s http://localhost:8081/api/spots 2>$null
if ($spots -match "SP-") {
    Pass "Spots encontrados"
} else {
    Fail "No se encontraron spots"
}

$points = curl -s http://localhost:8081/api/routes/root-points 2>$null
if ($points -match "RP-") {
    Pass "Root points encontrados"
} else {
    Fail "No se encontraron root points"
}

# Test route
$routeResponse = curl -s -o NUL -w "%{http_code}" http://localhost:8081/api/routes/from/RP-R00-C00/to/RP-R05-C09 2>$null
if ($routeResponse -eq "200") {
    Pass "GET /api/routes/from/RP-R00-C00/to/RP-R05-C09 → 200"
} else {
    Fail "GET /api/routes → $routeResponse"
}
Write-Host ""

# ---- 5. Crear Orden (test RabbitMQ end-to-end) ----
Write-Host "--- 📦 5. Crear Orden (test RabbitMQ publisher) ---" -ForegroundColor White

$orderResponse = curl -s -o NUL -w "%{http_code}" -X POST http://localhost:8081/api/orders `
    -H "Content-Type: application/json" `
    -d '{"items":[{"sku":"SKU-ELEC-001","quantity":2}],"targetSpotCode":"SP-A1-01","priority":"NORMAL"}' 2>$null
if ($orderResponse -eq "200" -or $orderResponse -eq "201") {
    Pass "POST /api/orders → $orderResponse"
} else {
    Fail "POST /api/orders → $orderResponse"
}
Write-Host ""

# ---- 6. Sin NATS ----
Write-Host "--- 🚫 6. Verificar NATS eliminado ---" -ForegroundColor White

$natsContainer = docker ps -a --filter "name=nats" --format "{{.Names}}" 2>$null
if ([string]::IsNullOrEmpty($natsContainer)) {
    Pass "No hay contenedores NATS"
} else {
    Fail "Contenedor NATS encontrado: $natsContainer"
}

$natsInCompose = Select-String -Path "smartlogistic\docker-compose.yml" -Pattern "nats:" 2>$null
if ($natsInCompose) {
    Fail "docker-compose.yml todavía tiene servicio nats"
} else {
    Pass "docker-compose.yml no tiene servicio nats"
}
Write-Host ""

# ---- 7. Nginx Gateway ----
Write-Host "--- 🌐 7. Nginx Gateway ---" -ForegroundColor White

$nginxResponse = curl -s -o NUL -w "%{http_code}" http://localhost:8086/api/robots 2>$null
if ($nginxResponse -eq "200") {
    Pass "Nginx → /api/robots → 200"
} else {
    Fail "Nginx → /api/robots → $nginxResponse"
}
Write-Host ""

# ---- 8. Publicar mensaje RabbitMQ directo ----
Write-Host "--- 🐰 8. Test RabbitMQ Directo ---" -ForegroundColor White

$body = @{
    robot_id = "RBT-01"
    battery_level = 88
    current_location = "RP-R03-C07"
    operational_mode = "IDLE"
    timestamp = (Get-Date).ToString("o")
} | ConvertTo-Json

Info "Publicando mensaje directo a RabbitMQ..."
$publishResult = curl -s -u guest:guest -X POST `
    http://localhost:15672/api/exchanges/%2F/logistics.exchange/publish `
    -H "Content-Type: application/json" `
    -d (@{
        properties = @{}
        routing_key = "robot.telemetry"
        payload = $body
        payload_encoding = "string"
    } | ConvertTo-Json -Depth 5) 2>$null

if ($publishResult -match "true") {
    Pass "Mensaje publicado a logistics.exchange con routing_key 'robot.telemetry'"
} else {
    Info "Publish result: $publishResult"
}
Write-Host ""

# ---- Summary ----
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  🏁 Test completado" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "🔗 URLs útiles:" -ForegroundColor White
Write-Host "   RabbitMQ UI:  http://localhost:15672 (guest/guest)" -ForegroundColor Yellow
Write-Host "   Jaeger UI:    http://localhost:16686" -ForegroundColor Yellow
Write-Host "   Grafana:      http://localhost:3001 (admin/admin)" -ForegroundColor Yellow
Write-Host "   Nginx (API):  http://localhost:8086" -ForegroundColor Yellow
Write-Host "   Warehouse:    http://localhost:8081" -ForegroundColor Yellow
Write-Host "   Robot Status: http://localhost:8082" -ForegroundColor Yellow
Write-Host "   Identity:     http://localhost:8084" -ForegroundColor Yellow