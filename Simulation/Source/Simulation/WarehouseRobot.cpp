// Copyright Epic Games, Inc. All Rights Reserved.

#include "WarehouseRobot.h"
#include "UObject/ConstructorHelpers.h"
#include "Materials/MaterialInstanceDynamic.h"

AWarehouseRobot::AWarehouseRobot()
{
    PrimaryActorTick.bCanEverTick = false;

    // ─── Root / Body Mesh ────────────────────────────────────────
    BodyMesh = CreateDefaultSubobject<UStaticMeshComponent>(TEXT("BodyMesh"));
    RootComponent = BodyMesh;

    // Use the engine default cube
    static ConstructorHelpers::FObjectFinder<UStaticMesh> CubeMesh(
        TEXT("/Engine/BasicShapes/Cube.Cube"));
    if (CubeMesh.Succeeded())
    {
        BodyMesh->SetStaticMesh(CubeMesh.Object);
    }

    // Try custom material first, fallback to engine material.
    // TO ENABLE COLORS: Create M_SolidColor in Content Browser:
    //   Content → New Folder "Materials" → Add → Material → "M_SolidColor"
    //   Open → Right-click graph → Convert to Parameter → Name: "Color"
    //   Connect RGB → Emissive Color → Shading Model: Unlit → Apply & Save
    static ConstructorHelpers::FObjectFinder<UMaterialInterface> CustomMat(
        TEXT("/Game/Materials/M_SolidColor.M_SolidColor"));
    if (CustomMat.Succeeded())
    {
        BodyMesh->SetMaterial(0, CustomMat.Object);
        UE_LOG(LogTemp, Log, TEXT("[Robot] Using M_SolidColor material (colors enabled)"));
    }
    else
    {
        // Fallback: engine basic shape material (gray, but at least visible)
        static ConstructorHelpers::FObjectFinder<UMaterialInterface> FallbackMat(
            TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial"));
        if (FallbackMat.Succeeded())
        {
            BodyMesh->SetMaterial(0, FallbackMat.Object);
        }
        UE_LOG(LogTemp, Warning, TEXT("[Robot] M_SolidColor not found — using fallback (no colors)"));
    }

    // Make sure it's visible
    BodyMesh->SetVisibility(true);
    BodyMesh->SetHiddenInGame(false);
    BodyMesh->SetCollisionEnabled(ECollisionEnabled::QueryAndPhysics);

    // Scale: UE units = cm. Scale 2 = 200cm cube (2 meters) — easy to see!
    BodyMesh->SetWorldScale3D(FVector(2.0f, 1.5f, 1.0f));

    // ─── Status Text (floating above) ────────────────────────────
    StatusText = CreateDefaultSubobject<UTextRenderComponent>(TEXT("StatusText"));
    StatusText->SetupAttachment(RootComponent);
    StatusText->SetRelativeLocation(FVector(0.0f, 0.0f, 120.0f));
    StatusText->SetRelativeRotation(FRotator(90.0f, 0.0f, 180.0f));
    StatusText->SetText(FText::FromString(TEXT("Robot")));
    StatusText->SetTextRenderColor(FColor::White);
    StatusText->SetXScale(1.0f);
    StatusText->SetYScale(1.0f);
    StatusText->SetWorldSize(24.0f);
    StatusText->SetHorizontalAlignment(EHTA_Center);
    StatusText->SetVerticalAlignment(EVRTA_TextBottom);
    StatusText->SetVisibility(true);
    StatusText->SetHiddenInGame(false);

    // ─── Battery Text ────────────────────────────────────────────
    BatteryText = CreateDefaultSubobject<UTextRenderComponent>(TEXT("BatteryText"));
    BatteryText->SetupAttachment(RootComponent);
    BatteryText->SetRelativeLocation(FVector(0.0f, 0.0f, 90.0f));
    BatteryText->SetRelativeRotation(FRotator(90.0f, 0.0f, 180.0f));
    BatteryText->SetText(FText::FromString(TEXT("100%")));
    BatteryText->SetTextRenderColor(FColor::Green);
    BatteryText->SetXScale(1.0f);
    BatteryText->SetYScale(1.0f);
    BatteryText->SetWorldSize(18.0f);
    BatteryText->SetHorizontalAlignment(EHTA_Center);
    BatteryText->SetVerticalAlignment(EVRTA_TextBottom);
    BatteryText->SetVisibility(true);
    BatteryText->SetHiddenInGame(false);

    DynMaterial = nullptr;
}

void AWarehouseRobot::BeginPlay()
{
    Super::BeginPlay();

    // Create dynamic material instance from whatever material is set
    if (BodyMesh)
    {
        DynMaterial = BodyMesh->CreateAndSetMaterialInstanceDynamic(0);

        if (DynMaterial)
        {
            // Set initial IDLE color (blue)
            FLinearColor InitialColor(0.2f, 0.6f, 1.0f, 1.0f);
            DynMaterial->SetVectorParameterValue(TEXT("Color"), InitialColor);

            UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Dynamic material OK at %s"),
                *GetName(), *GetActorLocation().ToString());
        }
        else
        {
            UE_LOG(LogTemp, Error, TEXT("[Robot:%s] NO dynamic material!"),
                *GetName());
        }
    }
}

void AWarehouseRobot::UpdateFromData(const FSmartLogisticRobotData& Data)
{
    CurrentData = Data;

    // ─── Update Status Text ──────────────────────────────────────
    FString StatusStr = FString::Printf(TEXT("%s [%s]"),
        *Data.RobotName,
        *UEnum::GetValueAsString(Data.OperationalMode).RightChop(1));
    StatusText->SetText(FText::FromString(StatusStr));

    // ─── Update Battery Text & Color ─────────────────────────────
    FString BatStr = FString::Printf(TEXT("%d%%"), Data.BatteryLevel);
    BatteryText->SetText(FText::FromString(BatStr));

    if (Data.BatteryLevel > 60)
        BatteryText->SetTextRenderColor(FColor::Green);
    else if (Data.BatteryLevel > 25)
        BatteryText->SetTextRenderColor(FColor::Yellow);
    else
        BatteryText->SetTextRenderColor(FColor::Red);

    // ─── Update Body Color via Dynamic Material ──────────────────
    if (DynMaterial)
    {
        FLinearColor StatusColor = GetStatusColor();
        DynMaterial->SetVectorParameterValue(TEXT("Color"), StatusColor);
    }

    UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Updated → %s | Bat:%d%% | Loc:%s"),
           *Data.RobotId,
           *UEnum::GetValueAsString(Data.OperationalMode),
           Data.BatteryLevel,
           *Data.CurrentLocation);
}

FLinearColor AWarehouseRobot::GetStatusColor() const
{
    switch (CurrentData.OperationalMode)
    {
    case ERobotOperationalMode::IDLE:      return FLinearColor(0.2f, 0.6f, 1.0f);  // Blue
    case ERobotOperationalMode::MOVING:    return FLinearColor(0.2f, 0.9f, 0.2f);  // Green
    case ERobotOperationalMode::PICKING:   return FLinearColor(1.0f, 0.8f, 0.0f);  // Yellow
    case ERobotOperationalMode::CHARGING:  return FLinearColor(0.0f, 0.8f, 0.8f);  // Cyan
    case ERobotOperationalMode::OFFLINE:   return FLinearColor(0.5f, 0.0f, 0.0f);  // Dark Red
    default:                               return FLinearColor::White;
    }
}