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

    // Fetch robots from backend API after a short delay (let warehouse env build first)
    FTimerHandle RobotFetchTimerHandle;
    GetWorldTimerManager().SetTimer(RobotFetchTimerHandle, [this]()
    {
        FetchRobotsFromBackend();
    }, 6.0f, false);

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
        NatsClient->OnPackageReceived.AddDynamic(this, &ARobotManager::HandlePackageReceived);
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

        // 2. Fall back to default warehouse spawn position + spread by slot index
        if (!bPositionFound)
        {
            SpawnPos = WarehouseEnv->GetDefaultSpawnPosition();
            // Offset each robot so they don't overlap
            float SpreadX = 200.0f * (SlotIndex % 5);
            float SpreadY = 200.0f * (SlotIndex / 5);
            SpawnPos += FVector(SpreadX, SpreadY, 0.0f);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' spawned at spread position (slot %d): %s"),
                *Data.RobotId, SlotIndex, *SpawnPos.ToString());
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

    // Use route planning API for navigation
    if (InMissionType == TEXT("STOCK_IN"))
    {
        // First leg: robot current position → reception spot
        RequestRouteAndFollowWaypoints(Robot, Robot->CurrentLocationCode, InReceptionSpotCode, false);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' routing to RECEPTION '%s'"),
            *InRobotId, *InReceptionSpotCode);
    }
    else
    {
        // STOCK_OUT: robot → target spot directly
        RequestRouteAndFollowWaypoints(Robot, Robot->CurrentLocationCode, InTargetSpotCode, false);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' routing to TARGET '%s'"),
            *InRobotId, *InTargetSpotCode);
    }
}

void ARobotManager::HandlePackageReceived(int64 PackageId, const FString& Sku, int32 Quantity,
    const FString& ReceptionSpotCode, const FString& TargetSpotCode)
{
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Package received: pkg=%lld sku=%s qty=%d at reception=%s -> target=%s"),
        PackageId, *Sku, Quantity, *ReceptionSpotCode, *TargetSpotCode);

    if (!WarehouseEnv)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] No WarehouseEnv - cannot spawn package visual"));
        return;
    }

    // Spawn a box visual at the reception spot
    WarehouseEnv->SpawnItemVisualAtSpot(ReceptionSpotCode, PackageId, Sku, Quantity);

    // Auto-dispatch: find an available (idle) robot with enough battery
    AWarehouseRobot* BestRobot = nullptr;
    float BestDist = FLT_MAX;

    for (auto& Pair : RobotActors)
    {
        AWarehouseRobot* Robot = Pair.Value;
        if (!Robot) continue;

        // Check if robot is available (idle, not on a mission)
        const FRobotMissionData& CurMission = Robot->ActiveMission;
        bool bIdle = CurMission.PackageId == 0 && CurMission.MissionType.IsEmpty();

        if (!bIdle) continue;

        // Skip robots with low battery (they should go charge instead)
        if (Robot->GetBatteryLevel() < 20.0f)
        {
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Skipping robot '%s' for dispatch - low battery (%.0f%%)"),
                *Robot->RobotId, Robot->GetBatteryLevel());
            // Auto-send to nearest charging station
            AutoChargeRobot(Robot);
            continue;
        }

        // Prefer the closest idle robot to the reception spot
        FVector ReceptionPos;
        if (WarehouseEnv->GetSpotPosition(ReceptionSpotCode, ReceptionPos))
        {
            float Dist = FVector::Dist(Robot->GetActorLocation(), ReceptionPos);
            if (Dist < BestDist)
            {
                BestDist = Dist;
                BestRobot = Robot;
            }
        }
        else if (!BestRobot)
        {
            BestRobot = Robot; // fallback: any idle robot
        }
    }

    if (!BestRobot)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] No idle robot available for package %lld - will wait for mission command"), PackageId);
        return;
    }

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Auto-dispatching robot '%s' for package %lld (dist=%.0f)"),
        *BestRobot->RobotId, PackageId, BestDist);

    // Resolve positions
    FVector ReceptionPos = FVector::ZeroVector;
    WarehouseEnv->GetSpotPosition(ReceptionSpotCode, ReceptionPos);

    FVector TargetPos = FVector::ZeroVector;
    if (!WarehouseEnv->GetSpotPosition(TargetSpotCode, TargetPos))
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Target spot '%s' not found for auto-dispatch"), *TargetSpotCode);
    }

    // Build mission and dispatch
    FRobotMissionData Mission;
    Mission.PackageId = PackageId;
    Mission.MissionType = TEXT("STOCK_IN");
    Mission.ReceptionSpotCode = ReceptionSpotCode;
    Mission.TargetSpotCode = TargetSpotCode;
    Mission.ReceptionSpotPosition = ReceptionPos;
    Mission.TargetSpotPosition = TargetPos;
    Mission.MissionPhase = TEXT("GO_TO_RECEPTION");

    BestRobot->SetMission(Mission);

    // Use route planning for auto-dispatch navigation
    RequestRouteAndFollowWaypoints(BestRobot, BestRobot->CurrentLocationCode, ReceptionSpotCode, false);

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' auto-dispatched: routing to reception '%s'"),
        *BestRobot->RobotId, *ReceptionSpotCode);
}

