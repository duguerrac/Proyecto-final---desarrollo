// Copyright Epic Games, Inc. All Rights Reserved.

#include "WarehouseRobot.h"
#include "UObject/ConstructorHelpers.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Kismet/KismetMathLibrary.h"
#include "Engine/World.h"

AWarehouseRobot::AWarehouseRobot()
{
    PrimaryActorTick.bCanEverTick = true;
    PrimaryActorTick.TickInterval = 0.016f; // ~60 FPS

    // ─── Root / Body Mesh ────────────────────────────────────────
    BodyMesh = CreateDefaultSubobject<UStaticMeshComponent>(TEXT("BodyMesh"));
    RootComponent = BodyMesh;

    static ConstructorHelpers::FObjectFinder<UStaticMesh> CubeMesh(
        TEXT("/Engine/BasicShapes/Cube.Cube"));
    if (CubeMesh.Succeeded())
    {
        BodyMesh->SetStaticMesh(CubeMesh.Object);
    }

    static ConstructorHelpers::FObjectFinder<UMaterialInterface> CustomMat(
        TEXT("/Game/Materials/M_SolidColor.M_SolidColor"));
    if (CustomMat.Succeeded())
    {
        BodyMesh->SetMaterial(0, CustomMat.Object);
    }
    else
    {
        static ConstructorHelpers::FObjectFinder<UMaterialInterface> FallbackMat(
            TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial"));
        if (FallbackMat.Succeeded())
        {
            BodyMesh->SetMaterial(0, FallbackMat.Object);
        }
    }

    BodyMesh->SetVisibility(true);
    BodyMesh->SetHiddenInGame(false);
    BodyMesh->SetCollisionEnabled(ECollisionEnabled::QueryAndPhysics);
    BodyMesh->SetWorldScale3D(FVector(1.5f, 1.0f, 0.6f));

    // ─── Battery Bar Background (red, behind fill) ──────────────
    BatteryBarBg = CreateDefaultSubobject<UStaticMeshComponent>(TEXT("BatteryBarBg"));
    BatteryBarBg->SetupAttachment(RootComponent);
    BatteryBarBg->SetRelativeLocation(FVector(0.0f, 0.0f, 80.0f));
    BatteryBarBg->SetWorldScale3D(FVector(BatteryBarMaxWidth, 0.15f, 0.08f));
    if (CubeMesh.Succeeded()) BatteryBarBg->SetStaticMesh(CubeMesh.Object);

    // ─── Battery Bar Fill (green, on top of bg) ─────────────────
    BatteryBarFill = CreateDefaultSubobject<UStaticMeshComponent>(TEXT("BatteryBarFill"));
    BatteryBarFill->SetupAttachment(RootComponent);
    BatteryBarFill->SetRelativeLocation(FVector(0.0f, 0.0f, 80.0f));
    BatteryBarFill->SetWorldScale3D(FVector(BatteryBarMaxWidth, 0.16f, 0.09f));
    if (CubeMesh.Succeeded()) BatteryBarFill->SetStaticMesh(CubeMesh.Object);

    // ─── Cargo Indicator (small cube on top of body) ────────────
    CargoIndicator = CreateDefaultSubobject<UStaticMeshComponent>(TEXT("CargoIndicator"));
    CargoIndicator->SetupAttachment(RootComponent);
    CargoIndicator->SetRelativeLocation(FVector(0.0f, 0.0f, 50.0f));
    CargoIndicator->SetWorldScale3D(FVector(0.4f, 0.4f, 0.4f));
    CargoIndicator->SetVisibility(false);
    if (CubeMesh.Succeeded()) CargoIndicator->SetStaticMesh(CubeMesh.Object);

    // ─── Status Text ────────────────────────────────────────────
    StatusText = CreateDefaultSubobject<UTextRenderComponent>(TEXT("StatusText"));
    StatusText->SetupAttachment(RootComponent);
    StatusText->SetRelativeLocation(FVector(0.0f, 0.0f, 120.0f));
    StatusText->SetRelativeRotation(FRotator(90.0f, 0.0f, 180.0f));
    StatusText->SetText(FText::FromString(TEXT("Robot")));
    StatusText->SetTextRenderColor(FColor::White);
    StatusText->SetWorldSize(20.0f);
    StatusText->SetHorizontalAlignment(EHTA_Center);
    StatusText->SetVerticalAlignment(EVRTA_TextBottom);
    StatusText->SetVisibility(true);
    StatusText->SetHiddenInGame(false);

    // ─── Battery Text ───────────────────────────────────────────
    BatteryText = CreateDefaultSubobject<UTextRenderComponent>(TEXT("BatteryText"));
    BatteryText->SetupAttachment(RootComponent);
    BatteryText->SetRelativeLocation(FVector(0.0f, 0.0f, 95.0f));
    BatteryText->SetRelativeRotation(FRotator(90.0f, 0.0f, 180.0f));
    BatteryText->SetText(FText::FromString(TEXT("100%")));
    BatteryText->SetTextRenderColor(FColor::Green);
    BatteryText->SetWorldSize(16.0f);
    BatteryText->SetHorizontalAlignment(EHTA_Center);
    BatteryText->SetVerticalAlignment(EVRTA_TextBottom);
    BatteryText->SetVisibility(true);
    BatteryText->SetHiddenInGame(false);

    DynMaterial = nullptr;
    BatteryBarMaterial = nullptr;
}

void AWarehouseRobot::BeginPlay()
{
    Super::BeginPlay();

    PreviousPosition = GetActorLocation();
    InternalBattery = 100.0f;

    // Create dynamic materials
    if (BodyMesh)
    {
        DynMaterial = BodyMesh->CreateAndSetMaterialInstanceDynamic(0);
        if (DynMaterial)
        {
            DynMaterial->SetVectorParameterValue(TEXT("Color"), FLinearColor(0.2f, 0.6f, 1.0f));
        }
    }

    // Battery bar background = dark red
    if (BatteryBarBg)
    {
        UMaterialInterface* SolidMat = LoadObject<UMaterialInterface>(nullptr,
            TEXT("/Game/Materials/M_SolidColor.M_SolidColor"));
        if (SolidMat)
        {
            UMaterialInstanceDynamic* BgMat = UMaterialInstanceDynamic::Create(SolidMat, this);
            BgMat->SetVectorParameterValue(TEXT("Color"), FLinearColor(0.3f, 0.0f, 0.0f));
            BatteryBarBg->SetMaterial(0, BgMat);
        }
    }

    // Battery bar fill = dynamic green
    if (BatteryBarFill)
    {
        UMaterialInterface* SolidMat = LoadObject<UMaterialInterface>(nullptr,
            TEXT("/Game/Materials/M_SolidColor.M_SolidColor"));
        if (SolidMat)
        {
            BatteryBarMaterial = UMaterialInstanceDynamic::Create(SolidMat, this);
            BatteryBarMaterial->SetVectorParameterValue(TEXT("Color"), FLinearColor(0.0f, 0.9f, 0.0f));
            BatteryBarFill->SetMaterial(0, BatteryBarMaterial);
        }
    }

    // Cargo indicator = orange
    if (CargoIndicator)
    {
        UMaterialInterface* SolidMat = LoadObject<UMaterialInterface>(nullptr,
            TEXT("/Game/Materials/M_SolidColor.M_SolidColor"));
        if (SolidMat)
        {
            UMaterialInstanceDynamic* CargoMat = UMaterialInstanceDynamic::Create(SolidMat, this);
            CargoMat->SetVectorParameterValue(TEXT("Color"), FLinearColor(1.0f, 0.6f, 0.0f));
            CargoIndicator->SetMaterial(0, CargoMat);
        }
    }

    // Initialize data
    CurrentData.BatteryLevel = 100;
    CurrentData.OperationalMode = ERobotOperationalMode::IDLE;
    CurrentData.MaxCapacity = MaxCargoCapacity;
    CurrentData.CarriedItems = 0;
    CurrentData.WorldPosition = GetActorLocation();
    CurrentData.WorldRotation = GetActorRotation();

    UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Initialized at %s"), *GetName(), *GetActorLocation().ToString());
}

void AWarehouseRobot::Tick(float DeltaTime)
{
    Super::Tick(DeltaTime);

    // ─── Movement ───────────────────────────────────────────────
    if (bIsMoving)
    {
        FVector CurrentPos = GetActorLocation();
        FVector Direction = MoveTarget - CurrentPos;
        float DistanceToTarget = Direction.Size();

        if (DistanceToTarget < 10.0f)
        {
            SetActorLocation(MoveTarget);
            bIsMoving = false;
            CurrentData.Speed = 0.0f;
            UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Arrived at %s"), *RobotId, *MoveTarget.ToString());

            if (bIsCharging)
            {
                CurrentData.OperationalMode = ERobotOperationalMode::CHARGING;
                UpdateTextDisplays();
            }
        }
        else
        {
            Direction.Normalize();
            FVector NewPos = CurrentPos + Direction * FMath::Min(MovementSpeed * DeltaTime, DistanceToTarget);
            SetActorLocation(NewPos);

            FRotator TargetRot = FRotationMatrix::MakeFromX(Direction).Rotator();
            SetActorRotation(FMath::RInterpTo(GetActorRotation(), TargetRot, DeltaTime, 5.0f));

            float DeltaDist = (NewPos - PreviousPosition).Size();
            SimulateBatteryDrain(DeltaDist);
            CurrentData.DistanceTraveled += DeltaDist;
            CurrentData.Speed = DeltaDist / DeltaTime;
        }
    }
    else
    {
        CurrentData.Speed = 0.0f;
    }

    // ─── Charging ───────────────────────────────────────────────
    if (bIsCharging && !bIsMoving)
    {
        SimulateCharge(DeltaTime);
    }

    // ─── Update state ───────────────────────────────────────────
    PreviousPosition = GetActorLocation();
    CurrentData.WorldPosition = GetActorLocation();
    CurrentData.WorldRotation = GetActorRotation();
    CurrentData.BatteryLevel = FMath::RoundToInt(InternalBattery);
    CurrentData.MaxCapacity = MaxCargoCapacity;

    UpdateOperationalMode();
    UpdateBatteryBar();
    UpdateCargoIndicator();
    UpdateTextDisplays();
}

// ═══════════════════════════════════════════════════════════════════
//  COMMANDS
// ═══════════════════════════════════════════════════════════════════

void AWarehouseRobot::MoveTo(const FVector& TargetLocation)
{
    if (InternalBattery <= 0.0f)
    {
        UE_LOG(LogTemp, Warning, TEXT("[Robot:%s] Cannot move - battery dead!"), *RobotId);
        return;
    }

    MoveTarget = TargetLocation;
    bIsMoving = true;
    bIsCharging = false;
    CurrentData.OperationalMode = ERobotOperationalMode::MOVING;
    CurrentData.CurrentLocation = FString::Printf(TEXT("(%.0f,%.0f,%.0f)"),
        TargetLocation.X, TargetLocation.Y, TargetLocation.Z);

    UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Moving to %s (Bat: %.1f%%)"),
        *RobotId, *TargetLocation.ToString(), InternalBattery);
}

