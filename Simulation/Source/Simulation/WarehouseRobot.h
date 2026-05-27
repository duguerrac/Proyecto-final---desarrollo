// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "Components/StaticMeshComponent.h"
#include "Components/TextRenderComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "RobotTypes.h"
#include "WarehouseRobot.generated.h"

/**
 * Visual representation of a warehouse robot in the 3D scene.
 * Updated by ARobotManager when NATS events arrive.
 * Changes color based on operational mode and shows battery level.
 */
UCLASS(BlueprintType, Category = "SmartLogistics")
class AWarehouseRobot : public AActor
{
    GENERATED_BODY()

public:
    AWarehouseRobot();

    virtual void BeginPlay() override;

    /** Update this robot's visual state from event data. */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics|Robot")
    void UpdateFromData(const FSmartLogisticRobotData& Data);

    /** Get current robot data. */
    UFUNCTION(BlueprintPure, Category = "SmartLogistics|Robot")
    const FSmartLogisticRobotData& GetCurrentData() const { return CurrentData; }

    /** Robot ID this actor represents. */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "SmartLogistics|Robot")
    FString RobotId;

    // ─── Visual Components ──────────────────────────────────────

    /** Main body mesh. */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UStaticMeshComponent* BodyMesh;

    /** Text showing robot name + status. */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UTextRenderComponent* StatusText;

    /** Text showing battery percentage. */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "SmartLogistics|Components")
    UTextRenderComponent* BatteryText;

private:
    FSmartLogisticRobotData CurrentData;

    /** Dynamic material for color changes. */
    UMaterialInstanceDynamic* DynMaterial;

    /** Map operational mode → display color. */
    FLinearColor GetStatusColor() const;
};