void ARobotManager::HandleRobotArrival(AWarehouseRobot* Robot, const FString& MissionType)
{
    if (!Robot) return;

    const FRobotMissionData& Mission = Robot->ActiveMission;

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' arrived for mission type=%s, package=%lld, spot=%s, phase=%s"),
        *Robot->RobotId, *MissionType, Mission.PackageId, *Mission.TargetSpotCode, *Mission.MissionPhase);

    // Update robot's current location code based on where it arrived
    if (Mission.MissionPhase == TEXT("GO_TO_RECEPTION"))
    {
        Robot->CurrentLocationCode = Mission.ReceptionSpotCode;
    }
    else if (Mission.MissionPhase == TEXT("GO_TO_TARGET"))
    {
        Robot->CurrentLocationCode = Mission.TargetSpotCode;
    }

    // Handle multi-phase missions (STOCK_IN: go to reception first, then to target)
    if (MissionType == TEXT("STOCK_IN") && Mission.MissionPhase == TEXT("GO_TO_RECEPTION"))
    {
        // Arrived at reception - pick up item, remove visual, then route to target storage spot
        Robot->PickUpItem();

        // Remove the package box visual from the reception spot
        if (WarehouseEnv)
        {
            WarehouseEnv->RemoveItemVisual(Mission.PackageId);
        }

        // Publish package.taken so backend updates package status to IN_TRANSIT
        if (NatsClient && NatsClient->IsConnected())
        {
            FString TakenJson = FString::Printf(
                TEXT("{\"packageId\":%lld,\"robotId\":\"%s\"}"),
                Mission.PackageId, *Robot->RobotId);
            NatsClient->Publish(TEXT("package.taken"), TakenJson);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Published package.taken for pkg=%lld robot=%s"),
                Mission.PackageId, *Robot->RobotId);
        }

        Robot->ActiveMission.MissionPhase = TEXT("GO_TO_TARGET");

        // Use route planning for second leg: reception → target
        RequestRouteAndFollowWaypoints(Robot, Mission.ReceptionSpotCode, Mission.TargetSpotCode, true);

        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' picked up at reception, routing to target '%s'"),
            *Robot->RobotId, *Mission.TargetSpotCode);
        return; // Don't clear mission yet
    }

    if (MissionType == TEXT("STOCK_IN") || MissionType == TEXT("STOCK_OUT"))
    {
        // Arrived at final destination - drop off items
        Robot->DropOffItems();

        // Publish package.delivered so backend updates package status to DELIVERED
        if (NatsClient && NatsClient->IsConnected())
        {
            FString DeliveredJson = FString::Printf(
                TEXT("{\"packageId\":%lld}"),
                Mission.PackageId);
            NatsClient->Publish(TEXT("package.delivered"), DeliveredJson);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Published package.delivered for pkg=%lld"),
                Mission.PackageId);
        }
    }

    // Publish completion event (keep for analytics/monitoring)
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

// ─── Robot Sync from Backend ────────────────────────────────────────

