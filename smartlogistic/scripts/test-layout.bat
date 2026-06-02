@echo off
echo ============================================================
echo  SmartLogistics - Warehouse Layout Test Script
echo  Creates a layout, activates it, then verifies active fetch
echo ============================================================
echo.

set BASE=http://localhost:8081/api/layouts
set SCRIPT_DIR=%~dp0

echo [1/4] Creating a test warehouse layout (10x8 grid)...
curl -s -X POST "%BASE%" -H "Content-Type: application/json" -d @"%SCRIPT_DIR%test-layout-payload.json"
echo.
echo.

echo [2/4] Listing all layouts...
curl -s "%BASE%"
echo.
echo.

echo [3/4] Activating layout ID 1...
curl -s -X POST "%BASE%/1/activate"
echo.
echo.

echo [4/4] Fetching active layout (what UE5 will call)...
curl -s "%BASE%/active"
echo.
echo.

echo ============================================================
echo  Done! If step 4 returns JSON with status=ACTIVE, the
echo  UE5 simulation should be able to fetch and apply the layout.
echo ============================================================
pause