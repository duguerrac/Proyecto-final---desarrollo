// Copyright Epic Games, Inc. All Rights Reserved.

#include "RobotManager.h"
#include "WarehouseRobot.h"
#include "WarehouseEnvironment.h"
#include "Engine/World.h"
#include "Kismet/GameplayStatics.h"
#include "Serialization/JsonSerializer.h"
#include "Dom/JsonObject.h"
#include "Serialization/JsonWriter.h"
#include "TimerManager.h"
#include "HttpModule.h"
#include "Interfaces/IHttpRequest.h"
#include "Interfaces/IHttpResponse.h"

ARobotManager::ARobotManager()
{
    PrimaryActorTick.bCanEverTick = true;
    PrimaryActorTick.TickInterval = 0.5f;
    NatsClient = nullptr;
}

void ARobotManager::BeginPlay()
{
    Super::BeginPlay();

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Starting SmartLogistics simulation..."));
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] NATS URL: %s | MaxRobots: %d"), *NatsUrl, MaxRobots);

    // Auto-find WarehouseEnvironment in the level if not manually set
    if (!WarehouseEnv)
    {
        TArray<AActor*> FoundActors;
        UGameplayStatics::GetAllActorsOfClass(GetWorld(), AWarehouseEnvironment::StaticClass(), FoundActors);
        if (FoundActors.Num() > 0)
        {
            WarehouseEnv = Cast<AWarehouseEnvironment>(FoundActors[0]);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Auto-found WarehouseEnvironment: %s"),
                WarehouseEnv ? *WarehouseEnv->GetName() : TEXT("NULL"));
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] No WarehouseEnvironment actor found in level!"));
        }
    }

    ConnectToNats();

    // Wait for WarehouseEnvironment to finish its own layout fetch (it does this in BeginPlay),
    // then extract charging stations and publish layout via NATS.
    FTimerHandle PostLayoutTimerHandle;
    GetWorldTimerManager().SetTimer(PostLayoutTimerHandle, [this]()
    {
        if (WarehouseEnv)
        {
            // Extract charging stations from whatever layout was built (dynamic or static)
            ChargingStations.Empty();
            TArray<FWarehouseLocation> ChargeLocations;
            WarehouseEnv->GetLocationsByType(TEXT("CHARGING"), ChargeLocations);
            for (const auto& Loc : ChargeLocations)
            {
                ChargingStations.Add(Loc.Position);
            }

            UE_LOG(LogTemp, Log, TEXT("[RobotManager] WarehouseEnv ready. Dynamic=%s, %d locations, %d charging stations"),
                WarehouseEnv->bUsingDynamicLayout ? TEXT("true") : TEXT("false"),
                WarehouseEnv->Locations.Num(),
                ChargingStations.Num());

            // Publish layout via NATS
            if (NatsClient && NatsClient->IsConnected())
            {
                FString LayoutJson = WarehouseEnv->GetLayoutJson();
                NatsClient->Publish(TEXT("smartlogistic.warehouse.layout"), LayoutJson);
                UE_LOG(LogTemp, Log, TEXT("[RobotManager] Published layout to NATS"));
            }
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Still no WarehouseEnv after delay!"));
        }
    }, 5.0f, false);
}

void ARobotManager::EndPlay(const EEndPlayReason::Type EndPlayReason)
{
    DisconnectFromNats();
    Super::EndPlay(EndPlayReason);
}

void ARobotManager::Tick(float DeltaTime)
{
    Super::Tick(DeltaTime);

    if (TelemetryInterval > 0.0f && NatsClient && NatsClient->IsConnected())
    {
        LastTelemetryTime += DeltaTime;
        if (LastTelemetryTime >= TelemetryInterval)
        {
            LastTelemetryTime = 0.0f;
            PublishTelemetry();
        }
    }

    bIsNatsConnected = NatsClient && NatsClient->IsConnected();
}

void ARobotManager::ConnectToNats()
{
    if (NatsClient && NatsClient->IsConnected())
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Already connected to NATS"));
        return;
    }

    NatsClient = NewObject<UNatsWebSocketClient>(this, TEXT("NatsClient"));
    if (NatsClient)
    {
        NatsClient->OnRobotStatusReceived.AddDynamic(this, &ARobotManager::HandleRobotStatusEvent);
        NatsClient->OnRobotCommandReceived.AddDynamic(this, &ARobotManager::HandleRobotCommand);
        NatsClient->OnPackageMissionReceived.AddDynamic(this, &ARobotManager::HandleMissionCommand);
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

void ARobotManager::HandleRobotStatusEvent(const FSmartLogisticRobotData& RobotData)
{
    TotalEventsReceived++;

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Event #%d -> Robot: %s"),
           TotalEventsReceived, *RobotData.RobotId);

    AWarehouseRobot* RobotActor = FindOrCreateRobot(RobotData);
    if (RobotActor)
    {
        RobotActor->UpdateFromData(RobotData);
    }

    OnRobotUpdated.Broadcast(RobotData);
}

