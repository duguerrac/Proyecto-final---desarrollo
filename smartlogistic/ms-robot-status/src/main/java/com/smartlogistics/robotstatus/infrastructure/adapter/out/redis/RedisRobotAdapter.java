package com.smartlogistics.robotstatus.infrastructure.adapter.out.redis;

import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotStatus;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis adapter that implements the RobotCachePort output port.
 * Framework imports are allowed in infrastructure layer.
 */
@Component
public class RedisRobotAdapter implements RobotCachePort {

    private static final String KEY_PREFIX = "robot:";
    private static final String KEY_SUFFIX = ":status";
    private static final String ROBOT_IDS_SET = "robot:ids";

    private final HashOperations<String, String, String> hashOps;
    private final RedisTemplate<String, String> redisTemplate;

    public RedisRobotAdapter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
    }

    @Override
    public Optional<Robot> findById(String robotId) {
        String key = KEY_PREFIX + robotId + KEY_SUFFIX;
        Map<String, String> entries = hashOps.entries(key);

        if (entries.isEmpty()) {
            return Optional.empty();
        }

        Robot robot = new Robot();
        robot.setId(robotId);
        robot.setName(entries.get("name"));
        robot.setBatteryLevel(Integer.parseInt(entries.getOrDefault("batteryLevel", "0")));
        robot.setAvailable(Boolean.parseBoolean(entries.getOrDefault("available", "false")));
        robot.setCurrentLocation(entries.get("currentLocation"));
        String mode = entries.get("operationalMode");
        if (mode != null) {
            robot.setOperationalMode(RobotStatus.valueOf(mode));
        }

        return Optional.of(robot);
    }

    @Override
    public List<Robot> findAll() {
        return redisTemplate.opsForSet().members(ROBOT_IDS_SET).stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    public Robot save(Robot robot) {
        String key = KEY_PREFIX + robot.getId() + KEY_SUFFIX;

        hashOps.put(key, "name", robot.getName());
        hashOps.put(key, "batteryLevel", String.valueOf(robot.getBatteryLevel()));
        hashOps.put(key, "available", String.valueOf(robot.isAvailable()));
        hashOps.put(key, "currentLocation", robot.getCurrentLocation() != null ? robot.getCurrentLocation() : "");
        hashOps.put(key, "operationalMode",
                robot.getOperationalMode() != null ? robot.getOperationalMode().name() : RobotStatus.IDLE.name());

        // Track robot ID in the set for findAll()
        redisTemplate.opsForSet().add(ROBOT_IDS_SET, robot.getId());

        return robot;
    }
}
