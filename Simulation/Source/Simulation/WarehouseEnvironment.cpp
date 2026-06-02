// Copyright Epic Games, Inc. All Rights Reserved.

#include "WarehouseEnvironment.h"
#include "Components/StaticMeshComponent.h"
#include "Components/TextRenderComponent.h"
#include "UObject/ConstructorHelpers.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "UObject/SoftObjectPath.h"
#include "HttpModule.h"
#include "Interfaces/IHttpResponse.h"
#include "Serialization/JsonSerializer.h"

AWarehouseEnvironment::AWarehouseEnvironment()
{
    PrimaryActorTick.bCanEverTick = false;
}

void AWarehouseEnvironment::BeginPlay()
{
    Super::BeginPlay();

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] BeginPlay - fetching active layout from backend..."));

    // Fetch the active layout from the warehouse-core API
    TSharedRef<IHttpRequest> Request = FHttpModule::Get().CreateRequest();
    Request->SetURL(TEXT("http://localhost:8081/api/layouts/active"));
    Request->SetVerb(TEXT("GET"));
    Request->SetHeader(TEXT("Content-Type"), TEXT("application/json"));

    Request->OnProcessRequestComplete().BindLambda([this](FHttpRequestPtr Req, FHttpResponsePtr Response, bool bSuccess)
    {
        if (!bSuccess || !Response.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("[Warehouse] Failed to fetch layout from backend. Using static layout."));
            return;
        }

        int32 Code = Response->GetResponseCode();
        FString Body = Response->GetContentAsString();

        if (Code != 200)
        {
            UE_LOG(LogTemp, Warning, TEXT("[Warehouse] Layout API returned %d: %s"), Code, *Body);
            return;
        }

        UE_LOG(LogTemp, Log, TEXT("[Warehouse] Got layout response (%d bytes)"), Body.Len());

        // Parse JSON
        TSharedPtr<FJsonObject> RootObj;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Body);
        if (!FJsonSerializer::Deserialize(Reader, RootObj) || !RootObj.IsValid())
        {
            UE_LOG(LogTemp, Error, TEXT("[Warehouse] Failed to parse layout JSON"));
            return;
        }

        FWarehouseLayoutData LayoutData;
        LayoutData.LayoutId = RootObj->GetIntegerField(TEXT("id"));
        LayoutData.LayoutName = RootObj->GetStringField(TEXT("name"));
        LayoutData.Rows = RootObj->GetIntegerField(TEXT("rows"));
        LayoutData.Cols = RootObj->GetIntegerField(TEXT("cols"));
        LayoutData.CellSize = (float)RootObj->GetNumberField(TEXT("cellSize"));
        LayoutData.Status = RootObj->GetStringField(TEXT("status"));

        const TArray<TSharedPtr<FJsonValue>>* CellsArr;
        if (RootObj->TryGetArrayField(TEXT("cells"), CellsArr))
        {
            for (const auto& CellVal : *CellsArr)
            {
                TSharedPtr<FJsonObject> CellObj = CellVal->AsObject();
                FLayoutCell Cell;
                Cell.RowIndex = CellObj->GetIntegerField(TEXT("rowIndex"));
                Cell.ColIndex = CellObj->GetIntegerField(TEXT("colIndex"));
                Cell.CellType = CellObj->GetStringField(TEXT("cellType"));
                LayoutData.Cells.Add(Cell);
            }
        }

        UE_LOG(LogTemp, Log, TEXT("[Warehouse] Parsed layout: %s (%dx%d, %d cells, status=%s)"),
            *LayoutData.LayoutName, LayoutData.Rows, LayoutData.Cols, LayoutData.Cells.Num(), *LayoutData.Status);

        // Rebuild the warehouse from the backend layout
        BuildFromLayout(LayoutData);
    });

    Request->ProcessRequest();
}

void AWarehouseEnvironment::OnConstruction(const FTransform& Transform)
{
    Super::OnConstruction(Transform);

    // Skip if this is the CDO (Class Default Object) — not a real actor instance
    if (!HasActorBegunPlay() && !GetWorld())
    {
        return;
    }

    // Only build static layout if we are NOT using dynamic layout
    if (bUsingDynamicLayout)
    {
        UE_LOG(LogTemp, Log, TEXT("[Warehouse] OnConstruction skipped - using dynamic layout"));
        return;
    }

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Building static procedural environment..."));

    ClearProceduralComponents();

    BuildFloor();
    BuildWalls();
    BuildShelves();
    BuildChargingStations();
    BuildDeliveryArea();
    BuildReceivingArea();
    BuildPickupZone();
    BuildLocationMap();

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Static environment built: %d components, %d locations"), ProceduralComponents.Num(), Locations.Num());
}

// ─── Helpers ────────────────────────────────────────────────────────

void AWarehouseEnvironment::ClearProceduralComponents()
{
    for (UStaticMeshComponent* Comp : ProceduralComponents)
    {
        if (Comp)
        {
            Comp->DestroyComponent();
        }
    }
    ProceduralComponents.Empty();
}

UStaticMeshComponent* AWarehouseEnvironment::AddBox(const FString& Name, FVector Location, FVector Scale, FLinearColor Color, FRotator Rotation)
{
    // Create a new static mesh component
    FString CompName = FString::Printf(TEXT("Env_%s"), *Name);
    UStaticMeshComponent* MeshComp = NewObject<UStaticMeshComponent>(this, *CompName);

    if (!MeshComp) return nullptr;

    MeshComp->RegisterComponent();
    MeshComp->AttachToComponent(GetRootComponent(), FAttachmentTransformRules::KeepRelativeTransform);
    MeshComp->SetRelativeLocation(Location);
    MeshComp->SetRelativeRotation(Rotation);
    MeshComp->SetWorldScale3D(Scale);

    // Load the default cube mesh (LoadObject works outside constructors)
    if (!DefaultCubeMesh)
    {
        DefaultCubeMesh = LoadObject<UStaticMesh>(nullptr, TEXT("/Engine/BasicShapes/Cube.Cube"));
    }
    if (DefaultCubeMesh)
    {
        MeshComp->SetStaticMesh(DefaultCubeMesh);
    }

    // Load M_SolidColor material (MUST have "Color" vector parameter exposed)
    UMaterialInterface* SolidColorMat = LoadObject<UMaterialInterface>(nullptr, TEXT("/Game/Materials/M_SolidColor.M_SolidColor"));
    if (SolidColorMat)
    {
        UMaterialInstanceDynamic* DynMat = UMaterialInstanceDynamic::Create(SolidColorMat, this);
        DynMat->SetVectorParameterValue(FName("Color"), Color);
        MeshComp->SetMaterial(0, DynMat);
    }

    MeshComp->SetCollisionEnabled(ECollisionEnabled::QueryAndPhysics);
    MeshComp->SetGenerateOverlapEvents(false);

    ProceduralComponents.Add(MeshComp);
    return MeshComp;
}