void AWarehouseRobot::PickUpItem()
{
    if (CurrentData.CarriedItems >= MaxCargoCapacity)
    {
        UE_LOG(LogTemp, Warning, TEXT("[Robot:%s] Cargo full (%d/%d)"),
            *RobotId, CurrentData.CarriedItems, MaxCargoCapacity);
        return;
    }

    CurrentData.CarriedItems++;
    CurrentData.OperationalMode = ERobotOperationalMode::PICKING;

    UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Picked up item (%d/%d)"),
        *RobotId, CurrentData.CarriedItems, MaxCargoCapacity);
}

void AWarehouseRobot::DropOffItems()
{
    int32 Dropped = CurrentData.CarriedItems;
    CurrentData.CarriedItems = 0;
    UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Dropped off %d items"), *RobotId, Dropped);
}

void AWarehouseRobot::GoCharge(const FVector& StationLocation)
{
    ChargeStationLocation = StationLocation;
    bIsCharging = true;
    MoveTo(StationLocation);

    UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Going to charge at %s (Bat: %.1f%%)"),
        *RobotId, *StationLocation.ToString(), InternalBattery);
}

// ═══════════════════════════════════════════════════════════════════
//  BATTERY SIMULATION
// ═══════════════════════════════════════════════════════════════════

void AWarehouseRobot::SimulateBatteryDrain(float DeltaDistance)
{
    float Meters = DeltaDistance / 100.0f;
    float Drain = Meters * BatteryDrainPerMeter;

    // Extra drain when carrying items
    float LoadFactor = 1.0f + (CurrentData.CarriedItems * 0.05f);
    Drain *= LoadFactor;

    InternalBattery = FMath::Clamp(InternalBattery - Drain, 0.0f, 100.0f);

    if (InternalBattery <= LowBatteryThreshold && !bIsCharging)
    {
        UE_LOG(LogTemp, Warning, TEXT("[Robot:%s] LOW BATTERY (%.1f%%) - seeking charger"),
            *RobotId, InternalBattery);
    }

    if (InternalBattery <= 0.0f)
    {
        bIsMoving = false;
        CurrentData.OperationalMode = ERobotOperationalMode::OFFLINE;
        CurrentData.bAvailable = false;
        UE_LOG(LogTemp, Error, TEXT("[Robot:%s] BATTERY DEAD - offline"), *RobotId);
    }
}

