package com.smartlogistics.warehouse.infrastructure.adapter.out.robotstatus;

import com.smartlogistics.warehouse.application.port.out.RobotStatusPort;
import com.smartlogistics.warehouse.domain.exception.RobotRejectedException;
import com.smartlogistics.warehouse.domain.model.RobotStatus;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class RobotStatusClient implements RobotStatusPort {
    private final WebClient webClient;
    private final Duration timeout;

    public RobotStatusClient(
            WebClient.Builder builder,
            @Value("${robot-status.base-url}") String baseUrl,
            @Value("${robot-status.timeout-ms:2000}") long timeoutMs) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @Override
    public RobotStatus getStatus(String robotId) {
        try {
            RobotStatusResponse response = webClient.get()
                    .uri("/api/robots/{robotId}/status", robotId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class).map(body -> new RobotRejectedException("Robot not found or unavailable: " + robotId)))
                    .bodyToMono(RobotStatusResponse.class)
                    .block(timeout);
            if (response == null) {
                throw new RobotRejectedException("Robot status unavailable");
            }
            return new RobotStatus(response.robotId(), response.batteryLevel(), response.available(), response.currentLocation(), response.operationalMode());
        } catch (RobotRejectedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RobotRejectedException("RobotStatus service unavailable");
        }
    }

    public record RobotStatusResponse(String robotId, int batteryLevel, boolean available, String currentLocation, String operationalMode) {
    }
}