// ─── Floor ──────────────────────────────────────────────────────────

void AWarehouseEnvironment::BuildFloor()
{
    // Main floor
    AddBox(
        TEXT("Floor"),
        FVector(0.0f, 0.0f, -5.0f),             // slightly below origin
        FVector(WarehouseDepth / 100.0f, WarehouseWidth / 100.0f, 0.1f),  // UE scale = cm
        FloorColor
    );

    // Floor grid lines (subtle markings every 1000 units)
    FLinearColor LineColor = FLinearColor(0.2f, 0.2f, 0.25f);
    for (float X = -WarehouseDepth / 2.0f; X <= WarehouseDepth / 2.0f; X += 1000.0f)
    {
        AddBox(
            FString::Printf(TEXT("GridLine_X_%d"), (int32)X),
            FVector(X, 0.0f, 0.5f),
            FVector(0.02f, WarehouseWidth / 100.0f, 0.01f),
            LineColor
        );
    }
    for (float Y = -WarehouseWidth / 2.0f; Y <= WarehouseWidth / 2.0f; Y += 1000.0f)
    {
        AddBox(
            FString::Printf(TEXT("GridLine_Y_%d"), (int32)Y),
            FVector(0.0f, Y, 0.5f),
            FVector(WarehouseDepth / 100.0f, 0.02f, 0.01f),
            LineColor
        );
    }
}

// ─── Walls ──────────────────────────────────────────────────────────

void AWarehouseEnvironment::BuildWalls()
{
    float HalfW = WarehouseWidth / 2.0f;
    float HalfD = WarehouseDepth / 2.0f;
    float WH = WallHeight;
    float WallThick = 10.0f;

    // Back wall (X negative)
    AddBox(TEXT("Wall_Back"), FVector(-HalfD, 0.0f, WH / 2.0f),
           FVector(WallThick / 100.0f, WarehouseWidth / 100.0f, WH / 100.0f), WallColor);

    // Front wall (X positive) — with gap for dock
    AddBox(TEXT("Wall_Front_L"), FVector(HalfD, -HalfW / 2.0f - 500.0f, WH / 2.0f),
           FVector(WallThick / 100.0f, (WarehouseWidth - 1000.0f) / 200.0f, WH / 100.0f), WallColor);
    AddBox(TEXT("Wall_Front_R"), FVector(HalfD, HalfW / 2.0f + 500.0f, WH / 2.0f),
           FVector(WallThick / 100.0f, (WarehouseWidth - 1000.0f) / 200.0f, WH / 100.0f), WallColor);

    // Left wall (Y negative)
    AddBox(TEXT("Wall_Left"), FVector(0.0f, -HalfW, WH / 2.0f),
           FVector(WarehouseDepth / 100.0f, WallThick / 100.0f, WH / 100.0f), WallColor);

    // Right wall (Y positive)
    AddBox(TEXT("Wall_Right"), FVector(0.0f, HalfW, WH / 2.0f),
           FVector(WarehouseDepth / 100.0f, WallThick / 100.0f, WH / 100.0f), WallColor);
}

// ─── Shelves / Estantes ─────────────────────────────────────────────

void AWarehouseEnvironment::BuildShelves()
{
    // Shelves occupy the center area of the warehouse
    float StartX = -WarehouseDepth * 0.25f;
    float StartY = -WarehouseWidth * 0.3f;

    for (int32 Row = 0; Row < NumShelfRows; Row++)
    {
        for (int32 Col = 0; Col < NumShelfUnitsPerRow; Col++)
        {
            float X = StartX + Row * ShelfSpacingX;
            float Y = StartY + Col * ShelfSpacingY;
            float Z = 50.0f;  // shelf height base

            FString Name = FString::Printf(TEXT("Shelf_R%d_C%d"), Row, Col);

            // Shelf base (brown/wooden color)
            FLinearColor UnitColor = ShelfColor;

            // Alternate shelf colors slightly for visual variety
            if (Row % 2 == 0)
                UnitColor = FLinearColor(0.5f, 0.3f, 0.12f);
            else
                UnitColor = FLinearColor(0.6f, 0.4f, 0.18f);

            AddBox(Name, FVector(X, Y, Z), FVector(3.0f, 2.5f, 1.0f), UnitColor);

            // Shelf uprights (darker)
            FLinearColor UprightColor = FLinearColor(0.3f, 0.3f, 0.32f);
            float UprightH = 80.0f;

            // Left upright
            AddBox(Name + "_UL", FVector(X - 120.0f, Y - 80.0f, Z + UprightH / 2.0f),
                   FVector(0.15f, 0.15f, UprightH / 100.0f), UprightColor);
            // Right upright
            AddBox(Name + "_UR", FVector(X - 120.0f, Y + 80.0f, Z + UprightH / 2.0f),
                   FVector(0.15f, 0.15f, UprightH / 100.0f), UprightColor);

            // Top shelf plank
            AddBox(Name + "_Top", FVector(X, Y, Z + UprightH),
                   FVector(3.0f, 2.5f, 0.3f), UnitColor);
        }
    }
}

// ─── Charging Stations ──────────────────────────────────────────────

void AWarehouseEnvironment::BuildChargingStations()
{
    // Charging stations along the left wall (Y negative side)
    float BaseY = -WarehouseWidth / 2.0f + 400.0f;
    float BaseX = WarehouseDepth * 0.3f;

    for (int32 i = 0; i < NumChargingStations; i++)
    {
        float X = BaseX + i * 500.0f;
        float Y = BaseY;

        FString Name = FString::Printf(TEXT("Charger_%d"), i);

        // Charging platform (cyan/teal)
        AddBox(Name + "_Platform", FVector(X, Y, 2.0f),
               FVector(2.5f, 2.5f, 0.1f), ChargingColor);

        // Charging post
        AddBox(Name + "_Post", FVector(X + 80.0f, Y, 40.0f),
               FVector(0.2f, 0.2f, 0.8f), FLinearColor(0.0f, 0.6f, 0.5f));

        // Blinking indicator (bright spot on top)
        AddBox(Name + "_Light", FVector(X + 80.0f, Y, 82.0f),
               FVector(0.15f, 0.15f, 0.15f), FLinearColor(0.0f, 1.0f, 0.9f));
    }
}

// ─── Delivery / Dock Area ───────────────────────────────────────────