void ARobotManager::HandleRobotCommand(const FString& RobotId, const FString& CommandType, const FString& TargetLocation)
{
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] NATS Command: %s -> %s (%s)"), *RobotId, *CommandType, *TargetLocation);
    SendRobotCommand(RobotId, CommandType, TargetLocation);
}

AWarehouseRobot* ARobotManager::FindOrCreateRobot(const FSmartLogisticRobotData& Data)
{
    if (AWarehouseRobot** Existing = RobotActors.Find(Data.RobotId))
    {
        return *Existing;
    }

    if (RobotActors.Num() >= MaxRobots)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Max robots (%d) reached. Ignoring %s"),
               MaxRobots, *Data.RobotId);
        return nullptr;
    }

    UWorld* World = GetWorld();
    if (!World) return nullptr;

    int32 SlotIndex = RobotCounter++;
    FVector SpawnPos;
    FRotator SpawnRot = FRotator::ZeroRotator;

    // Try to spawn the robot inside the warehouse at its current location
    if (WarehouseEnv)
    {
        bool bPositionFound = false;

        // 1. Try resolving the robot's currentLocation to a world position
        if (!Data.CurrentLocation.IsEmpty())
        {
            FVector ResolvedPos;
            if (WarehouseEnv->GetSpotPosition(Data.CurrentLocation, ResolvedPos))
            {
                SpawnPos = ResolvedPos;
                bPositionFound = true;
                UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' spawned at currentLocation '%s' -> %s"),
                    *Data.RobotId, *Data.CurrentLocation, *SpawnPos.ToString());
            }
        }

        // 2. Fall back to default warehouse spawn position
        if (!bPositionFound)
        {
            SpawnPos = WarehouseEnv->GetDefaultSpawnPosition();
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' spawned at default warehouse position: %s"),
                *Data.RobotId, *SpawnPos.ToString());
        }
    }
    else
    {
        // No warehouse environment — use grid slot positioning
        SpawnPos = GetSlotPosition(SlotIndex);
    }

    FActorSpawnParameters SpawnParams;
    SpawnParams.Name = *FString::Printf(TEXT("Robot_%s"), *Data.RobotId);
    SpawnParams.Owner = this;

    AWarehouseRobot* NewRobot = World->SpawnActor<AWarehouseRobot>(
        AWarehouseRobot::StaticClass(), SpawnPos, SpawnRot, SpawnParams);

    if (NewRobot)
    {
        NewRobot->RobotId = Data.RobotId;
        NewRobot->OnArrivalAtTarget.AddDynamic(this, &ARobotManager::HandleRobotArrival);
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
    int32 Cols = 5;
    int32 Row = SlotIndex / Cols;
    int32 Col = SlotIndex % Cols;

    return GetActorLocation() + SpawnOrigin + FVector(
        Row * RobotSpacing,
        (Col - Cols / 2) * RobotSpacing,
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

void ARobotManager::PublishTelemetry()
{
    if (!NatsClient || !NatsClient->IsConnected()) return;
    if (RobotActors.Num() == 0) return;

    FString JsonArray;
    JsonArray += TEXT("[");

    int32 Count = 0;
    for (const auto& Pair : RobotActors)
    {
        if (!Pair.Value) continue;
        const FSmartLogisticRobotData& Data = Pair.Value->GetCurrentData();

        FString ModeStr;
        switch (Data.OperationalMode)
        {
        case ERobotOperationalMode::IDLE:     ModeStr = TEXT("IDLE"); break;
        case ERobotOperationalMode::MOVING:   ModeStr = TEXT("MOVING"); break;
        case ERobotOperationalMode::PICKING:  ModeStr = TEXT("PICKING"); break;
        case ERobotOperationalMode::CHARGING: ModeStr = TEXT("CHARGING"); break;
        case ERobotOperationalMode::OFFLINE:  ModeStr = TEXT("OFFLINE"); break;
        default:                              ModeStr = TEXT("IDLE"); break;
        }

        if (Count > 0) JsonArray += TEXT(",");

        JsonArray += FString::Printf(
            TEXT("{\"id\":\"%s\",\"name\":\"%s\",\"batteryLevel\":%d,\"available\":%s,"
                 "\"currentLocation\":\"%s\",\"operationalMode\":\"%s\","
                 "\"position\":{\"x\":%.1f,\"y\":%.1f,\"z\":%.1f},"
                 "\"speed\":%.1f,\"distanceTraveled\":%.1f,"
                 "\"carriedItems\":%d,\"maxCapacity\":%d}"),
            *Data.RobotId,
            *Data.RobotName,
            Data.BatteryLevel,
            Data.bAvailable ? TEXT("true") : TEXT("false"),
            *Data.CurrentLocation,
            *ModeStr,
            Data.WorldPosition.X, Data.WorldPosition.Y, Data.WorldPosition.Z,
            Data.Speed,
            Data.DistanceTraveled,
            Data.CarriedItems,
            Data.MaxCapacity
        );

        // Also publish per-robot event for Java backend to consume
        FString PerRobotJson = FString::Printf(
            TEXT("{\"event\":\"SIMULATION_TELEMETRY\","
                 "\"timestamp\":\"%s\","
                 "\"source\":\"ue5-simulation\","
                 "\"robot\":{\"id\":\"%s\",\"name\":\"%s\","
                 "\"batteryLevel\":%d,\"available\":%s,"
                 "\"currentLocation\":\"%s\",\"operationalMode\":\"%s\","
                 "\"position\":{\"x\":%.1f,\"y\":%.1f,\"z\":%.1f},"
                 "\"speed\":%.1f,\"distanceTraveled\":%.1f,"
                 "\"carriedItems\":%d,\"maxCapacity\":%d}}"),
            *FDateTime::UtcNow().ToIso8601(),
            *Data.RobotId,
            *Data.RobotName,
            Data.BatteryLevel,
            Data.bAvailable ? TEXT("true") : TEXT("false"),
            *Data.CurrentLocation,
            *ModeStr,
            Data.WorldPosition.X, Data.WorldPosition.Y, Data.WorldPosition.Z,
            Data.Speed,
            Data.DistanceTraveled,
            Data.CarriedItems,
            Data.MaxCapacity
        );

        FString PerRobotSubject = FString::Printf(TEXT("smartlogistic.robot.telemetry.%s"), *Data.RobotId);
        NatsClient->Publish(PerRobotSubject, PerRobotJson);

        Count++;
    }

    JsonArray += TEXT("]");

    // Keep batch telemetry for any other consumers
    NatsClient->Publish(TEXT("smartlogistic.simulation.telemetry"), JsonArray);

    UE_LOG(LogTemp, Verbose, TEXT("[RobotManager] Published telemetry for %d robots (batch + per-robot)"), Count);
}

void ARobotManager::SendRobotCommand(const FString& TargetRobotId, const FString& CommandType, const FString& TargetLocation)
{
    AWarehouseRobot** Found = RobotActors.Find(TargetRobotId);
    if (!Found || !*Found)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Robot '%s' not found for command"), *TargetRobotId);
        return;
    }

    AWarehouseRobot* Robot = *Found;

    if (CommandType == TEXT("GO_TO"))
    {
        TArray<FString> Parts;
        TargetLocation.ParseIntoArray(Parts, TEXT(","), true);
        if (Parts.Num() >= 3)
        {
            FVector Target(FCString::Atof(*Parts[0]), FCString::Atof(*Parts[1]), FCString::Atof(*Parts[2]));
            Robot->MoveTo(Target);
        }
    }
    else if (CommandType == TEXT("PICK_UP"))
    {
        Robot->PickUpItem();
    }
    else if (CommandType == TEXT("DROP_OFF"))
    {
        Robot->DropOffItems();
    }
    else if (CommandType == TEXT("RETURN_DOCK"))
    {
        if (ChargingStations.Num() > 0)
        {
            FVector RobotPos = Robot->GetActorLocation();
            FVector Nearest = ChargingStations[0];
            float MinDist = FVector::Dist(RobotPos, Nearest);

            for (const FVector& Station : ChargingStations)
            {
                float Dist = FVector::Dist(RobotPos, Station);
                if (Dist < MinDist)
                {
                    MinDist = Dist;
                    Nearest = Station;
                }
            }
            Robot->GoCharge(Nearest);
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] No charging stations configured!"));
        }
    }
    else
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Unknown command: %s"), *CommandType);
    }
}

