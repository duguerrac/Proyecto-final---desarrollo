package com.smartlogistics.robotstatus.infrastructure.adapter.out.redis;

import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.domain.model.Robot;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RedisRobotAdapter implements RobotCachePort {

    private static final String KEY_PREFIX = "robot:";
    private static final String STATUS_SUFFIX = ":status";
    private static final String ALL_IDS_KEY = "robot:all_ids";

    private final HashOperations<String, String, String> hashOps;
    private final RedisTemplate<String, String> redisTemplate;

    public RedisRobotAdapter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
    }

    @Override
    public void save(Robot robot) {
        String key = redisKey(robot.id());
        hashOps.putAll(key, Map.of(
                "name", robot.name(),
                "batteryLevel", String.valueOf(robot.batteryLevel()),
                "available", String.valueOf(robot.available()),
                "currentLocation", robot.currentLocation(),
                "operationalMode", robot.operationalMode()
        ));
        // Track this robot ID in the index set
        redisTemplate.opsForSet().add(ALL_IDS_KEY, robot.id());
    }

    @Override
    public Optional<Robot> findById(String robotId) {
        String key = redisKey(robotId);
        Map<String, String> entries = hashOps.entries(key);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toRobot(robotId, entries));
    }

    @Override
    public List<Robot> findAll() {
        Set<String> ids = redisTemplate.opsForSet().members(ALL_IDS_KEY);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(id -> {
                    Map<String, String> entries = hashOps.entries(redisKey(id));
                    if (entries.isEmpty()) return null;
                    return toRobot(id, entries);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Robot toRobot(String robotId, Map<String, String> entries) {
        return new Robot(
                robotId,
                entries.getOrDefault("name", ""),
                Integer.parseInt(entries.getOrDefault("batteryLevel", "0")),
                Boolean.parseBoolean(entries.getOrDefault("available", "false")),
                entries.getOrDefault("currentLocation", ""),
                entries.getOrDefault("operationalMode", "")
        );
    }

    private String redisKey(String robotId) {
        return KEY_PREFIX + robotId + STATUS_SUFFIX;
    }
}