void AWarehouseRobot::SimulateCharge(float DeltaTime)
{
    InternalBattery = FMath::Clamp(InternalBattery + BatteryChargeRate * DeltaTime, 0.0f, 100.0f);

    if (InternalBattery >= 100.0f)
    {
        bIsCharging = false;
        CurrentData.bAvailable = true;
        CurrentData.OperationalMode = ERobotOperationalMode::IDLE;
        UE_LOG(LogTemp, Log, TEXT("[Robot:%s] Fully charged!"), *RobotId);
    }
}

// ═══════════════════════════════════════════════════════════════════
//  VISUAL UPDATES
// ═══════════════════════════════════════════════════════════════════

void AWarehouseRobot::UpdateBatteryBar()
{
    if (!BatteryBarFill) return;

    float Ratio = FMath::Clamp(InternalBattery / 100.0f, 0.05f, 1.0f);
    float Width = BatteryBarMaxWidth * Ratio;
    float OffsetX = (Width - BatteryBarMaxWidth) / 2.0f;

    BatteryBarFill->SetRelativeLocation(FVector(OffsetX, 0.0f, 80.0f));
    BatteryBarFill->SetWorldScale3D(FVector(Width, 0.16f, 0.09f));

    if (BatteryBarMaterial)
    {
        FLinearColor BarColor;
        if (InternalBattery > 60.0f)
            BarColor = FLinearColor(0.0f, 0.85f, 0.0f);
        else if (InternalBattery > 30.0f)
        {
            float T = (InternalBattery - 30.0f) / 30.0f;
            BarColor = FLinearColor(FMath::Lerp(1.0f, 0.0f, T),
                                     FMath::Lerp(0.5f, 0.85f, T), 0.0f);
        }
        else
        {
            float T = InternalBattery / 30.0f;
            BarColor = FLinearColor(0.85f, FMath::Lerp(0.0f, 0.5f, T), 0.0f);
        }
        BatteryBarMaterial->SetVectorParameterValue(TEXT("Color"), BarColor);
    }
}

