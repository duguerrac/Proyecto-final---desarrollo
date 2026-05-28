const fs = require('fs');
const p = 'd:/University/Proyecto final/Proyecto-final---desarrollo/smartlogistic/ms-robot-status/src/main/java/com/smartlogistics/robotstatus/infrastructure/adapter/in/rest/RobotStatusController.java';
let c = fs.readFileSync(p, 'utf8');

const missionCode = `
    // --- Mission Endpoint ---

    @PostMapping("/{id}/mission")
    public ResponseEntity<Map<String, Object>> createMission(
            @PathVariable String id, @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<String> pickLocations = (List<String>) body.get("pickLocations");
        String dropOffLocation = (String) body.get("dropOffLocation");
        String orderId = (String) body.getOrDefault("orderId", "ORDER-" + System.currentTimeMillis());

        if (pickLocations == null || pickLocations.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "pickLocations is required"));
        }
        if (dropOffLocation == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "dropOffLocation is required"));
        }

        RobotMission mission = missionOptimizerService.createMission(id, pickLocations, dropOffLocation, orderId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "missionId", mission.getMissionId(),
                "robotId", mission.getRobotId(),
                "orderId", mission.getOrderId(),
                "status", mission.getStatus().name(),
                "totalSteps", mission.getSteps().size(),
                "steps", mission.getSteps().stream().map(s -> Map.<String, Object>of(
                        "stepIndex", s.getStepIndex(),
                        "action", s.getAction(),
                        "locationName", s.getLocationName(),
                        "targetPosition", s.getTargetPositionString(),
                        "status", s.getStatus().name()
                )).toList()
        ));
    }

    // --- Warehouse Layout Endpoints ---

    @GetMapping("/warehouse/layout")
    public ResponseEntity<List<Map<String, Object>>> getWarehouseLayout() {
        List<Map<String, Object>> locations = layoutPort.getLayout().stream()
                .map(loc -> Map.<String, Object>of(
                        "name", loc.getName(),
                        "type", loc.getType(),
                        "x", loc.getX(),
                        "y", loc.getY(),
                        "z", loc.getZ()
                ))
                .toList();
        return ResponseEntity.ok(locations);
    }

    @GetMapping("/warehouse/layout/{type}")
    public ResponseEntity<List<Map<String, Object>>> getLayoutByType(@PathVariable String type) {
        List<Map<String, Object>> locations = layoutPort.findByType(type).stream()
                .map(loc -> Map.<String, Object>of(
                        "name", loc.getName(),
                        "type", loc.getType(),
                        "x", loc.getX(),
                        "y", loc.getY(),
                        "z", loc.getZ()
                ))
                .toList();
        return ResponseEntity.ok(locations);
    }

`;

c = c.replace('    private Map<String, Object> toMap(Robot robot) {', missionCode + '    private Map<String, Object> toMap(Robot robot) {');
fs.writeFileSync(p, c);
console.log('Endpoints added successfully');