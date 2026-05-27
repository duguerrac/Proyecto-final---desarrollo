@echo off
echo === SmartLogistic - Send ALL 5 robots to UE5 ===
echo.

echo [1] PATCH RBT-01 to MOVING:
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-01/status -H "Content-Type: application/json" -d "{\"operationalMode\":\"MOVING\",\"batteryLevel\":85}"
echo.
echo.

echo [2] PATCH RBT-02 to PICKING:
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-02/status -H "Content-Type: application/json" -d "{\"operationalMode\":\"PICKING\",\"batteryLevel\":72}"
echo.
echo.

echo [3] PATCH RBT-03 to CHARGING:
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-03/status -H "Content-Type: application/json" -d "{\"operationalMode\":\"CHARGING\",\"batteryLevel\":8}"
echo.
echo.

echo [4] PATCH RBT-04 to IDLE:
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-04/status -H "Content-Type: application/json" -d "{\"operationalMode\":\"IDLE\",\"batteryLevel\":50}"
echo.
echo.

echo [5] PATCH RBT-LOW to IDLE:
curl.exe -s -X PATCH http://localhost:8082/api/robots/RBT-LOW/status -H "Content-Type: application/json" -d "{\"operationalMode\":\"IDLE\",\"batteryLevel\":10}"
echo.
echo.

echo === Done! All 5 robots sent. Check UE5 ===
pause