void AWarehouseEnvironment::BuildDeliveryArea()
{
    // Delivery area at the front of the warehouse (X positive)
    float DockX = WarehouseDepth / 2.0f - 400.0f;
    float DockW = 1200.0f;
    float DockD = 600.0f;

    // Main dock platform (orange)
    AddBox(TEXT("Dock_Platform"), FVector(DockX, 0.0f, 2.0f),
           FVector(DockD / 100.0f, DockW / 100.0f, 0.15f), DeliveryColor);

    // Dock lane markers (darker orange stripes)
    for (int32 i = -2; i <= 2; i++)
    {
        float Y = i * 250.0f;
        AddBox(FString::Printf(TEXT("Dock_Marker_%d"), i),
               FVector(DockX, Y, 5.0f),
               FVector(5.0f, 0.1f, 0.05f), FLinearColor(0.7f, 0.4f, 0.0f));
    }

    // Dock bumpers (yellow-black)
    FLinearColor BumperColor = FLinearColor(0.9f, 0.85f, 0.0f);
    AddBox(TEXT("Dock_Bumper_L"), FVector(DockX + DockD / 2.0f - 30.0f, -DockW / 2.0f + 20.0f, 10.0f),
           FVector(0.3f, 0.3f, 0.3f), BumperColor);
    AddBox(TEXT("Dock_Bumper_R"), FVector(DockX + DockD / 2.0f - 30.0f, DockW / 2.0f - 20.0f, 10.0f),
           FVector(0.3f, 0.3f, 0.3f), BumperColor);
}

// ─── Receiving Area ─────────────────────────────────────────────────

void AWarehouseEnvironment::BuildReceivingArea()
{
    // Receiving area at the back of the warehouse (X negative)
    float RecX = -WarehouseDepth / 2.0f + 400.0f;
    float RecW = 1000.0f;
    float RecD = 600.0f;

    // Main receiving platform (green)
    AddBox(TEXT("Receiving_Platform"), FVector(RecX, 0.0f, 2.0f),
           FVector(RecD / 100.0f, RecW / 100.0f, 0.15f), ReceivingColor);

    // Receiving lane markers (darker green stripes)
    for (int32 i = -2; i <= 2; i++)
    {
        float Y = i * 200.0f;
        AddBox(FString::Printf(TEXT("Receiving_Marker_%d"), i),
               FVector(RecX, Y, 5.0f),
               FVector(4.0f, 0.1f, 0.05f), FLinearColor(0.05f, 0.55f, 0.1f));
    }

    // Receiving dock bumpers
    FLinearColor RecBumperColor = FLinearColor(0.1f, 0.6f, 0.15f);
    AddBox(TEXT("Receiving_Bumper_L"), FVector(RecX - RecD / 2.0f + 30.0f, -RecW / 2.0f + 20.0f, 10.0f),
           FVector(0.3f, 0.3f, 0.3f), RecBumperColor);
    AddBox(TEXT("Receiving_Bumper_R"), FVector(RecX - RecD / 2.0f + 30.0f, RecW / 2.0f - 20.0f, 10.0f),
           FVector(0.3f, 0.3f, 0.3f), RecBumperColor);

    // Incoming items placeholder boxes (small yellow-green cubes)
    FLinearColor ItemColor = FLinearColor(0.85f, 0.85f, 0.15f);
    AddBox(TEXT("Receiving_Item1"), FVector(RecX - 100.0f, -150.0f, 15.0f),
           FVector(0.8f, 0.8f, 0.8f), ItemColor);
    AddBox(TEXT("Receiving_Item2"), FVector(RecX + 100.0f, 150.0f, 15.0f),
           FVector(0.8f, 0.8f, 0.8f), ItemColor);
}

// ─── Pickup Zone ────────────────────────────────────────────────────

void AWarehouseEnvironment::BuildPickupZone()
{
    // Pickup zone between shelves and delivery area (center-right)
    float PickX = WarehouseDepth * 0.15f;
    float PickY = WarehouseWidth / 2.0f - 600.0f;  // along right wall
    float PickW = 800.0f;
    float PickD = 500.0f;

    // Choose color based on whether items are pending pickup
    FLinearColor ZoneColor = (PendingPickupItems > 0) ? PickupFullColor : PickupEmptyColor;

    // Main pickup platform
    AddBox(TEXT("Pickup_Platform"), FVector(PickX, PickY, 2.0f),
           FVector(PickD / 100.0f, PickW / 100.0f, 0.15f), ZoneColor);

    // Pickup lane markers
    FLinearColor MarkerColor = (PendingPickupItems > 0)
        ? FLinearColor(0.4f, 0.1f, 0.6f)   // purple markers when full
        : FLinearColor(0.05f, 0.55f, 0.1f); // green markers when empty
    for (int32 i = -1; i <= 1; i++)
    {
        float Y = PickY + i * 250.0f;
        AddBox(FString::Printf(TEXT("Pickup_Marker_%d"), i),
               FVector(PickX, Y, 5.0f),
               FVector(4.0f, 0.1f, 0.05f), MarkerColor);
    }

    // Pickup staging posts (corners)
    FLinearColor PostColor = FLinearColor(0.5f, 0.5f, 0.5f);
    float CornerOffX = PickD / 2.0f - 30.0f;
    float CornerOffY = PickW / 2.0f - 30.0f;
    AddBox(TEXT("Pickup_Post_TL"), FVector(PickX - CornerOffX, PickY - CornerOffY, 30.0f),
           FVector(0.2f, 0.2f, 0.6f), PostColor);
    AddBox(TEXT("Pickup_Post_TR"), FVector(PickX - CornerOffX, PickY + CornerOffY, 30.0f),
           FVector(0.2f, 0.2f, 0.6f), PostColor);
    AddBox(TEXT("Pickup_Post_BL"), FVector(PickX + CornerOffX, PickY - CornerOffY, 30.0f),
           FVector(0.2f, 0.2f, 0.6f), PostColor);
    AddBox(TEXT("Pickup_Post_BR"), FVector(PickX + CornerOffX, PickY + CornerOffY, 30.0f),
           FVector(0.2f, 0.2f, 0.6f), PostColor);

    // Pending item boxes (only shown when items are waiting)
    if (PendingPickupItems > 0)
    {
        FLinearColor ItemColor = FLinearColor(0.7f, 0.2f, 0.9f);
        for (int32 i = 0; i < FMath::Min(PendingPickupItems, 4); i++)
        {
            float Y = PickY - 200.0f + i * 130.0f;
            AddBox(FString::Printf(TEXT("Pickup_Item_%d"), i),
                   FVector(PickX, Y, 15.0f),
                   FVector(0.8f, 0.8f, 0.8f), ItemColor);
        }
    }
}

// ─── Labels (stub) ──────────────────────────────────────────────────

void AWarehouseEnvironment::BuildLabels()
{
    // Placeholder for future text labels / signage
}

// ─── Location Map ────────────────────────────────────────────────────

