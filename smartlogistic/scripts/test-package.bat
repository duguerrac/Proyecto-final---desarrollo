@echo off
echo === Test: Receive Package (Stock-In Flow) ===
echo.

echo [1] Receiving package: SKU-ELEC-001 x10 at reception spot...
curl -s -X POST http://localhost:8081/api/packages/receive -H "Content-Type: application/json" -d "{\"sku\":\"SKU-ELEC-001\",\"quantity\":10,\"receptionSpotCode\":\"RECV-01\"}"
echo.
echo.

echo [2] Listing all packages...
curl -s http://localhost:8081/api/packages
echo.
echo.

echo [3] Packages with status RECEIVED...
curl -s http://localhost:8081/api/packages/status/RECEIVED
echo.
echo.

echo === Package flow test complete ===
echo Check ms-warehouse-core logs for NATS package.received event
echo Check ms-robot-status logs for robot dispatch