// ─── Warehouse Layout Fetch ────────────────────────────────────────

void ARobotManager::FetchAndApplyWarehouseLayout()
{
    FString Url = WarehouseApiUrl + TEXT("/api/layouts/active");
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Fetching warehouse layout from: %s"), *Url);

    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> Request = FHttpModule::Get().CreateRequest();
    Request->SetURL(Url);
    Request->SetVerb(TEXT("GET"));
    Request->SetHeader(TEXT("Content-Type"), TEXT("application/json"));
    Request->SetTimeout(10.0f);

    Request->OnProcessRequestComplete().BindLambda([this](FHttpRequestPtr Req, FHttpResponsePtr Resp, bool bSuccess)
    {
        if (!bSuccess || !Resp.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Layout fetch failed (no response)"));
            return;
        }

        int32 Code = Resp->GetResponseCode();
        FString Body = Resp->GetContentAsString();

        if (Code != 200)
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Layout fetch returned HTTP %d: %s"), Code, *Body);
            return;
        }

        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Layout response received (%d bytes)"), Body.Len());
        ApplyWarehouseLayoutFromJson(Body);
    });

    Request->ProcessRequest();
}

void ARobotManager::ApplyWarehouseLayoutFromJson(const FString& JsonString)
{
    TSharedPtr<FJsonObject> RootObj;
    TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(JsonString);

    if (!FJsonSerializer::Deserialize(Reader, RootObj) || !RootObj.IsValid())
    {
        UE_LOG(LogTemp, Error, TEXT("[RobotManager] Failed to parse layout JSON"));
        return;
    }

    FWarehouseLayoutData LayoutData;

    // Parse top-level fields
    LayoutData.LayoutId = static_cast<int64>(RootObj->GetNumberField(TEXT("id")));
    LayoutData.LayoutName = RootObj->GetStringField(TEXT("name"));
    LayoutData.Rows = RootObj->GetIntegerField(TEXT("rows"));
    LayoutData.Cols = RootObj->GetIntegerField(TEXT("cols"));

    // cellSize is optional, default 200
    if (RootObj->HasField(TEXT("cellSize")))
    {
        LayoutData.CellSize = static_cast<float>(RootObj->GetNumberField(TEXT("cellSize")));
    }
    else
    {
        LayoutData.CellSize = 200.0f;
    }

    if (RootObj->HasField(TEXT("status")))
    {
        LayoutData.Status = RootObj->GetStringField(TEXT("status"));
    }

    // Parse cells array
    const TArray<TSharedPtr<FJsonValue>>* CellsArray;
    if (RootObj->TryGetArrayField(TEXT("cells"), CellsArray))
    {
        for (const TSharedPtr<FJsonValue>& CellValue : *CellsArray)
        {
            TSharedPtr<FJsonObject> CellObj = CellValue->AsObject();
            if (!CellObj.IsValid()) continue;

            FLayoutCell Cell;
            Cell.RowIndex = CellObj->GetIntegerField(TEXT("rowIndex"));
            Cell.ColIndex = CellObj->GetIntegerField(TEXT("colIndex"));
            Cell.CellType = CellObj->GetStringField(TEXT("cellType"));

            LayoutData.Cells.Add(Cell);
        }
    }

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Parsed layout: '%s' (%dx%d, cellSize=%.1f, %d cells, status=%s)"),
        *LayoutData.LayoutName, LayoutData.Rows, LayoutData.Cols, LayoutData.CellSize,
        LayoutData.Cells.Num(), *LayoutData.Status);

    // Apply to warehouse environment
    if (WarehouseEnv)
    {
        WarehouseEnv->BuildFromLayout(LayoutData);

        // Update charging stations from layout locations
        ChargingStations.Empty();
        TArray<FWarehouseLocation> ChargeLocations;
        WarehouseEnv->GetLocationsByType(TEXT("CHARGING"), ChargeLocations);
        for (const auto& Loc : ChargeLocations)
        {
            ChargingStations.Add(Loc.Position);
        }

        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Layout applied! %d charging stations extracted"), ChargingStations.Num());

        // Publish updated layout via NATS
        if (NatsClient && NatsClient->IsConnected())
        {
            FString LayoutJson = WarehouseEnv->GetLayoutJson();
            NatsClient->Publish(TEXT("smartlogistic.warehouse.layout"), LayoutJson);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Published dynamic layout to NATS"));
        }
    }
    else
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] No WarehouseEnv reference - layout parsed but not applied"));
    }
}

