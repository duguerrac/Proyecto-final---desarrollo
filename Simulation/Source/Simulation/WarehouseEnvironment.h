// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "WarehouseEnvironment.generated.h"

/**
 * A named warehouse location (shelf, dock, charging station, etc.)
 */
USTRUCT(BlueprintType)
struct FWarehouseLocation
{
    GENERATED_BODY()

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    FString Name;

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    FString Type;

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    FVector Position = FVector::ZeroVector;

    FWarehouseLocation() {}
    FWarehouseLocation(const FString& InName, const FString& InType, const FVector& InPos)
        : Name(InName), Type(InType), Position(InPos) {}
};

/**
 * A single cell from the backend layout API.
 * Matches: CellDTO { rowIndex, colIndex, cellType }
 */
USTRUCT(BlueprintType)
struct FLayoutCell
{
    GENERATED_BODY()

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    int32 RowIndex = 0;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    int32 ColIndex = 0;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    FString CellType; // EMPTY, SHELF, ROBOT_CHARGE, ORDER_ENTRY, ORDER_EXIT, OBSTACLE

    FLayoutCell() {}
    FLayoutCell(int32 InRow, int32 InCol, const FString& InType)
        : RowIndex(InRow), ColIndex(InCol), CellType(InType) {}
};

/**
 * An inventory item within a spot.
 * Matches: SpotItemDTO { itemId, name, sku, quantityAvailable }
 */
USTRUCT(BlueprintType)
struct FSpotItemData
{
    GENERATED_BODY()

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    int64 ItemId = 0;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    FString ItemName;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    FString Sku;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    int32 QuantityAvailable = 0;

    FSpotItemData() {}
};

/**
 * A warehouse storage spot (shelf location).
 * Matches: SpotDTO { id, code, aisle, section, x, y, items[] }
 */
USTRUCT(BlueprintType)
struct FSpotData
{
    GENERATED_BODY()

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    int64 SpotId = 0;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    FString Code;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    FString Aisle;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    FString Section;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    float X = 0.f;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    float Y = 0.f;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    TArray<FSpotItemData> Items;

    FSpotData() {}
};

/**
 * Layout data fetched from the warehouse-core API.
 * Matches: LayoutResponse { id, name, rows, cols, cellSize, status, cells[] }
 */
USTRUCT(BlueprintType)
struct FWarehouseLayoutData
{
    GENERATED_BODY()

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    int64 LayoutId = 0;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    FString LayoutName;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    int32 Rows = 0;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    int32 Cols = 0;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    float CellSize = 200.0f; // UE units per cell

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    FString Status;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly)
    TArray<FLayoutCell> Cells;
};

/**
 * Procedural warehouse environment builder.
 * Place this actor in the level and it auto-generates:
 *   - Floor, walls, ceiling
 *   - Shelving racks (colored blocks)
 *   - Charging stations (cyan platforms)
 *   - Delivery/dock area (orange platform)
 *   - Navigation markers
 *
 * Now supports dynamic layout from backend API via BuildFromLayout().
 */
UCLASS(BlueprintType, Category = "SmartLogistics")
class AWarehouseEnvironment : public AActor
{
    GENERATED_BODY()

public:
    AWarehouseEnvironment();
    virtual void BeginPlay() override;
    virtual void OnConstruction(const FTransform& Transform) override;

    // ─── Warehouse Dimensions ────────────────────────────────────
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Dimensions")
    float WarehouseWidth = 4000.0f;   // Y axis

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Dimensions")
    float WarehouseDepth = 6000.0f;   // X axis

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Dimensions")
    float WallHeight = 800.0f;