void AWarehouseEnvironment::BuildLocationMap()
{
    Locations.Empty();

    // ── Shelf locations (STORAGE) ──
    float StartX = -WarehouseDepth * 0.25f;
    float StartY = -WarehouseWidth * 0.3f;

    for (int32 Row = 0; Row < NumShelfRows; Row++)
    {
        for (int32 Col = 0; Col < NumShelfUnitsPerRow; Col++)
        {
            float X = StartX + Row * ShelfSpacingX;
            float Y = StartY + Col * ShelfSpacingY;

            // Use letter for row (A, B, C, D...) and number for column
            FString RowLetter = FString::Chr('A' + Row);
            FString ShelfName = FString::Printf(TEXT("S-%s%02d"), *RowLetter, Col + 1);
            Locations.Add(FWarehouseLocation(ShelfName, TEXT("STORAGE"), FVector(X, Y, 0.0f)));
        }
    }

    // ── Charging stations (CHARGING) ──
    float BaseY = -WarehouseWidth / 2.0f + 400.0f;
    float BaseX = WarehouseDepth * 0.3f;

    for (int32 i = 0; i < NumChargingStations; i++)
    {
        float X = BaseX + i * 500.0f;
        FString Name = FString::Printf(TEXT("DOCK-%02d"), i + 1);
        Locations.Add(FWarehouseLocation(Name, TEXT("CHARGING"), FVector(X, BaseY, 0.0f)));
    }

    // ── Delivery / Dock area (DOCK) ──
    float DockX = WarehouseDepth / 2.0f - 400.0f;
    Locations.Add(FWarehouseLocation(TEXT("DELIVERY-ZONE"), TEXT("DOCK"), FVector(DockX, 0.0f, 0.0f)));

    // ── Receiving area (RECEIVING) ──
    float RecX = -WarehouseDepth / 2.0f + 400.0f;
    Locations.Add(FWarehouseLocation(TEXT("RECEIVING-ZONE"), TEXT("RECEIVING"), FVector(RecX, 0.0f, 0.0f)));

    // ── Pickup zone (PICKUP) ──
    float PickX = WarehouseDepth * 0.15f;
    float PickY = WarehouseWidth / 2.0f - 600.0f;
    Locations.Add(FWarehouseLocation(TEXT("PICKUP-ZONE"), TEXT("PICKUP"), FVector(PickX, PickY, 0.0f)));

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Location map built with %d locations:"), Locations.Num());
    for (const auto& Loc : Locations)
    {
        UE_LOG(LogTemp, Log, TEXT("  %s [%s] at (%.0f, %.0f, %.0f)"),
            *Loc.Name, *Loc.Type, Loc.Position.X, Loc.Position.Y, Loc.Position.Z);
    }
}

bool AWarehouseEnvironment::GetLocation(const FString& Name, FWarehouseLocation& OutLocation) const
{
    for (const auto& Loc : Locations)
    {
        if (Loc.Name == Name)
        {
            OutLocation = Loc;
            return true;
        }
    }
    return false;
}

void AWarehouseEnvironment::GetLocationsByType(const FString& Type, TArray<FWarehouseLocation>& OutLocations) const
{
    OutLocations.Empty();
    for (const auto& Loc : Locations)
    {
        if (Loc.Type == Type)
        {
            OutLocations.Add(Loc);
        }
    }
}

FString AWarehouseEnvironment::GetLayoutJson() const
{
    // Build JSON manually using string concatenation to avoid Printf format issues with curly braces
    FString Json = TEXT("{\"locations\":[");
    for (int32 i = 0; i < Locations.Num(); i++)
    {
        const auto& Loc = Locations[i];
        if (i > 0) Json += TEXT(",");

        FString LocEntry = TEXT("{\"name\":\"") + Loc.Name
            + TEXT("\",\"type\":\"") + Loc.Type
            + TEXT("\",\"position\":{\"x\":") + FString::SanitizeFloat(Loc.Position.X)
            + TEXT(",\"y\":") + FString::SanitizeFloat(Loc.Position.Y)
            + TEXT(",\"z\":") + FString::SanitizeFloat(Loc.Position.Z)
            + TEXT("}}");

        Json += LocEntry;
    }
    Json += TEXT("]}");
    return Json;
}

// ─── Dynamic Layout API ──────────────────────────────────────────

float AWarehouseEnvironment::GetCellSize() const
{
    if (bUsingDynamicLayout)
    {
        return CurrentLayout.CellSize;
    }
    return 200.0f;
}

FVector AWarehouseEnvironment::CellToWorldPosition(int32 Row, int32 Col) const
{
    float CellSz = GetCellSize();
    // Cell center: row/col * cellSize + cellSize/2
    float X = Row * CellSz + CellSz / 2.0f;
    // Mirror Y axis so backend col 0 (left) appears on right in UE5 view
    float Y = (bUsingDynamicLayout && CurrentLayout.Cols > 0)
        ? (CurrentLayout.Cols - 1 - Col) * CellSz + CellSz / 2.0f
        : Col * CellSz + CellSz / 2.0f;
    return GetActorLocation() + FVector(X, Y, 0.0f);
}

FVector AWarehouseEnvironment::CellToNavigationPosition(int32 Row, int32 Col) const
{
    float CellSz = GetCellSize();
    float OffsetAmount = CellSz * RobotNavOffsetFraction;

    // Determine center of the grid
    float CenterRow = 0.0f;
    float CenterCol = 0.0f;
    if (bUsingDynamicLayout && CurrentLayout.Rows > 0 && CurrentLayout.Cols > 0)
    {
        CenterRow = (CurrentLayout.Rows - 1) / 2.0f;
        CenterCol = (CurrentLayout.Cols - 1) / 2.0f;
    }

    // Direction from this cell toward grid center (normalized)
    float DirX = CenterRow - (float)Row;
    float DirY = CenterCol - (float)Col;
    float DirLen = FMath::Sqrt(DirX * DirX + DirY * DirY);

    // For cells at the grid center (DirLen ≈ 0), offset toward the interior
    // by checking which edge is closest and pushing away from it
    if (DirLen < 0.01f)
    {
        // Already at center — no offset needed (center cells are safe)
        return CellToWorldPosition(Row, Col);
    }

    // Normalize direction
    DirX /= DirLen;
    DirY /= DirLen;

    // Apply offset toward center
    FVector BasePos = CellToWorldPosition(Row, Col);
    FVector Offset = FVector(DirX * OffsetAmount, DirY * OffsetAmount, 0.0f);

    return BasePos + Offset;
}

void AWarehouseEnvironment::BuildFromLayout(const FWarehouseLayoutData& LayoutData)
{
    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Building dynamic layout: %s (%d x %d, cellSize=%.1f, %d cells)"),
        *LayoutData.LayoutName, LayoutData.Rows, LayoutData.Cols, LayoutData.CellSize, LayoutData.Cells.Num());

    CurrentLayout = LayoutData;
    bUsingDynamicLayout = true;

    ClearProceduralComponents();
    Locations.Empty();

    float CellSz = LayoutData.CellSize;

    // Update warehouse dimensions to match layout
    WarehouseDepth = LayoutData.Rows * CellSz;
    WarehouseWidth = LayoutData.Cols * CellSz;

    BuildDynamicFloor(LayoutData.Rows, LayoutData.Cols, CellSz);
    BuildDynamicWalls(LayoutData.Rows, LayoutData.Cols, CellSz);
    BuildDynamicCells(LayoutData);
    BuildDynamicLocationMap(LayoutData);

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Dynamic layout built: %d components, %d locations"),
        ProceduralComponents.Num(), Locations.Num());

    // Fetch spot inventory data from backend (maps spot codes to items)
    FetchSpotsFromBackend();
}