void AWarehouseRobot::UpdateCargoIndicator()
{
    if (!CargoIndicator) return;

    if (CurrentData.CarriedItems > 0)
    {
        CargoIndicator->SetVisibility(true);
        float Scale = 0.3f + (CurrentData.CarriedItems * 0.1f);
        CargoIndicator->SetWorldScale3D(FVector(Scale, Scale, Scale));
    }
    else
    {
        CargoIndicator->SetVisibility(false);
    }
}

void AWarehouseRobot::UpdateTextDisplays()
{
    FString ModeStr;
    switch (CurrentData.OperationalMode)
    {
    case ERobotOperationalMode::IDLE:     ModeStr = TEXT("IDLE"); break;
    case ERobotOperationalMode::MOVING:   ModeStr = TEXT("MOVING"); break;
    case ERobotOperationalMode::PICKING:  ModeStr = TEXT("PICKING"); break;
    case ERobotOperationalMode::CHARGING: ModeStr = TEXT("CHARGING"); break;
    case ERobotOperationalMode::OFFLINE:  ModeStr = TEXT("OFFLINE"); break;
    default:                              ModeStr = TEXT("???"); break;
    }

    FString StatusStr = FString::Printf(TEXT("%s [%s] Items:%d"),
        *CurrentData.RobotName, *ModeStr, CurrentData.CarriedItems);
    if (StatusText) StatusText->SetText(FText::FromString(StatusStr));

    FString BatStr = FString::Printf(TEXT("%.0f%%"), InternalBattery);
    if (BatteryText)
    {
        BatteryText->SetText(FText::FromString(BatStr));
        if (InternalBattery > 60.0f)
            BatteryText->SetTextRenderColor(FColor::Green);
        else if (InternalBattery > 25.0f)
            BatteryText->SetTextRenderColor(FColor::Yellow);
        else
            BatteryText->SetTextRenderColor(FColor::Red);
    }

    if (DynMaterial)
        DynMaterial->SetVectorParameterValue(TEXT("Color"), GetStatusColor());
}

