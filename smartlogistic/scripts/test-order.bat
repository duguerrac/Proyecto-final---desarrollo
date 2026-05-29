@echo off
echo === Create Order ===
curl -s -X POST http://localhost:8081/api/orders -H "Content-Type: application/json" -d "{\"pickupSpotCode\":\"SHELF-A1\",\"deliveryPoint\":\"DOCK-1\",\"lines\":[{\"sku\":\"SHO-001\",\"quantity\":2},{\"sku\":\"SHT-001\",\"quantity\":1}]}"
echo.
echo.
echo === List All Orders ===
curl -s http://localhost:8081/api/orders
echo.
echo.
echo === Get Pending Orders ===
curl -s http://localhost:8081/api/orders/status/PENDING
echo.