void ARobotManager::AutoChargeRobot(AWarehouseRobot* Robot)
{
    if (!Robot || ChargingStations.Num() == 0) return;

    FVector RobotPos = Robot->GetActorLocation();
    FVector NearestStation = ChargingStations[0];
    float MinDist = FVector::Dist(RobotPos, NearestStation);

    for (const FVector& Station : ChargingStations)
    {
        float Dist = FVector::Dist(RobotPos, Station);
        if (Dist < MinDist)
        {
            MinDist = Dist;
            NearestStation = Station;
        }
    }

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Auto-charging robot '%s' (battery=%.0f%%) → nearest station at %s"),
        *Robot->RobotId, Robot->GetBatteryLevel(), *NearestStation.ToString());

    // Set a charge mission so the robot doesn't get dispatched while charging
    FRobotMissionData ChargeMission;
    ChargeMission.PackageId = 0;
    ChargeMission.MissionType = TEXT("CHARGE");
    ChargeMission.ReceptionSpotCode = TEXT("");
    ChargeMission.TargetSpotCode = TEXT("CHARGING_STATION");
    ChargeMission.ReceptionSpotPosition = NearestStation;
    ChargeMission.TargetSpotPosition = NearestStation;
    ChargeMission.MissionPhase = TEXT("GO_TO_CHARGE");

    Robot->SetMission(ChargeMission);
    Robot->GoCharge(NearestStation);
}

void ARobotManager::FetchRobotsFromBackend()
{
    FString Url = RobotApiUrl + TEXT("/api/robots");
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Fetching robots from backend: %s"), *Url);

    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> Request = FHttpModule::Get().CreateRequest();
    Request->SetURL(Url);
    Request->SetVerb(TEXT("GET"));
    Request->SetHeader(TEXT("Content-Type"), TEXT("application/json"));
    Request->SetTimeout(10.0f);

    Request->OnProcessRequestComplete().BindLambda([this](FHttpRequestPtr Req, FHttpResponsePtr Resp, bool bSuccess)
    {
        if (!bSuccess || !Resp.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Robot fetch failed (no response)"));
            return;
        }

        int32 Code = Resp->GetResponseCode();
        FString Body = Resp->GetContentAsString();

        if (Code != 200)
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Robot fetch returned HTTP %d: %s"), Code, *Body);
            return;
        }

        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot response received (%d bytes)"), Body.Len());

        // Parse JSON array
        TArray<TSharedPtr<FJsonValue>> JsonArray;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Body);

        if (!FJsonSerializer::Deserialize(Reader, JsonArray))
        {
            UE_LOG(LogTemp, Error, TEXT("[RobotManager] Failed to parse robots JSON"));
            return;
        }

        int32 SpawnedCount = 0;
        for (const TSharedPtr<FJsonValue>& Item : JsonArray)
        {
            TSharedPtr<FJsonObject> Obj = Item->AsObject();
            if (!Obj.IsValid()) continue;

            FSmartLogisticRobotData Data;
            Data.RobotId = Obj->GetStringField(TEXT("id"));
            Data.RobotName = Obj->GetStringField(TEXT("name"));
            Data.BatteryLevel = Obj->GetIntegerField(TEXT("batteryLevel"));
            Data.bAvailable = Obj->GetBoolField(TEXT("available"));
            Data.CurrentLocation = Obj->GetStringField(TEXT("currentLocation"));

            FString ModeStr = Obj->GetStringField(TEXT("operationalMode"));
            if (ModeStr == TEXT("IDLE"))        Data.OperationalMode = ERobotOperationalMode::IDLE;
            else if (ModeStr == TEXT("MOVING")) Data.OperationalMode = ERobotOperationalMode::MOVING;
            else if (ModeStr == TEXT("PICKING"))Data.OperationalMode = ERobotOperationalMode::PICKING;
            else if (ModeStr == TEXT("CHARGING"))Data.OperationalMode = ERobotOperationalMode::CHARGING;
            else if (ModeStr == TEXT("OFFLINE"))Data.OperationalMode = ERobotOperationalMode::OFFLINE;
            else                                Data.OperationalMode = ERobotOperationalMode::IDLE;

            AWarehouseRobot* Robot = FindOrCreateRobot(Data);
            if (Robot)
            {
                Robot->UpdateFromData(Data);
                SpawnedCount++;
            }
        }

        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Synced %d robots from backend (total actors: %d)"),
            SpawnedCount, RobotActors.Num());
    });

    Request->ProcessRequest();
}