     /**
     * Navigation offset applied to robot target positions via CellToNavigationPosition().
     * Applied as a fraction of CellSize, shifting the target toward the grid center.
     * WARNING: This offset can push waypoints into adjacent SHELF cells, causing robots
     * to clip through shelves. Kept at 0.0 because the backend route planner already
     * ensures waypoints are only on navigable cells (EMPTY corridors), so the exact
     * cell center is always a safe position.
     */
     UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Dimensions",
               meta = (ClampMin = "0.0", ClampMax = "0.5"))
     float RobotNavOffsetFraction = 0.0f;

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

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Colors")
    FLinearColor ReceivingColor = FLinearColor(0.1f, 0.85f, 0.2f);

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Colors")
    FLinearColor PickupEmptyColor = FLinearColor(0.1f, 0.85f, 0.2f);

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Colors")
    FLinearColor PickupFullColor = FLinearColor(0.6f, 0.15f, 0.8f);

    // ─── Pickup Zone Config ──────────────────────────────────────
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Warehouse|Pickup")
    int32 PendingPickupItems = 0;

    // ─── Dynamic Layout State ────────────────────────────────────

    /** The current layout data from the backend API */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "Warehouse|Layout")
    FWarehouseLayoutData CurrentLayout;

    /** Whether we are using a dynamic layout from the API */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "Warehouse|Layout")
    bool bUsingDynamicLayout = false;

    // ─── Named Locations ─────────────────────────────────────────
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "Warehouse|Locations")
    TArray<FWarehouseLocation> Locations;

    /** Find a location by name */
    bool GetLocation(const FString& Name, FWarehouseLocation& OutLocation) const;

    /** Get all locations of a given type */
    void GetLocationsByType(const FString& Type, TArray<FWarehouseLocation>& OutLocations) const;

    /** Get all locations as JSON string */
    FString GetLayoutJson() const;

    // ─── Dynamic Layout API ──────────────────────────────────────

    /**
     * Rebuild the entire warehouse from a layout received from the backend API.
     * Clears all procedural components and rebuilds floor, walls, and all cells.
     */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void BuildFromLayout(const FWarehouseLayoutData& LayoutData);

    /**
     * Convert a grid cell (row, col) to UE world position.
     * Returns the center of the cell in world space.
     */
    FVector CellToWorldPosition(int32 Row, int32 Col) const;

    /**
     * Convert a grid cell (row, col) to a navigation position for robots.
     * Same as CellToWorldPosition but applies RobotNavOffsetFraction to push
     * the target inward from cell edges (prevents robot clipping through walls/shelves).
     * The offset direction is toward the center of the grid.
     */
    FVector CellToNavigationPosition(int32 Row, int32 Col) const;

    /**
     * Get cell size currently in use (from dynamic layout or default).
     */
    float GetCellSize() const;

    // ─── Spot Inventory API ──────────────────────────────────────

    /** Cached spot data, keyed by spot code (e.g. "S-A1-01") */
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "Warehouse|Spots")
    TMap<FString, FSpotData> SpotMap;

    /** Fetch all spots with items from backend API and update SpotMap */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void FetchSpotsFromBackend();

    /** Get spot data by code */
    bool GetSpotByCode(const FString& Code, FSpotData& OutSpot) const;

    /**
     * Get the UE world position for a spot or named location by its code.
     * Checks Locations array first (by Name), then SpotMap (by Code).
     * Returns true if found.
     */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    bool GetSpotPosition(const FString& SpotCode, FVector& OutPosition) const;

    /**
     * Get a default spawn position inside the warehouse.
     * Tries: first DOCK/DELIVERY/RECEIVING location → center of warehouse.
     */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    FVector GetDefaultSpawnPosition() const;

    /**
     * Convert a root point code (e.g. "RP-R02-C03") to UE world position.
     * Parses row/col from the code and uses CellToWorldPosition.
     * Returns true if the code was parsed successfully.
     */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    bool RootPointCodeToPosition(const FString& Code, FVector& OutPosition) const;

    /**
     * Convert an array of root point codes to UE world positions.
     * Returns false if any code fails to parse.
     */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    bool RootPointCodesToPositions(const TArray<FString>& Codes, TArray<FVector>& OutPositions) const;

    /**
     * Find the nearest root point code for a given world position.
     * Reverses CellToWorldPosition: converts world X,Y back to row,col,
     * then formats as "RP-Rxx-Cyy". Clamps to valid layout bounds.
     * Returns empty string if no dynamic layout is available.
     */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    FString FindNearestRootPointCode(const FVector& WorldPosition) const;

    /**
     * Spawn a box visual at a spot to represent a package waiting for pickup.
     * Box is placed on top of the spot location with the SKU label color.
     * Returns the spawned component (or nullptr if spot not found).
     */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    UStaticMeshComponent* SpawnItemVisualAtSpot(const FString& SpotCode, int64 PackageId,
        const FString& Sku, int32 Quantity);

    /**
     * Remove a package visual by PackageId.
     */
    UFUNCTION(BlueprintCallable, Category = "SmartLogistics")
    void RemoveItemVisual(int64 PackageId);

private:
    UPROPERTY()
    TArray<UStaticMeshComponent*> ProceduralComponents;

    /** Cached cube mesh for procedural boxes */
    UPROPERTY()
    UStaticMesh* DefaultCubeMesh = nullptr;

    /** Tracked package visuals by PackageId (for removal after pickup) */
    UPROPERTY()
    TMap<int64, UStaticMeshComponent*> PackageVisuals;

    void ClearProceduralComponents();
    UStaticMeshComponent* AddBox(const FString& Name, FVector Location, FVector Scale, FLinearColor Color, FRotator Rotation = FRotator::ZeroRotator);
    void BuildFloor();
    void BuildWalls();
    void BuildShelves();
    void BuildChargingStations();
    void BuildDeliveryArea();
    void BuildReceivingArea();
    void BuildPickupZone();
    void BuildLabels();
    void BuildLocationMap();

    // ─── Dynamic layout builders ─────────────────────────────────
    void BuildDynamicFloor(int32 Rows, int32 Cols, float CellSz);
    void BuildDynamicWalls(int32 Rows, int32 Cols, float CellSz);
    void BuildDynamicCells(const FWarehouseLayoutData& LayoutData);
    void BuildDynamicLocationMap(const FWarehouseLayoutData& LayoutData);
};