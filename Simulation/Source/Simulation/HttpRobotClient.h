// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "RobotTypes.h"
#include "HttpRobotClient.generated.h"

/** Delegate broadcast when a robot command is received from backend polling */
DECLARE_DYNAMIC_MULTICAST_DELEGATE_ThreeParams(FOnHttpRobotCommand, const FString&, RobotId, const FString&, CommandType, const FString&, TargetLocation);

/** Delegate broadcast when a package mission command is received */
DECLARE_DYNAMIC_MULTICAST_DELEGATE_SevenParams(FOnHttpMissionCommand,
    const FString&, RobotId,
    int64, PackageId,
    const FString&, MissionType,
    const FString&, ReceptionSpotCode,
    const FString&, TargetSpotCode,
    const FString&, ItemSku,
    int32, Quantity);

/** Delegate broadcast when a package.received event arrives */
DECLARE_DYNAMIC_MULTICAST_DELEGATE_FiveParams(FOnHttpPackageReceived,
    int64, PackageId,
    const FString&, Sku,
    int32, Quantity,
    const FString&, ReceptionSpotCode,
    const FString&, TargetSpotCode);

/**
 * HTTP REST client for communicating with ms-robot-status backend.
 * Replaces NATS WebSocket — uses FHttpModule for all communication.
 * 
 * Publishing:  POST to REST API endpoints
 * Subscribing: Periodic polling for pending commands/missions
 */
UCLASS(BlueprintType, Category = "SmartLogistics")
class UHttpRobotClient : public UObject
{
    GENERATED_BODY()

public:
    UHttpRobotClient();

    /** Initialize with the robot-status API base URL (e.g., "http://localhost:8082") */
    void Initialize(const FString& InApiBaseUrl);

    /** Set the warehouse-core API URL for package polling */
    void SetWarehouseUrl(const FString& InWarehouseUrl);

    /** Start polling for commands. Call after Initialize(). */
    void StartPolling();

    /** Stop polling and disconnect. */
    void Disconnect();

    /** Is the client active and polling? */
    bool IsConnected() const;

    /** Send telemetry for a single robot via PUT /api/robots/{id}/telemetry */
    void SendTelemetry(const FString& RobotId, const FString& JsonPayload);

    /** Send telemetry batch via POST (array of robot data) */
    void SendTelemetryBatch(const FString& JsonBatchPayload);

    /** Publish a mission event (replaces NATS publish) */
    void PublishEvent(const FString& EventType, const FString& JsonPayload);

    /** Notify backend that a robot completed its route */
    void NotifyRouteComplete(const FString& RobotId);

    /** Perform one poll cycle (called by RobotManager from Tick) */
    void PollForCommands();

    /** Delegate: command received for a robot */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnHttpRobotCommand OnRobotCommandReceived;

    /** Delegate: mission command received */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnHttpMissionCommand OnMissionCommandReceived;

    /** Delegate: package received at reception */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnHttpPackageReceived OnPackageReceived;

private:
    /** Base URL for the robot-status API */
    FString ApiBaseUrl;

    /** Base URL for the warehouse-core API */
    FString WarehouseApiUrl;

    /** Is polling active? */
    bool bIsPolling = false;

    /** Timer handle for command polling */
    FTimerHandle PollTimerHandle;

    /** How often to poll for commands (seconds) */
    float PollInterval = 1.0f;

    /** Set of package IDs already processed (avoid duplicates) */
    TSet<int64> ProcessedPackageIds;

    /** Poll for RECEIVED packages from warehouse-core API */
    void PollForPendingPackages();

    /** Poll for robot status/command changes */
    void PollForRobotCommands();

    /** Internal: make an HTTP request */
    void MakeRequest(const FString& Method, const FString& Url, const FString& Body,
        TFunction<void(int32, const FString&)> Callback);
};