// ─── Route Planning Integration ────────────────────────────────────

void ARobotManager::RequestRouteAndFollowWaypoints(AWarehouseRobot* Robot, const FString& FromCode, const FString& ToCode,
    bool bPickUpAtDestination)
{
    if (!Robot)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] RequestRouteAndFollowWaypoints: null robot"));
        return;
    }

    // If no "from" code, resolve it from the robot's current world position
    FString ResolvedFrom = FromCode;
    if (ResolvedFrom.IsEmpty() && WarehouseEnv)
    {
        ResolvedFrom = WarehouseEnv->FindNearestRootPointCode(Robot->GetActorLocation());
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' no CurrentLocationCode, resolved from world pos → %s"),
            *Robot->RobotId, *ResolvedFrom);
    }

    // If still no location code, fall back to direct movement
    if (ResolvedFrom.IsEmpty() || ToCode.IsEmpty())
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Robot '%s' missing location code (from=%s, to=%s), using direct move"),
            *Robot->RobotId, *ResolvedFrom, *ToCode);

        // Direct move as fallback
        if (bPickUpAtDestination)
        {
            Robot->MoveTo(Robot->ActiveMission.TargetSpotPosition);
        }
        else
        {
            Robot->MoveTo(Robot->ActiveMission.ReceptionSpotPosition);
        }
        return;
    }

    // Same location — no route needed
    if (ResolvedFrom == ToCode)
    {
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' already at destination '%s'"), *Robot->RobotId, *ToCode);

        // Trigger arrival immediately
        if (Robot->bOnMission)
        {
            Robot->OnArrivalAtTarget.Broadcast(Robot, Robot->ActiveMission.MissionType);
        }
        return;
    }

    FString Url = FString::Printf(TEXT("%s/api/routes/from/%s/to/%s"),
        *WarehouseApiUrl, *ResolvedFrom, *ToCode);

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Requesting route: %s"), *Url);

    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> Request = FHttpModule::Get().CreateRequest();
    Request->SetURL(Url);
    Request->SetVerb(TEXT("GET"));
    Request->SetHeader(TEXT("Content-Type"), TEXT("application/json"));
    Request->SetTimeout(5.0f);

    // Capture robot ID and pick-up flag in the lambda
    FString RobotId = Robot->RobotId;
    bool bPickUp = bPickUpAtDestination;

    Request->OnProcessRequestComplete().BindLambda([this, RobotId, bPickUp](FHttpRequestPtr Req, FHttpResponsePtr Resp, bool bSuccess)
    {
        AWarehouseRobot** Found = RobotActors.Find(RobotId);
        if (!Found || !*Found)
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Route response for robot '%s' but robot no longer exists"), *RobotId);
            return;
        }
        AWarehouseRobot* Robot = *Found;

        if (!bSuccess || !Resp.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Route request failed for robot '%s', falling back to direct move"), *RobotId);
            // Fallback: direct move
            if (bPickUp)
                Robot->MoveTo(Robot->ActiveMission.TargetSpotPosition);
            else
                Robot->MoveTo(Robot->ActiveMission.ReceptionSpotPosition);
            return;
        }

        int32 Code = Resp->GetResponseCode();
        FString Body = Resp->GetContentAsString();

        if (Code != 200)
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Route API returned HTTP %d for robot '%s', falling back to direct move"), Code, *RobotId);
            if (bPickUp)
                Robot->MoveTo(Robot->ActiveMission.TargetSpotPosition);
            else
                Robot->MoveTo(Robot->ActiveMission.ReceptionSpotPosition);
            return;
        }

        // Parse the route response: { "from": "...", "to": "...", "path": [...], "waypoints": [...] }
        TSharedPtr<FJsonObject> RootObj;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Body);

        if (!FJsonSerializer::Deserialize(Reader, RootObj) || !RootObj.IsValid())
        {
            UE_LOG(LogTemp, Error, TEXT("[RobotManager] Failed to parse route JSON for robot '%s'"), *RobotId);
            Robot->MoveTo(Robot->ActiveMission.TargetSpotPosition);
            return;
        }

        // Debug: log the raw JSON to understand structure
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Route response for '%s': %s"), *RobotId, *Body.Left(500));

        TArray<FVector> Waypoints;
        int32 ResolvedCount = 0;

        // ─── Strategy 1: Use waypoints array with x,y coordinates from backend ───
        const TArray<TSharedPtr<FJsonValue>>* WaypointsArray;
        bool bHasWaypoints = RootObj->TryGetArrayField(TEXT("waypoints"), WaypointsArray);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] waypoints field present=%s, count=%d"),
            bHasWaypoints ? TEXT("true") : TEXT("false"),
            bHasWaypoints ? WaypointsArray->Num() : 0);

        if (bHasWaypoints && WaypointsArray->Num() > 0)
        {
            float CellSz = WarehouseEnv ? WarehouseEnv->GetCellSize() : 200.0f;
            FVector ActorOffset = WarehouseEnv ? WarehouseEnv->GetActorLocation() : FVector::ZeroVector;

            for (const TSharedPtr<FJsonValue>& WpValue : *WaypointsArray)
            {
                TSharedPtr<FJsonObject> WpObj = WpValue->AsObject();
                if (!WpObj.IsValid()) continue;

                double WpX = WpObj->GetNumberField(TEXT("x"));
                double WpY = WpObj->GetNumberField(TEXT("y"));
                FString WpCode = WpObj->GetStringField(TEXT("code"));
                FString WpAction = WpObj->GetStringField(TEXT("action"));

                // Backend: x = col*cellSize, y = row*cellSize (corner-based)
                // UE5 CellToWorldPosition: X = (row+0.5)*cellSize, Y = (col+0.5)*cellSize (center-based + actor loc)
                float UE5_X = static_cast<float>(WpY) + CellSz / 2.0f;
                float UE5_Y = static_cast<float>(WpX) + CellSz / 2.0f;
                FVector WorldPos = ActorOffset + FVector(UE5_X, UE5_Y, 0.0f);
                Waypoints.Add(WorldPos);
                ResolvedCount++;

                UE_LOG(LogTemp, Log, TEXT("[RobotManager]   Waypoint %d: code=%s pos=(%.0f,%.0f) action=%s"),
                    ResolvedCount, *WpCode, WpX, WpY, *WpAction);
            }

            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' route: %d waypoints with coordinates from backend"),
                *RobotId, ResolvedCount);
        }
        else
        {
            // ─── Strategy 2: Fallback - resolve path codes via WarehouseEnv ───
            const TArray<TSharedPtr<FJsonValue>>* PathArray;
            if (!RootObj->TryGetArrayField(TEXT("path"), PathArray) || PathArray->Num() == 0)
            {
                UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Empty route path for robot '%s'"), *RobotId);
                Robot->MoveTo(Robot->ActiveMission.TargetSpotPosition);
                return;
            }

            for (const TSharedPtr<FJsonValue>& PathItem : *PathArray)
            {
                FString PointCode = PathItem->AsString();
                FVector WorldPos;

                if (WarehouseEnv && WarehouseEnv->GetSpotPosition(PointCode, WorldPos))
                {
                    Waypoints.Add(WorldPos);
                    ResolvedCount++;
                }
                else
                {
                    UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Could not resolve root point '%s' to world position"), *PointCode);
                }
            }

            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' route: %d root points → %d waypoints resolved via env"),
                *RobotId, PathArray->Num(), ResolvedCount);
        }

        if (Waypoints.Num() == 0)
        {
            UE_LOG(LogTemp, Error, TEXT("[RobotManager] No waypoints resolved for robot '%s', falling back to direct move"), *RobotId);
            if (bPickUp)
                Robot->MoveTo(Robot->ActiveMission.TargetSpotPosition);
            else
                Robot->MoveTo(Robot->ActiveMission.ReceptionSpotPosition);
            return;
        }

        // Instruct the robot to follow the waypoints
        Robot->FollowWaypoints(Waypoints);
    });

    Request->ProcessRequest();
}
