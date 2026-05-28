// Copyright Epic Games, Inc. All Rights Reserved.

#include "WarehouseEnvironment.h"
#include "Components/StaticMeshComponent.h"
#include "Components/TextRenderComponent.h"
#include "UObject/ConstructorHelpers.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "UObject/SoftObjectPath.h"

AWarehouseEnvironment::AWarehouseEnvironment()
{
    PrimaryActorTick.bCanEverTick = false;
}

void AWarehouseEnvironment::OnConstruction(const FTransform& Transform)
{
    Super::OnConstruction(Transform);

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Building procedural environment..."));

    ClearProceduralComponents();

    BuildFloor();
    BuildWalls();
    BuildShelves();
    BuildChargingStations();
    BuildDeliveryArea();
    BuildReceivingArea();
    BuildPickupZone();
    BuildLocationMap();

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Environment built: %d components, %d locations"), ProceduralComponents.Num(), Locations.Num());
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
            FString ShelfName = FString::Printf(TEXT("SHELF-%s%d"), *RowLetter, Col + 1);
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
