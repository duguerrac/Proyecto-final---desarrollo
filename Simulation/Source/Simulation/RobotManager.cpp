// Copyright Epic Games, Inc. All Rights Reserved.

#include "RobotManager.h"
#include "WarehouseRobot.h"
#include "Engine/World.h"
#include "Kismet/GameplayStatics.h"

ARobotManager::ARobotManager()
{
    PrimaryActorTick.bCanEverTick = false;
    NatsClient = nullptr;
}

void ARobotManager::BeginPlay()
{
    Super::BeginPlay();

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Starting SmartLogistics simulation..."));
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] NATS URL: %s | MaxRobots: %d"), *NatsUrl, MaxRobots);

    // Auto-connect to NATS
    ConnectToNats();
}

void ARobotManager::EndPlay(const EEndPlayReason::Type EndPlayReason)
{
    DisconnectFromNats();
    Super::EndPlay(EndPlayReason);
}

// ─── Connection Management ──────────────────────────────────────────

void ARobotManager::ConnectToNats()
{
    if (NatsClient && NatsClient->IsConnected())
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Already connected to NATS"));
        return;
    }

    // Create the NATS client as a UObject
    NatsClient = NewObject<UNatsWebSocketClient>(this, TEXT("NatsClient"));
    if (NatsClient)
    {
        // Bind the event delegate
        NatsClient->OnRobotStatusReceived.AddDynamic(this, &ARobotManager::HandleRobotStatusEvent);

        // Connect
        NatsClient->Connect(NatsUrl);
        bIsNatsConnected = true;
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] NATS client created and connecting..."));
    }
    else
    {
        UE_LOG(LogTemp, Error, TEXT("[RobotManager] Failed to create NATS client!"));
    }
}

void ARobotManager::DisconnectFromNats()
{
    if (NatsClient)
    {
        NatsClient->Disconnect();
        NatsClient = nullptr;
    }
    bIsNatsConnected = false;
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Disconnected from NATS"));
}

// ─── Event Handling ─────────────────────────────────────────────────

void ARobotManager::HandleRobotStatusEvent(const FSmartLogisticRobotData& RobotData)
{
    TotalEventsReceived++;

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Event #%d → Robot: %s"),
           TotalEventsReceived, *RobotData.RobotId);

    // Find or create the visual robot actor
    AWarehouseRobot* RobotActor = FindOrCreateRobot(RobotData);
    if (RobotActor)
    {
        // Update the visual representation
        RobotActor->UpdateFromData(RobotData);
    }

    // Re-broadcast for HUD / other listeners
    OnRobotUpdated.Broadcast(RobotData);
}

AWarehouseRobot* ARobotManager::FindOrCreateRobot(const FSmartLogisticRobotData& Data)
{
    // Check if we already have this robot
    if (AWarehouseRobot** Existing = RobotActors.Find(Data.RobotId))
    {
        return *Existing;
    }

    // Check capacity
    if (RobotActors.Num() >= MaxRobots)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Max robots (%d) reached. Ignoring %s"),
               MaxRobots, *Data.RobotId);
        return nullptr;
    }

    // Spawn a new robot actor
    UWorld* World = GetWorld();
    if (!World) return nullptr;

    int32 SlotIndex = RobotCounter++;
    FVector SpawnPos = GetSlotPosition(SlotIndex);
    FRotator SpawnRot = FRotator::ZeroRotator;

    FActorSpawnParameters SpawnParams;
    SpawnParams.Name = *FString::Printf(TEXT("Robot_%s"), *Data.RobotId);
    SpawnParams.Owner = this;

    AWarehouseRobot* NewRobot = World->SpawnActor<AWarehouseRobot>(
        AWarehouseRobot::StaticClass(), SpawnPos, SpawnRot, SpawnParams);

    if (NewRobot)
    {
        NewRobot->RobotId = Data.RobotId;
        RobotActors.Add(Data.RobotId, NewRobot);
        ActiveRobotCount = RobotActors.Num();

        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Spawned robot '%s' at slot %d (%s)"),
               *Data.RobotId, SlotIndex, *SpawnPos.ToString());
    }
    else
    {
        UE_LOG(LogTemp, Error, TEXT("[RobotManager] Failed to spawn robot actor!"));
    }

    return NewRobot;
}

FVector ARobotManager::GetSlotPosition(int32 SlotIndex) const
{
    // Arrange robots in a grid relative to this actor's world position
    int32 Cols = 5;  // 5 robots per row
    int32 Row = SlotIndex / Cols;
    int32 Col = SlotIndex % Cols;

    return GetActorLocation() + SpawnOrigin + FVector(
        Row * RobotSpacing,
        (Col - Cols / 2) * RobotSpacing,  // centered on Y axis
        0.0f
    );
}

void ARobotManager::GetAllRobotData(TArray<FSmartLogisticRobotData>& OutData) const
{
    OutData.Empty();
    for (const auto& Pair : RobotActors)
    {
        if (Pair.Value)
        {
            OutData.Add(Pair.Value->GetCurrentData());
        }
    }
}