// ─── Mission Handling ────────────────────────────────────────────

void ARobotManager::HandleMissionCommand(const FString& InRobotId, int64 InPackageId, const FString& InMissionType,
    const FString& InReceptionSpotCode, const FString& InTargetSpotCode, const FString& InItemSku, int32 InQuantity)
{
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Mission command: robot=%s, type=%s, package=%lld, spot=%s, item=%s, qty=%d"),
        *InRobotId, *InMissionType, InPackageId, *InTargetSpotCode, *InItemSku, InQuantity);

    // Find the robot
    AWarehouseRobot** Found = RobotActors.Find(InRobotId);
    if (!Found || !*Found)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Robot '%s' not found for mission"), *InRobotId);
        return;
    }

    AWarehouseRobot* Robot = *Found;

    // Look up spot positions from environment
    if (!WarehouseEnv)
    {
        UE_LOG(LogTemp, Error, TEXT("[RobotManager] No WarehouseEnv - cannot resolve spots"));
        return;
    }

    FVector ReceptionPos = FVector::ZeroVector;
    WarehouseEnv->GetSpotPosition(InReceptionSpotCode, ReceptionPos);

    FVector TargetPos = FVector::ZeroVector;
    if (!WarehouseEnv->GetSpotPosition(InTargetSpotCode, TargetPos))
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Target spot '%s' not found in environment"), *InTargetSpotCode);
    }

    // Build mission data from parameters
    FRobotMissionData Mission;
    Mission.PackageId = InPackageId;
    Mission.MissionType = InMissionType;
    Mission.ReceptionSpotCode = InReceptionSpotCode;
    Mission.TargetSpotCode = InTargetSpotCode;
    Mission.ReceptionSpotPosition = ReceptionPos;
    Mission.TargetSpotPosition = TargetPos;
    Mission.MissionPhase = TEXT("GO_TO_RECEPTION");

    // Set mission on robot
    Robot->SetMission(Mission);

    // For STOCK_IN: first go to reception to pick up, then to target
    if (InMissionType == TEXT("STOCK_IN"))
    {
        Robot->MoveTo(ReceptionPos);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' dispatched to RECEPTION '%s' at %s"),
            *InRobotId, *InReceptionSpotCode, *ReceptionPos.ToString());
    }
    else
    {
        Robot->MoveTo(TargetPos);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' dispatched to TARGET '%s' at %s"),
            *InRobotId, *InTargetSpotCode, *TargetPos.ToString());
    }
}

