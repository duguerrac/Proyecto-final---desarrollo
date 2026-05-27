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

    UE_LOG(LogTemp, Log, TEXT("[Warehouse] Environment built: %d components"), ProceduralComponents.Num());
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