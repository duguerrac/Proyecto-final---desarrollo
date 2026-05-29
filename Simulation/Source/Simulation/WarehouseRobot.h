// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "Components/StaticMeshComponent.h"
#include "Components/TextRenderComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "RobotTypes.h"
#include "WarehouseRobot.generated.h"

/** Delegate fired when the robot arrives at its movement target */
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnArrivalAtTarget, AWarehouseRobot*, Robot, const FString&, MissionType);

/**
 * Autonomous warehouse robot with battery simulation.
 * - Drains battery based on distance traveled
 * - Auto-charges at charging stations
 * - Carries items with capacity tracking
 * - Publishes telemetry via RobotManager → NATS
 */
UCLASS(BlueprintType, Category = "SmartLogistics")
class AWarehouseRobot : public AActor
{
    GENERATED_BODY()

public:
    AWarehouseRobot();

    virtual void BeginPlay() override;
    virtual void Tick(float DeltaTime) override;

    // ─── Configuration (editable in level) ──────────────────────

    /** Battery drain per 100cm (1 meter) of movement */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Battery")
    float BatteryDrainPerMeter = 0.15f;

    /** Battery charge per second when at charging station */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Battery")
    float BatteryChargeRate = 8.0f;

    /** Battery level to trigger auto-seek charger */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Battery")
    float LowBatteryThreshold = 20.0f;

    /** Movement speed in cm/s */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Movement")
    float MovementSpeed = 300.0f;

    /** Maximum cargo capacity */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Cargo")
    int32 MaxCargoCapacity = 5;

    // ─── Public API ─────────────────────────────────────────────

    /** Update from external event data (NATS inbound) */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics|Robot")
    void UpdateFromData(const FSmartLogisticRobotData& Data);

    /** Get current telemetry data */
    UFUNCTION(BlueprintPure, Category = "SmartLogistics|Robot")
    const FSmartLogisticRobotData& GetCurrentData() const { return CurrentData; }

    /** Command: Move to world position */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics|Robot")
    void MoveTo(const FVector& TargetLocation);

    /** Command: Pick up item at current location */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics|Robot")
    void PickUpItem();

    /** Command: Drop off items at current location */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics|Robot")
    void DropOffItems();

    /** Command: Go charge at nearest station */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics|Robot")
    void GoCharge(const FVector& StationLocation);

    /** Is the robot currently moving to a target? */
    UFUNCTION(BlueprintPure, Category = "SmartLogistics|Robot")
    bool IsMoving() const { return bIsMoving; }

    /** Robot ID */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Robot")
    FString RobotId;

    // ─── Mission State ──────────────────────────────────────────

    /** Fired when robot arrives at its MoveTo target */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics|Mission")
    FOnArrivalAtTarget OnArrivalAtTarget;

    /** Current active mission data (empty if idle) */
    FRobotMissionData ActiveMission;

    /** Whether the robot is on an active mission */
    bool bOnMission = false;

    /** Set mission for this robot */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics|Mission")
    void SetMission(const FRobotMissionData& Mission);

    /** Clear mission state */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics|Mission")
    void ClearMission();

    // ─── Visual Components ──────────────────────────────────────

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UStaticMeshComponent* BodyMesh;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UTextRenderComponent* StatusText;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UTextRenderComponent* BatteryText;

    /** 3D battery bar background (red) */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UStaticMeshComponent* BatteryBarBg;

    /** 3D battery bar fill (green→red) */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UStaticMeshComponent* BatteryBarFill;

    /** Cargo indicator mesh */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UStaticMeshComponent* CargoIndicator;

private:
    FSmartLogisticRobotData CurrentData;

    // ─── Simulation State ───────────────────────────────────────

    /** Dynamic material for body color */
    UMaterialInstanceDynamic* DynMaterial;
    UMaterialInstanceDynamic* BatteryBarMaterial;

    /** Movement target */
    bool bIsMoving = false;
    FVector MoveTarget = FVector::ZeroVector;

    /** Charging state */
    bool bIsCharging = false;
    FVector ChargeStationLocation = FVector::ZeroVector;

    /** Previous position for distance calculation */
    FVector PreviousPosition = FVector::ZeroVector;

    /** Internal battery as float (0-100) for smooth drain */
    float InternalBattery = 100.0f;

    /** Battery bar width scale */
    static constexpr float BatteryBarMaxWidth = 1.5f;

    // ─── Internal Methods ───────────────────────────────────────

    /** Map operational mode → display color */
    FLinearColor GetStatusColor() const;

    /** Update battery bar 3D visual */
    void UpdateBatteryBar();

    /** Update cargo indicator visual */
    void UpdateCargoIndicator();

    /** Update all text displays */
    void UpdateTextDisplays();

    /** Simulate battery drain based on distance */
    void SimulateBatteryDrain(float DeltaDistance);

    /** Simulate battery charging */
    void SimulateCharge(float DeltaTime);

    /** Update operational mode based on state */
    void UpdateOperationalMode();
};