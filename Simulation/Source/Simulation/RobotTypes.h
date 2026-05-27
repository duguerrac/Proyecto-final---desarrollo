// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "RobotTypes.generated.h"

/**
 * Mirrors com.smartlogistics.robotstatus.domain.model.RobotStatus
 */
UENUM(BlueprintType)
enum class ERobotOperationalMode : uint8
{
    IDLE    UMETA(DisplayName = "Idle"),
    MOVING  UMETA(DisplayName = "Moving"),
    PICKING UMETA(DisplayName = "Picking"),
    CHARGING UMETA(DisplayName = "Charging"),
    OFFLINE  UMETA(DisplayName = "Offline")
};

/**
 * Mirrors com.smartlogistics.robotstatus.domain.model.Robot
 *Parsed from NATS JSON events.
 */
USTRUCT(BlueprintType)
struct FSmartLogisticRobotData
{
    GENERATED_BODY()

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FString RobotId;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FString RobotName;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    int32 BatteryLevel = 100;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    bool bAvailable = true;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FString CurrentLocation;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    ERobotOperationalMode OperationalMode = ERobotOperationalMode::IDLE;

    /** Timestamp from the event */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FString EventTimestamp;
};