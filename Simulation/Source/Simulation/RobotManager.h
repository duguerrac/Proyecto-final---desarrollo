// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "RobotTypes.h"
#include "HttpRobotClient.h"
#include "RobotManager.generated.h"

class AWarehouseRobot;
class AWarehouseEnvironment;

/**
 * Central manager for the SmartLogistics warehouse simulation.
 * Owns the HTTP REST client and manages a pool of AWarehouseRobot actors.
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

    /** Robot Status API URL (no trailing slash). */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config")
    FString RobotApiUrl = TEXT("http://localhost:8082");

    /** Warehouse Core API URL (no trailing slash). */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Config")
    FString WarehouseApiUrl = TEXT("http://localhost:8081");

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

    /** Is the backend connected? */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Status")
    bool bIsBackendConnected = false;

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

    /** Connect to backend APIs (called automatically in BeginPlay). */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void ConnectToBackend();

    /** Disconnect from backend. */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void DisconnectFromBackend();

    /** Get all tracked robot data as array. */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void GetAllRobotData(TArray<FSmartLogisticRobotData>& OutData) const;

    /** Publish all robot telemetry to backend via HTTP */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void PublishTelemetry();

    /** Send a command to a specific robot */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void SendRobotCommand(const FString& RobotId, const FString& CommandType, const FString& TargetLocation);

    /** Fetch the active layout from warehouse-core API and rebuild the environment. */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void FetchAndApplyWarehouseLayout();

    /** Apply a layout from a raw JSON string. */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void ApplyWarehouseLayoutFromJson(const FString& JsonString);

    // ─── Delegates ───────────────────────────────────────────────

    /** Fired when a robot status is updated (for HUD binding). */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnRobotStatusReceived OnRobotUpdated;

private:
    /** HTTP REST client instance. */
    UPROPERTY()
    UHttpRobotClient* HttpClient;

    /** Map robot ID → robot actor instance. */
    UPROPERTY()
    TMap<FString, AWarehouseRobot*> RobotActors;

    /** Counter for naming new robot actors. */
    int32 RobotCounter = 0;

    /** Handle incoming command. */
    UFUNCTION()
    void HandleRobotCommand(const FString& RobotId, const FString& CommandType, const FString& TargetLocation);

    /** Handle incoming mission command. */
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

    /** Publish a mission completion event. */
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
     */
    void RequestRouteAndFollowWaypoints(AWarehouseRobot* Robot, const FString& FromCode, const FString& ToCode,
        bool bPickUpAtDestination = false);
};