void AWarehouseEnvironment::BuildDynamicFloor(int32 Rows, int32 Cols, float CellSz)
{
    float TotalW = Cols * CellSz;
    float TotalD = Rows * CellSz;

    // Main floor
    AddBox(
        TEXT("Floor"),
        FVector(TotalD / 2.0f, TotalW / 2.0f, -5.0f),
        FVector(TotalD / 100.0f, TotalW / 100.0f, 0.1f),
        FloorColor
    );

    // Grid lines
    FLinearColor LineColor = FLinearColor(0.2f, 0.2f, 0.25f);
    for (int32 R = 0; R <= Rows; R++)
    {
        float X = R * CellSz;
        AddBox(
            FString::Printf(TEXT("GridLine_R%d"), R),
            FVector(X, TotalW / 2.0f, 0.5f),
            FVector(0.02f, TotalW / 100.0f, 0.01f),
            LineColor
        );
    }
    for (int32 C = 0; C <= Cols; C++)
    {
        float Y = C * CellSz;
        AddBox(
            FString::Printf(TEXT("GridLine_C%d"), C),
            FVector(TotalD / 2.0f, Y, 0.5f),
            FVector(TotalD / 100.0f, 0.02f, 0.01f),
            LineColor
        );
    }
}

void AWarehouseEnvironment::BuildDynamicWalls(int32 Rows, int32 Cols, float CellSz)
{
    float TotalW = Cols * CellSz;
    float TotalD = Rows * CellSz;
    float WH = WallHeight;
    float WallThick = 10.0f;

    // Back wall (X = 0)
    AddBox(TEXT("Wall_Back"), FVector(-WallThick / 2.0f, TotalW / 2.0f, WH / 2.0f),
        FVector(WallThick / 100.0f, TotalW / 100.0f, WH / 100.0f), WallColor);

    // Front wall (X = TotalD) — with gap for dock
    float GapWidth = CellSz * 2.0f;
    float SideW = (TotalW - GapWidth) / 2.0f;
    AddBox(TEXT("Wall_Front_L"), FVector(TotalD + WallThick / 2.0f, SideW / 2.0f, WH / 2.0f),
        FVector(WallThick / 100.0f, SideW / 100.0f, WH / 100.0f), WallColor);
    AddBox(TEXT("Wall_Front_R"), FVector(TotalD + WallThick / 2.0f, TotalW - SideW / 2.0f, WH / 2.0f),
        FVector(WallThick / 100.0f, SideW / 100.0f, WH / 100.0f), WallColor);

    // Left wall (Y = 0)
    AddBox(TEXT("Wall_Left"), FVector(TotalD / 2.0f, -WallThick / 2.0f, WH / 2.0f),
        FVector(TotalD / 100.0f, WallThick / 100.0f, WH / 100.0f), WallColor);

    // Right wall (Y = TotalW)
    AddBox(TEXT("Wall_Right"), FVector(TotalD / 2.0f, TotalW + WallThick / 2.0f, WH / 2.0f),
        FVector(TotalD / 100.0f, WallThick / 100.0f, WH / 100.0f), WallColor);
}

void AWarehouseEnvironment::BuildDynamicCells(const FWarehouseLayoutData& LayoutData)
{
    float CellSz = LayoutData.CellSize;
    float CellHalfScale = CellSz / 100.0f; // Convert UE cm to mesh scale

    int32 ShelfCounter = 0;
    int32 ChargeCounter = 0;
    int32 EntryCounter = 0;
    int32 ExitCounter = 0;

    for (const FLayoutCell& Cell : LayoutData.Cells)
    {
        FVector Center = CellToWorldPosition(Cell.RowIndex, Cell.ColIndex);

        if (Cell.CellType == TEXT("SHELF"))
        {
            FString Name = FString::Printf(TEXT("Shelf_%d_%d"), Cell.RowIndex, Cell.ColIndex);
            FString RowLetter = FString::Chr('A' + Cell.RowIndex);
            FString ShelfLabel = FString::Printf(TEXT("S-%s%02d"), *RowLetter, Cell.ColIndex + 1);

            FLinearColor UnitColor = (Cell.RowIndex % 2 == 0)
                ? FLinearColor(0.5f, 0.3f, 0.12f)
                : FLinearColor(0.6f, 0.4f, 0.18f);

            // Shelf base
            AddBox(Name, Center + FVector(0, 0, 50.0f),
                FVector(CellHalfScale * 0.8f, CellHalfScale * 0.7f, 1.0f), UnitColor);

            // Shelf uprights
            float UprightH = 80.0f;
            FLinearColor UprightColor = FLinearColor(0.3f, 0.3f, 0.32f);
            float Offset = CellSz * 0.3f;
            AddBox(Name + "_UL", Center + FVector(-Offset, -Offset, 50.0f + UprightH / 2.0f),
                FVector(0.15f, 0.15f, UprightH / 100.0f), UprightColor);
            AddBox(Name + "_UR", Center + FVector(-Offset, Offset, 50.0f + UprightH / 2.0f),
                FVector(0.15f, 0.15f, UprightH / 100.0f), UprightColor);

            // Top plank
            AddBox(Name + "_Top", Center + FVector(0, 0, 50.0f + UprightH),
                FVector(CellHalfScale * 0.8f, CellHalfScale * 0.7f, 0.3f), UnitColor);

            ShelfCounter++;
        }
        else if (Cell.CellType == TEXT("CHARGING"))
        {
            FString Name = FString::Printf(TEXT("Charger_%d"), ChargeCounter);

            // Platform
            AddBox(Name + "_Platform", Center + FVector(0, 0, 2.0f),
                FVector(CellHalfScale * 0.6f, CellHalfScale * 0.6f, 0.1f), ChargingColor);

            // Post
            AddBox(Name + "_Post", Center + FVector(CellSz * 0.2f, 0, 40.0f),
                FVector(0.2f, 0.2f, 0.8f), FLinearColor(0.0f, 0.6f, 0.5f));

            // Light
            AddBox(Name + "_Light", Center + FVector(CellSz * 0.2f, 0, 82.0f),
                FVector(0.15f, 0.15f, 0.15f), FLinearColor(0.0f, 1.0f, 0.9f));

            ChargeCounter++;
        }
        else if (Cell.CellType == TEXT("RECEIVING_DOCK"))
        {
            FString Name = FString::Printf(TEXT("Entry_%d"), EntryCounter);
            // Green receiving platform
            AddBox(Name, Center + FVector(0, 0, 2.0f),
                FVector(CellHalfScale * 0.9f, CellHalfScale * 0.9f, 0.15f), ReceivingColor);

            // Arrow indicator
            AddBox(Name + "_Arrow", Center + FVector(-CellSz * 0.3f, 0, 5.0f),
                FVector(0.3f, 0.1f, 0.05f), FLinearColor(0.0f, 1.0f, 0.0f));

            EntryCounter++;
        }
        else if (Cell.CellType == TEXT("DELIVERY_DOCK"))
        {
            FString Name = FString::Printf(TEXT("Exit_%d"), ExitCounter);
            // Orange delivery platform
            AddBox(Name, Center + FVector(0, 0, 2.0f),
                FVector(CellHalfScale * 0.9f, CellHalfScale * 0.9f, 0.15f), DeliveryColor);

            // Arrow indicator
            AddBox(Name + "_Arrow", Center + FVector(CellSz * 0.3f, 0, 5.0f),
                FVector(0.3f, 0.1f, 0.05f), FLinearColor(1.0f, 0.7f, 0.0f));

            ExitCounter++;
        }
        else if (Cell.CellType == TEXT("ROBOT_SPAWN"))
        {
            FString Name = FString::Printf(TEXT("Spawn_%d_%d"), Cell.RowIndex, Cell.ColIndex);
            // Blue spawn marker
            AddBox(Name, Center + FVector(0, 0, 2.0f),
                FVector(CellHalfScale * 0.7f, CellHalfScale * 0.7f, 0.1f),
                FLinearColor(0.2f, 0.4f, 0.9f));

            // Spawn indicator arrow
            AddBox(Name + "_Marker", Center + FVector(0, 0, 10.0f),
                FVector(0.3f, 0.3f, 0.3f), FLinearColor(0.3f, 0.5f, 1.0f));
        }
        else if (Cell.CellType == TEXT("OBSTACLE"))
        {
            FString Name = FString::Printf(TEXT("Obstacle_%d_%d"), Cell.RowIndex, Cell.ColIndex);
            AddBox(Name, Center + FVector(0, 0, 25.0f),
                FVector(CellHalfScale * 0.5f, CellHalfScale * 0.5f, 0.5f), FLinearColor(0.4f, 0.4f, 0.4f));
        }
    }

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Dynamic cells: %d shelves, %d chargers, %d entries, %d exits"),
        ShelfCounter, ChargeCounter, EntryCounter, ExitCounter);
}

