// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "RobotTypes.h"
#include "NatsWebSocketClient.h"
#include "RobotManager.generated.h"

class AWarehouseRobot;
class AWarehouseEnvironment;

/**
 * Central manager for the SmartLogistics warehouse simulation.
 * Owns the NATS WebSocket client and manages a pool of AWarehouseRobot actors.
 * Place one instance in the level to enable the simulation.
 */
UCLASS(BlueprintType, Category = "SmartLogistics")
class ARobotManager : public AActor
{
    GENERATED_BODY()

public:
    ARobotManager();

    virtual void BeginPlay() override;
    virtual void EndPlay(const EEndPlayReason::Type EndPlayReason) override;
    virtual void Tick(float DeltaTime) override;

    // ─── Configuration (set in level editor) ─────────────────────

    /** NATS WebSocket URL. Default: ws://localhost:8443 (matches docker-compose NATS_WS_PORT) */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config")
    FString NatsUrl = TEXT("127.0.0.1:4222");

    /** Warehouse Core API URL (no trailing slash). */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config")
    FString WarehouseApiUrl = TEXT("http://localhost:8081");

    /** Robot Status API URL (no trailing slash). */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config")
    FString RobotApiUrl = TEXT("http://localhost:8082");

    /** How many robot slots to pre-allocate in the scene. */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config",
              meta = (ClampMin = "1", ClampMax = "50"))
    int32 MaxRobots = 10;

    /** Spacing between robot slots on the X axis. */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config")
    float RobotSpacing = 300.0f;

    /** Starting position for the first robot. */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config")
    FVector SpawnOrigin = FVector(0.0f, 0.0f, 100.0f);

    // ─── Runtime State (read-only) ───────────────────────────────

    /** Number of active robots being tracked. */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Status")
    int32 ActiveRobotCount = 0;

    /** Number of events received in this session. */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Status")
    int32 TotalEventsReceived = 0;

    /** Is NATS connected? */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Status")
    bool bIsNatsConnected = false;

    // ─── Warehouse Environment Reference ────────────────────────

    /** Reference to the warehouse environment actor (for layout publishing) */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config")
    AWarehouseEnvironment* WarehouseEnv = nullptr;

    // ─── Charging Stations (set in level editor) ────────────────

    /** Positions of charging stations in the warehouse */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Charging")
    TArray<FVector> ChargingStations;

    // ─── Telemetry ──────────────────────────────────────────────

    /** How often to publish telemetry (seconds). 0 = disabled. */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Telemetry",
              meta = (ClampMin = "0.0", ClampMax = "60.0"))
    float TelemetryInterval = 2.0f;

    /** Last telemetry publish time */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Telemetry")
    float LastTelemetryTime = 0.0f;

    // ─── Actions ─────────────────────────────────────────────────

    /** Manually connect to NATS (called automatically in BeginPlay). */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void ConnectToNats();

    /** Disconnect from NATS. */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void DisconnectFromNats();

    /** Get all tracked robot data as array. */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void GetAllRobotData(TArray<FSmartLogisticRobotData>& OutData) const;

    /** Publish all robot telemetry to NATS */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void PublishTelemetry();

    /** Send a command to a specific robot */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void SendRobotCommand(const FString& RobotId, const FString& CommandType, const FString& TargetLocation);

    /** Fetch the active layout from warehouse-core API and rebuild the environment. */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void FetchAndApplyWarehouseLayout();

    /** Apply a layout from a raw JSON string (for NATS event-driven updates). */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void ApplyWarehouseLayoutFromJson(const FString& JsonString);

    // ─── Delegates ───────────────────────────────────────────────

    /** Fired when a robot status is updated (for HUD binding). */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnRobotStatusReceived OnRobotUpdated;

private:
    /** NATS client instance. */
    UPROPERTY()
    UNatsWebSocketClient* NatsClient;

    /** Map robot ID → robot actor instance. */
    UPROPERTY()
    TMap<FString, AWarehouseRobot*> RobotActors;

    /** Counter for naming new robot actors. */
    int32 RobotCounter = 0;

    /** Handle incoming NATS event. */
    UFUNCTION()
    void HandleRobotStatusEvent(const FSmartLogisticRobotData& RobotData);

    /** Handle incoming NATS command. */
    UFUNCTION()
    void HandleRobotCommand(const FString& RobotId, const FString& CommandType, const FString& TargetLocation);

    /** Handle incoming NATS command with mission data (STOCK_IN, STOCK_OUT, etc). */
    UFUNCTION()
    void HandleMissionCommand(const FString& RobotId, int64 PackageId, const FString& MissionType,
        const FString& ReceptionSpotCode, const FString& TargetSpotCode, const FString& ItemSku, int32 Quantity);

    /** Handle package.received event → spawn box at reception spot. */
    UFUNCTION()
    void HandlePackageReceived(int64 PackageId, const FString& Sku, int32 Quantity,
        const FString& ReceptionSpotCode, const FString& TargetSpotCode);

    /** Handle robot arrival at mission target. */
    UFUNCTION()
    void HandleRobotArrival(AWarehouseRobot* Robot, const FString& MissionType);

    /** Publish a mission completion event to NATS. */
    void PublishMissionEvent(const FString& EventType, const FString& RobotId,
        int64 PackageId, const FString& SpotCode, const FString& MissionType);

    /** Find or create a robot actor for the given ID. */
    AWarehouseRobot* FindOrCreateRobot(const FSmartLogisticRobotData& Data);

    /** Compute world position for robot slot index. */
    FVector GetSlotPosition(int32 SlotIndex) const;

    /** Send a low-battery robot to the nearest charging station. */
    void AutoChargeRobot(AWarehouseRobot* Robot);

    /** Fetch robots from the robot-status backend API and spawn actors. */
    void FetchRobotsFromBackend();

    /**
     * Request a route from the backend route-planning API and instruct the robot
     * to follow the resulting waypoints.
     * @param Robot The robot to dispatch
     * @param FromCode Root point code for origin (e.g. "RP-R01-C02")
     * @param ToCode Root point code for destination
     * @param bPickUpAtDestination Whether to pick up items when arriving (affects mission phase)
     */
    void RequestRouteAndFollowWaypoints(AWarehouseRobot* Robot, const FString& FromCode, const FString& ToCode,
        bool bPickUpAtDestination = false);
};
