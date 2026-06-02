@echo off
echo ============================================
echo   SmartLogistics - Robot Management Tests
echo ============================================
echo.

:: Check service is up
echo [1/12] Health check...
curl.exe -s http://localhost:8082/actuator/health
echo.
echo.

:: List all robots
echo [2/12] GET /api/robots - List all robots:
curl.exe -s http://localhost:8082/api/robots
echo.
echo.

:: Reset ALL robots to IDLE so they can accept commands
echo [3/12] PATCH - Reset RBT-01 to IDLE:
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-01/status -H "Content-Type: application/json" -d "{\"batteryLevel\":90,\"operationalMode\":\"IDLE\",\"available\":true}"
echo.
echo.

echo [4/12] PATCH - Reset RBT-02 to IDLE:
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-02/status -H "Content-Type: application/json" -d "{\"batteryLevel\":72,\"operationalMode\":\"IDLE\",\"available\":true}"
echo.
echo.

echo [5/12] PATCH - Reset RBT-04 to IDLE:
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-04/status -H "Content-Type: application/json" -d "{\"batteryLevel\":50,\"operationalMode\":\"IDLE\",\"available\":true}"
echo.
echo.

:: Get available robots (should show robots now)
echo [6/12] GET /api/robots/available - Available robots:
curl.exe -s http://localhost:8082/api/robots/available
echo.
echo.

:: Send GO_TO command to RBT-01 (coordinates: x=1000, y=500, z=0)
echo [7/12] POST - Send GO_TO to RBT-01 (1000,500,0):
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-01/command -H "Content-Type: application/json" -d "{\"type\":\"GO_TO\",\"targetLocation\":\"1000,500,0\"}"
echo.
echo.

:: Send PICK_UP command to RBT-02 (coordinates: x=-500, y=1200, z=0)
echo [8/12] POST - Send PICK_UP to RBT-02 (-500,1200,0) with SKU:
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-02/command -H "Content-Type: application/json" -d "{\"type\":\"PICK_UP\",\"targetLocation\":\"-500,1200,0\",\"itemSku\":\"SKU-12345\"}"
echo.
echo.

:: Send RETURN_DOCK to RBT-04 (dock at origin)
echo [9/12] POST - Send RETURN_DOCK to RBT-04 (0,0,0):
curl.exe -s -X POST http://localhost:8082/api/robots/RBT-04/command -H "Content-Type: application/json" -d "{\"type\":\"RETURN_DOCK\",\"targetLocation\":\"0,0,0\"}"
echo.
echo.

:: Wait for robots to reach their destinations
echo [10/12] Waiting 6 seconds for robots to reach destinations...
timeout /t 6 /nobreak >nul

:: Check status of all robots after commands
echo [11/12] Status after commands:
echo   RBT-01:
curl.exe -s http://localhost:8082/api/robots/RBT-01/status
echo.
echo   RBT-02:
curl.exe -s http://localhost:8082/api/robots/RBT-02/status
echo.
echo   RBT-04:
curl.exe -s http://localhost:8082/api/robots/RBT-04/status
echo.
echo.

:: SSE Telemetry stream (3 seconds)
echo [12/12] GET /api/robots/telemetry/stream - SSE stream (3 sec):
curl.exe -s -N --max-time 3 http://localhost:8082/api/robots/telemetry/stream
echo.
echo.

echo ============================================
echo   All tests completed!
echo ============================================
echo.
echo TIP: To see NATS messages in real-time, run:
echo   docker exec -it smartlogistic-nats nats sub "smartlogistic.robot.>"
echo.
pause