void AWarehouseEnvironment::BuildDynamicLocationMap(const FWarehouseLayoutData& LayoutData)
{
    Locations.Empty();

    int32 ShelfCounter = 0;
    int32 ChargeCounter = 0;
    int32 EntryCounter = 0;
    int32 ExitCounter = 0;

    for (const FLayoutCell& Cell : LayoutData.Cells)
    {
        FVector WorldPos = CellToWorldPosition(Cell.RowIndex, Cell.ColIndex);

        if (Cell.CellType == TEXT("SHELF"))
        {
            FString RowLetter = FString::Chr('A' + Cell.RowIndex);
            FString Name = FString::Printf(TEXT("S-%s%02d"), *RowLetter, Cell.ColIndex + 1);
            Locations.Add(FWarehouseLocation(Name, TEXT("STORAGE"), WorldPos));
            ShelfCounter++;
        }
        else if (Cell.CellType == TEXT("CHARGING"))
        {
            FString Name = FString::Printf(TEXT("CHARGER-%02d"), ChargeCounter + 1);
            Locations.Add(FWarehouseLocation(Name, TEXT("CHARGING"), WorldPos));
            ChargeCounter++;
        }
        else if (Cell.CellType == TEXT("RECEIVING_DOCK"))
        {
            FString Name = FString::Printf(TEXT("ENTRY-%02d"), EntryCounter + 1);
            Locations.Add(FWarehouseLocation(Name, TEXT("ORDER_ENTRY"), WorldPos));
            EntryCounter++;
        }
        else if (Cell.CellType == TEXT("DELIVERY_DOCK"))
        {
            FString Name = FString::Printf(TEXT("EXIT-%02d"), ExitCounter + 1);
            Locations.Add(FWarehouseLocation(Name, TEXT("ORDER_EXIT"), WorldPos));
            ExitCounter++;
        }
        else if (Cell.CellType == TEXT("ROBOT_SPAWN"))
        {
            FString Name = FString::Printf(TEXT("SPAWN-%02d"), Cell.ColIndex + 1);
            Locations.Add(FWarehouseLocation(Name, TEXT("SPAWN"), WorldPos));
        }
    }

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Dynamic location map: %d locations"), Locations.Num());
    for (const auto& Loc : Locations)
    {
        UE_LOG(LogTemp, Log, TEXT("  %s [%s] at (%.0f, %.0f, %.0f)"),
            *Loc.Name, *Loc.Type, Loc.Position.X, Loc.Position.Y, Loc.Position.Z);
    }
}

// ─── Spot Inventory API ──────────────────────────────────────────

void AWarehouseEnvironment::FetchSpotsFromBackend()
{
    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> Request = FHttpModule::Get().CreateRequest();
    Request->SetURL(TEXT("http://localhost:8081/api/v1/warehouse/spots"));
    Request->SetVerb(TEXT("GET"));
    Request->SetHeader(TEXT("Content-Type"), TEXT("application/json"));

    Request->OnProcessRequestComplete().BindLambda([this](FHttpRequestPtr, FHttpResponsePtr Response, bool bWasSuccessful)
    {
        if (!bWasSuccessful || !Response.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("FetchSpotsFromBackend: HTTP request failed"));
            return;
        }

        FString JsonStr = Response->GetContentAsString();
        TSharedPtr<FJsonValue> RootValue;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(JsonStr);

        if (!FJsonSerializer::Deserialize(Reader, RootValue) || !RootValue.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("FetchSpotsFromBackend: Failed to parse JSON"));
            return;
        }

        const TArray<TSharedPtr<FJsonValue>>* SpotsArray;
        if (!RootValue->TryGetArray(SpotsArray))
        {
            UE_LOG(LogTemp, Warning, TEXT("FetchSpotsFromBackend: Expected JSON array"));
            return;
        }

        SpotMap.Empty();

        for (const auto& SpotVal : *SpotsArray)
        {
            const TSharedPtr<FJsonObject>* SpotObj;
            if (!SpotVal->TryGetObject(SpotObj))
                continue;

            FSpotData Spot;
            Spot.SpotId = (*SpotObj)->GetIntegerField(TEXT("id"));
            Spot.Code   = (*SpotObj)->GetStringField(TEXT("code"));

            if ((*SpotObj)->HasField(TEXT("aisle")))
                Spot.Aisle = (*SpotObj)->GetStringField(TEXT("aisle"));
            if ((*SpotObj)->HasField(TEXT("section")))
                Spot.Section = (*SpotObj)->GetStringField(TEXT("section"));
            if ((*SpotObj)->HasField(TEXT("x")))
                Spot.X = (*SpotObj)->GetNumberField(TEXT("x"));
            if ((*SpotObj)->HasField(TEXT("y")))
                Spot.Y = (*SpotObj)->GetNumberField(TEXT("y"));

            // Parse items array
            if ((*SpotObj)->HasField(TEXT("items")))
            {
                const TArray<TSharedPtr<FJsonValue>>* ItemsArray;
                if ((*SpotObj)->TryGetArrayField(TEXT("items"), ItemsArray))
                {
                    for (const auto& ItemVal : *ItemsArray)
                    {
                        const TSharedPtr<FJsonObject>* ItemObj;
                        if (!ItemVal->TryGetObject(ItemObj))
                            continue;

                        FSpotItemData Item;
                        Item.ItemId = (*ItemObj)->GetIntegerField(TEXT("itemId"));
                        Item.ItemName = (*ItemObj)->GetStringField(TEXT("name"));
                        if ((*ItemObj)->HasField(TEXT("sku")))
                            Item.Sku = (*ItemObj)->GetStringField(TEXT("sku"));
                        if ((*ItemObj)->HasField(TEXT("quantityAvailable")))
                            Item.QuantityAvailable = (*ItemObj)->GetIntegerField(TEXT("quantityAvailable"));

                        Spot.Items.Add(Item);
                    }
                }
            }

            SpotMap.Add(Spot.Code, Spot);
            UE_LOG(LogTemp, Log, TEXT("Spot loaded: %s (aisle=%s, section=%s, items=%d)"),
                *Spot.Code, *Spot.Aisle, *Spot.Section, Spot.Items.Num());
        }

        UE_LOG(LogTemp, Log, TEXT("FetchSpotsFromBackend: Loaded %d spots"), SpotMap.Num());
    });

    Request->ProcessRequest();
}

