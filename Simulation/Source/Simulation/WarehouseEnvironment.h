// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "WarehouseEnvironment.generated.h"

/**
 * Procedural warehouse environment builder.
 * Place this actor in the level and it auto-generates:
 *   - Floor, walls, ceiling
 *   - Shelving racks (colored blocks)
 *   - Charging stations (cyan platforms)
 *   - Delivery/dock area (orange platform)
 *   - Navigation markers
 */
UCLASS(BlueprintType, Category = "SmartLogistics")
class AWarehouseEnvironment : public AActor
{
    GENERATED_BODY()

public:
    AWarehouseEnvironment();
    virtual void OnConstruction(const FTransform& Transform) override;

    // ─── Warehouse Dimensions ────────────────────────────────────
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Dimensions")
    float WarehouseWidth = 4000.0f;   // Y axis

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Dimensions")
    float WarehouseDepth = 6000.0f;   // X axis

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Dimensions")
    float WallHeight = 800.0f;

    // ─── Shelf Config ────────────────────────────────────────────
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Shelves")
    int32 NumShelfRows = 4;

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Shelves")
    int32 NumShelfUnitsPerRow = 6;

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Shelves")
    float ShelfSpacingX = 800.0f;

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Shelves")
    float ShelfSpacingY = 600.0f;

    // ─── Charging Station Config ─────────────────────────────────
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Charging")
    int32 NumChargingStations = 3;

    // ─── Colors ──────────────────────────────────────────────────
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Colors")
    FLinearColor FloorColor = FLinearColor(0.15f, 0.15f, 0.18f);

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Colors")
    FLinearColor WallColor = FLinearColor(0.35f, 0.35f, 0.38f);

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Colors")
    FLinearColor ShelfColor = FLinearColor(0.55f, 0.35f, 0.15f);

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Colors")
    FLinearColor ChargingColor = FLinearColor(0.0f, 0.8f, 0.7f);

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Colors")
    FLinearColor DeliveryColor = FLinearColor(0.9f, 0.55f, 0.1f);

private:
    UPROPERTY()
    TArray<UStaticMeshComponent*> ProceduralComponents;

    /** Cached cube mesh for procedural boxes */
    UPROPERTY()
    UStaticMesh* DefaultCubeMesh = nullptr;

    void ClearProceduralComponents();
    UStaticMeshComponent* AddBox(const FString& Name, FVector Location, FVector Scale, FLinearColor Color, FRotator Rotation = FRotator::ZeroRotator);
    void BuildFloor();
    void BuildWalls();
    void BuildShelves();
    void BuildChargingStations();
    void BuildDeliveryArea();
    void BuildLabels();
};