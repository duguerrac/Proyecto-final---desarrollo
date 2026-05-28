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
    IDLE     UMETA(DisplayName = "Idle"),
    MOVING   UMETA(DisplayName = "Moving"),
    PICKING  UMETA(DisplayName = "Picking"),
    CHARGING UMETA(DisplayName = "Charging"),
    OFFLINE  UMETA(DisplayName = "Offline")
};

/**
 * Comprehensive robot data for simulation telemetry.
 * Extended with position, cargo, and movement tracking.
 */
USTRUCT(BlueprintType)
struct FSmartLogisticRobotData
{
    GENERATED_BODY()

    // ─── Identity ───────────────────────────────────────────────

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FString RobotId;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FString RobotName;

    // ─── Battery ────────────────────────────────────────────────

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    int32 BatteryLevel = 100;

    // ─── Status ─────────────────────────────────────────────────

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    bool bAvailable = true;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FString CurrentLocation;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    ERobotOperationalMode OperationalMode = ERobotOperationalMode::IDLE;

    // ─── Position (UE5 world coordinates) ───────────────────────

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FVector WorldPosition = FVector::ZeroVector;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FRotator WorldRotation = FRotator::ZeroRotator;

    // ─── Movement ───────────────────────────────────────────────

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    float Speed = 0.0f;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    float DistanceTraveled = 0.0f;

    // ─── Cargo ──────────────────────────────────────────────────

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    int32 CarriedItems = 0;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    int32 MaxCapacity = 5;

    // ─── Timestamp ──────────────────────────────────────────────

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics")
    FString EventTimestamp;
};