bool AWarehouseEnvironment::GetSpotByCode(const FString& Code, FSpotData& OutSpot) const
{
    const FSpotData* Found = SpotMap.Find(Code);
    if (Found)
    {
        OutSpot = *Found;
        return true;
    }
    return false;
}

bool AWarehouseEnvironment::GetSpotPosition(const FString& SpotCode, FVector& OutPosition) const
{
    // 0) Root point codes: RP-Rxx-Cyy → parse row/col and use CellToWorldPosition
    //    This is the primary resolution for route waypoints from the backend.
    //    Uses exact cell center (CellToWorldPosition) because the backend route planner
    //    already ensures waypoints are only on navigable cells (EMPTY corridors).
    //    CellToNavigationPosition was previously pushing waypoints toward the grid center,
    //    which could shift them into adjacent SHELF cells and cause robots to clip through shelves.
    if (SpotCode.StartsWith(TEXT("RP-R")))
    {
        // Parse "RP-R02-C01" → Row=2, Col=1
        FString Code = SpotCode.RightChop(4); // "02-C01"
        int32 DashIdx;
        if (Code.FindChar('-', DashIdx))
        {
            FString RowStr = Code.Left(DashIdx);      // "02"
            FString ColStr = Code.RightChop(DashIdx + 2); // "01" (skip "-C")
            int32 Row = FCString::Atoi(*RowStr);
            int32 Col = FCString::Atoi(*ColStr);
            OutPosition = CellToWorldPosition(Row, Col);
            UE_LOG(LogTemp, Log, TEXT("[Warehouse] GetSpotPosition: Root point '%s' → Row=%d Col=%d → %s"),
                *SpotCode, Row, Col, *OutPosition.ToString());
            return true;
        }
    }

    // 1) Try named locations (RECEIVING-ZONE, DELIVERY-ZONE, ENTRY-xx, EXIT-xx, etc.)
    for (const auto& Loc : Locations)
    {
        if (Loc.Name == SpotCode)
        {
            OutPosition = Loc.Position;
            return true;
        }
    }

    // 2) Try SpotMap (shelf spots like SP-A1-01) — transform backend coords to UE5
    //    Backend: x = col*cellSize, y = row*cellSize (corner-based, x→Y axis, y→X axis)
    //    UE5:     X = (row+0.5)*cellSize, Y = (col+0.5)*cellSize (center-based)
    //    Transform: UE5_X = backend_y + cellSize/2, UE5_Y = backend_x + cellSize/2
    const FSpotData* Spot = SpotMap.Find(SpotCode);
    if (Spot)
    {
        float CellSz = GetCellSize();
        float UE5_X = Spot->Y + CellSz / 2.0f;
        // Match CellToWorldPosition Y mirroring: Y = (Cols-1-Col)*CellSz + CellSz/2
        // Backend Spot->X = col*CellSz, so UE5_Y = (Cols-0.5)*CellSz - Spot->X
        float UE5_Y = (CurrentLayout.Cols - 0.5f) * CellSz - Spot->X;
        OutPosition = GetActorLocation() + FVector(UE5_X, UE5_Y, 0.0f);
        UE_LOG(LogTemp, Log, TEXT("[Warehouse] GetSpotPosition: Spot '%s' backend(%.0f,%.0f) → UE5(%.0f,%.0f)"),
            *SpotCode, Spot->X, Spot->Y, UE5_X, UE5_Y);
        return true;
    }

    // 3) Fuzzy matching: map common backend codes to location types/names
    //    RECV-xx → ORDER_ENTRY,  DELIV-xx/SHIP-xx → ORDER_EXIT
    FString UpperCode = SpotCode.ToUpper();
    FString MatchType;
    if (UpperCode.StartsWith(TEXT("RECV")) || UpperCode.StartsWith(TEXT("RECEP")))
        MatchType = TEXT("ORDER_ENTRY");
    else if (UpperCode.StartsWith(TEXT("DELIV")) || UpperCode.StartsWith(TEXT("SHIP")) || UpperCode.StartsWith(TEXT("DISP")))
        MatchType = TEXT("ORDER_EXIT");
    else if (UpperCode.StartsWith(TEXT("CHARGE")) || UpperCode.StartsWith(TEXT("DOCK")))
        MatchType = TEXT("CHARGING");

    if (!MatchType.IsEmpty())
    {
        for (const auto& Loc : Locations)
        {
            if (Loc.Type == MatchType)
            {
                OutPosition = Loc.Position;
                UE_LOG(LogTemp, Log, TEXT("[Warehouse] GetSpotPosition: '%s' matched %s location '%s'"),
                    *SpotCode, *MatchType, *Loc.Name);
                return true;
            }
        }
    }

    // 4) Last resort: try matching by Location Type prefix
    for (const auto& Loc : Locations)
    {
        if (SpotCode.StartsWith(Loc.Type) || Loc.Type.StartsWith(SpotCode))
        {
            OutPosition = Loc.Position;
            return true;
        }
    }

    UE_LOG(LogTemp, Warning, TEXT("[Warehouse] GetSpotPosition: '%s' not found (tried %d locations, %d spots)"),
        *SpotCode, Locations.Num(), SpotMap.Num());
    return false;
}

