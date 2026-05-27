@echo off
echo === SmartLogistics - Robot Status Event Tester ===
echo.

if "%~1"=="" (
    echo Usage: test-robot-event.bat [ROBOT_ID] [MODE] [BATTERY]
    echo.
    echo Examples:
    echo   test-robot-event.bat RBT-01 MOVING
    echo   test-robot-event.bat RBT-01 CHARGING 15
    echo   test-robot-event.bat RBT-02 PICKING 80
    echo.
    echo Available modes: IDLE MOVING PICKING CHARGING OFFLINE
    echo Default robot: RBT-01, Default mode: MOVING, Default battery: 85
    echo.
    set /p ROBOT_ID="Enter Robot ID (e.g. RBT-01): "
    set /p MODE="Enter Mode (IDLE/MOVING/PICKING/CHARGING/OFFLINE): "
    set /p BATTERY="Enter Battery %% (0-100): "
) else (
    set ROBOT_ID=%~1
    set MODE=%~2
    set BATTERY=%~3
)

if "%ROBOT_ID%"=="" set ROBOT_ID=RBT-01
if "%MODE%"=="" set MODE=MOVING
if "%BATTERY%"=="" set BATTERY=85

echo.
echo Sending PATCH to robot %ROBOT_ID%...
echo   Mode: %MODE%
echo   Battery: %BATTERY%%
echo.

curl.exe -s -X PATCH http://localhost:8082/api/robots/%ROBOT_ID%/status -H "Content-Type: application/json" -d "{\"operationalMode\":\"%MODE%\",\"batteryLevel\":%BATTERY%}"

echo.
echo.
echo Done! Check UE5 Output Log for the robot update.
echo.
pause