void AWarehouseRobot::UpdateOperationalMode()
{
    if (InternalBattery <= 0.0f)
    {
        CurrentData.OperationalMode = ERobotOperationalMode::OFFLINE;
        CurrentData.bAvailable = false;
    }
    else if (bIsCharging && !bIsMoving)
        CurrentData.OperationalMode = ERobotOperationalMode::CHARGING;
    else if (bIsMoving)
        CurrentData.OperationalMode = ERobotOperationalMode::MOVING;
    else if (CurrentData.OperationalMode != ERobotOperationalMode::PICKING)
    {
        CurrentData.OperationalMode = ERobotOperationalMode::IDLE;
        CurrentData.bAvailable = true;
    }
}

// ═══════════════════════════════════════════════════════════════════
//  EXTERNAL UPDATE (from NATS events)
// ═══════════════════════════════════════════════════════════════════

void AWarehouseRobot::UpdateFromData(const FSmartLogisticRobotData& Data)
{
    CurrentData.RobotId = Data.RobotId;
    CurrentData.RobotName = Data.RobotName;
    CurrentData.EventTimestamp = Data.EventTimestamp;

    if (!bIsMoving && !bIsCharging)
    {
        CurrentData.BatteryLevel = Data.BatteryLevel;
        InternalBattery = Data.BatteryLevel;
    }

    UpdateTextDisplays();
}

FLinearColor AWarehouseRobot::GetStatusColor() const
{
    switch (CurrentData.OperationalMode)
    {
    case ERobotOperationalMode::IDLE:      return FLinearColor(0.2f, 0.6f, 1.0f);
    case ERobotOperationalMode::MOVING:    return FLinearColor(0.2f, 0.9f, 0.2f);
    case ERobotOperationalMode::PICKING:   return FLinearColor(1.0f, 0.8f, 0.0f);
    case ERobotOperationalMode::CHARGING:  return FLinearColor(0.0f, 0.8f, 0.8f);
    case ERobotOperationalMode::OFFLINE:   return FLinearColor(0.5f, 0.0f, 0.0f);
    default:                               return FLinearColor::White;
    }
}