FVector AWarehouseEnvironment::GetDefaultSpawnPosition() const
{
    // 1. Try to find a DOCK, DELIVERY, or RECEIVING location
    for (const auto& Loc : Locations)
    {
        if (Loc.Type.Contains(TEXT("DOCK")) ||
            Loc.Type.Contains(TEXT("DELIVERY")) ||
            Loc.Type.Contains(TEXT("RECEIVING")) ||
            Loc.Type.Contains(TEXT("RECEPTION")))
        {
            UE_LOG(LogTemp, Log, TEXT("[Warehouse] Default spawn at %s location: %s"),
                *Loc.Type, *Loc.Position.ToString());
            return Loc.Position;
        }
    }

    // 2. Try charging stations
    for (const auto& Loc : Locations)
    {
        if (Loc.Type.Contains(TEXT("CHARGE")))
        {
            UE_LOG(LogTemp, Log, TEXT("[Warehouse] Default spawn at charging: %s"), *Loc.Position.ToString());
            return Loc.Position;
        }
    }

    // 3. First location in the array
    if (Locations.Num() > 0)
    {
        UE_LOG(LogTemp, Log, TEXT("[Warehouse] Default spawn at first location: %s"),
            *Locations[0].Position.ToString());
        return Locations[0].Position;
    }

    // 4. Center of the warehouse
    FVector Center = GetActorLocation();
    if (bUsingDynamicLayout)
    {
        Center = CellToWorldPosition(CurrentLayout.Rows / 2, CurrentLayout.Cols / 2);
    }
    else
    {
        Center += FVector(WarehouseDepth / 2, WarehouseWidth / 2, 0);
    }
    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Default spawn at warehouse center: %s"), *Center.ToString());
    return Center;
}

UStaticMeshComponent* AWarehouseEnvironment::SpawnItemVisualAtSpot(const FString& SpotCode, int64 PackageId,
    const FString& Sku, int32 Quantity)
{
    FVector SpotPos;
    if (!GetSpotPosition(SpotCode, SpotPos))
    {
        UE_LOG(LogTemp, Warning, TEXT("[Warehouse] Cannot spawn item visual - spot '%s' not found"), *SpotCode);
        return nullptr;
    }

    // Stack box on top of existing items - make it tall enough to be visible above robots
    int32 StackIndex = PackageVisuals.Num();
    float BoxHeight = 120.0f; // Higher than robots (which are ~60cm)
    FVector BoxLocation = SpotPos + FVector(0, 0, BoxHeight + StackIndex * 100.0f);

    FString BoxName = FString::Printf(TEXT("PkgBox_%lld_%s"), PackageId, *Sku);
    FLinearColor BoxColor = FLinearColor(0.1f, 0.9f, 0.3f); // Bright green for packages (highly visible)

    UStaticMeshComponent* BoxComp = AddBox(*BoxName, BoxLocation, FVector(0.5f, 0.5f, 0.5f), BoxColor);

    if (BoxComp)
    {
        PackageVisuals.Add(PackageId, BoxComp);
        UE_LOG(LogTemp, Log, TEXT("[Warehouse] Spawned package visual at spot '%s': pkg=%lld sku=%s qty=%d pos=%s"),
            *SpotCode, PackageId, *Sku, Quantity, *BoxLocation.ToString());
    }

    return BoxComp;
}

void AWarehouseEnvironment::RemoveItemVisual(int64 PackageId)
{
    UStaticMeshComponent** Found = PackageVisuals.Find(PackageId);
    if (Found && *Found)
    {
        (*Found)->DestroyComponent();
        PackageVisuals.Remove(PackageId);
        UE_LOG(LogTemp, Log, TEXT("[Warehouse] Removed package visual for pkg=%lld"), PackageId);
    }
}

// ─── Root Point Code → Position Mapping ──────────────────────────

bool AWarehouseEnvironment::RootPointCodeToPosition(const FString& Code, FVector& OutPosition) const
{
    // Root point codes: "RP-R00-C00", "RP-R02-C03", etc.
    if (Code.StartsWith(TEXT("RP-")))
    {
        FString Rest = Code.RightChop(3); // Remove "RP-"

        int32 CPos = Rest.Find(TEXT("-C"), ESearchCase::CaseSensitive);
        if (Rest.StartsWith(TEXT("R")) && CPos > 1)
        {
            FString RowStr = Rest.Mid(1, CPos - 1);
            FString ColStr = Rest.RightChop(CPos + 2);

            int32 Row = FCString::Atoi(*RowStr);
            int32 Col = FCString::Atoi(*ColStr);

            OutPosition = CellToWorldPosition(Row, Col);
            UE_LOG(LogTemp, Log, TEXT("[Warehouse] RootPoint '%s' -> R=%d, C=%d -> %s"),
                *Code, Row, Col, *OutPosition.ToString());
            return true;
        }

        UE_LOG(LogTemp, Warning, TEXT("[Warehouse] Failed to parse root point code: %s"), *Code);
        return false;
    }

    // For other codes (ENTRY, EXIT, SP, etc.), delegate to GetSpotPosition
    return GetSpotPosition(Code, OutPosition);
}

bool AWarehouseEnvironment::RootPointCodesToPositions(const TArray<FString>& Codes, TArray<FVector>& OutPositions) const
{
    OutPositions.Empty();
    OutPositions.Reserve(Codes.Num());

    for (const FString& Code : Codes)
    {
        FVector Pos;
        if (!RootPointCodeToPosition(Code, Pos))
        {
            UE_LOG(LogTemp, Warning, TEXT("[Warehouse] Failed to convert waypoint code: %s"), *Code);
            OutPositions.Empty();
            return false;
        }
        OutPositions.Add(Pos);
    }

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Converted %d root point codes to positions"), OutPositions.Num());
    return true;
}

FString AWarehouseEnvironment::FindNearestRootPointCode(const FVector& WorldPosition) const
{
    if (!bUsingDynamicLayout || CurrentLayout.Rows == 0 || CurrentLayout.Cols == 0)
    {
        return TEXT("");
    }

    float CellSz = GetCellSize();

    // Reverse CellToWorldPosition (with Y mirroring):
    //   X = Row*CellSz + CellSz/2  → Row = round(X/CellSz - 0.5)
    //   Y = (Cols-1-Col)*CellSz + CellSz/2 → Col = Cols-1 - round(Y/CellSz - 0.5)
    FVector LocalPos = WorldPosition - GetActorLocation();
    int32 Row = FMath::RoundToInt(LocalPos.X / CellSz - 0.5f);
    int32 Col = CurrentLayout.Cols - 1 - FMath::RoundToInt(LocalPos.Y / CellSz - 0.5f);

    // Clamp to valid bounds
    Row = FMath::Clamp(Row, 0, CurrentLayout.Rows - 1);
    Col = FMath::Clamp(Col, 0, CurrentLayout.Cols - 1);

    FString Code = FString::Printf(TEXT("RP-R%02d-C%02d"), Row, Col);
    UE_LOG(LogTemp, Log, TEXT("[Warehouse] FindNearestRootPoint: WorldPos=%s → Row=%d Col=%d → %s"),
        *WorldPosition.ToString(), Row, Col, *Code);
    return Code;
}
