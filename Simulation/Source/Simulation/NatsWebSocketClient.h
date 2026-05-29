// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "RobotTypes.h"
#include "NatsWebSocketClient.generated.h"

/** Delegate broadcast when a robot status event is received from NATS */
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnRobotStatusReceived, const FSmartLogisticRobotData&, RobotData);

/** Delegate broadcast when a robot command is received from NATS */
DECLARE_DYNAMIC_MULTICAST_DELEGATE_ThreeParams(FOnRobotCommandReceived, const FString&, RobotId, const FString&, CommandType, const FString&, TargetLocation);

/** Delegate broadcast when a package mission command is received from NATS */
DECLARE_DYNAMIC_MULTICAST_DELEGATE_SevenParams(FOnPackageMissionReceived,
    const FString&, RobotId,
    int64, PackageId,
    const FString&, MissionType,
    const FString&, ReceptionSpotCode,
    const FString&, TargetSpotCode,
    const FString&, ItemSku,
    int32, Quantity);

/**
 * NATS client using raw TCP socket (more reliable than WebSocket on UE5/Windows).
 * Connects directly to NATS server on port 4222.
 * Handles the NATS protocol: INFO -> CONNECT -> SUBSCRIBE -> MSG processing.
 */
UCLASS(BlueprintType, Category = "SmartLogistics")
class UNatsWebSocketClient : public UObject
{
    GENERATED_BODY()

public:
    UNatsWebSocketClient();

    /** Connect to NATS server (e.g., "127.0.0.1:4222") */
    void Connect(const FString& ServerUrl);

    /** Disconnect from NATS */
    void Disconnect();

    /** Is NATS connected and subscribed? */
    bool IsConnected() const;

    /** Publish a message to a NATS subject */
    bool Publish(const FString& Subject, const FString& JsonPayload);

    /** Delegate broadcast when robot status event arrives */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnRobotStatusReceived OnRobotStatusReceived;

    /** Delegate broadcast when a robot command arrives from backend */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnRobotCommandReceived OnRobotCommandReceived;

    /** Delegate broadcast when a package mission command arrives from backend */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnPackageMissionReceived OnPackageMissionReceived;

private:
    // --- TCP Socket ---
    FSocket* NatsSocket = nullptr;
    FString RxBuffer;
    FString PendingMsgHeader;
    bool bNatsConnected = false;
    int32 NextSubId = 1;

    // --- Tick for reading socket ---
    FTimerHandle TickTimerHandle;
    void TickReadSocket();

    // --- Protocol handlers ---
    void ProcessLine(const FString& Line);
    void SendNatsConnect();
    void SendNatsSubscribe(const FString& Subject);
    bool SendString(const FString& Str);

    // --- JSON parsing ---
    FSmartLogisticRobotData ParseRobotJson(const TSharedPtr<FJsonObject>& JsonObject);
    ERobotOperationalMode ParseOperationalMode(const FString& ModeStr);
};