void ARobotManager::HandleRobotArrival(AWarehouseRobot* Robot, const FString& MissionType)
{
    if (!Robot) return;

    const FRobotMissionData& Mission = Robot->ActiveMission;

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' arrived for mission type=%s, package=%lld, spot=%s, phase=%s"),
        *Robot->RobotId, *MissionType, Mission.PackageId, *Mission.TargetSpotCode, *Mission.MissionPhase);

    // Handle multi-phase missions (STOCK_IN: go to reception first, then to target)
    if (MissionType == TEXT("STOCK_IN") && Mission.MissionPhase == TEXT("GO_TO_RECEPTION"))
    {
        // Arrived at reception - pick up item, then head to target storage spot
        Robot->PickUpItem();
        Robot->ActiveMission.MissionPhase = TEXT("GO_TO_TARGET");
        Robot->MoveTo(Mission.TargetSpotPosition);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' picked up at reception, heading to target '%s'"),
            *Robot->RobotId, *Mission.TargetSpotCode);
        return; // Don't clear mission yet
    }

    if (MissionType == TEXT("STOCK_IN") || MissionType == TEXT("STOCK_OUT"))
    {
        // Arrived at final destination - drop off items
        Robot->DropOffItems();
    }

    // Publish completion event
    PublishMissionEvent(TEXT("MISSION_COMPLETED"), Robot->RobotId,
        Mission.PackageId, Mission.TargetSpotCode, Mission.MissionType);

    // Clear the mission
    Robot->ClearMission();
}

void ARobotManager::PublishMissionEvent(const FString& EventType, const FString& RobotId,
    int64 PackageId, const FString& SpotCode, const FString& MissionType)
{
    if (!NatsClient || !NatsClient->IsConnected()) return;

    FString Json = FString::Printf(
        TEXT("{\"event\":\"%s\","
             "\"timestamp\":\"%s\","
             "\"source\":\"ue5-simulation\","
             "\"robotId\":\"%s\","
             "\"packageId\":%lld,"
             "\"spotCode\":\"%s\","
             "\"missionType\":\"%s\"}"),
        *EventType,
        *FDateTime::UtcNow().ToIso8601(),
        *RobotId,
        PackageId,
        *SpotCode,
        *MissionType
    );

    NatsClient->Publish(TEXT("smartlogistic.mission.completed"), Json);

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Published mission event: %s for robot=%s, package=%lld"),
        *EventType, *RobotId, PackageId);
}
