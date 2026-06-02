// Copyright Epic Games, Inc. All Rights Reserved.

#include "RobotManager.h"
#include "WarehouseRobot.h"
#include "WarehouseEnvironment.h"
#include "Engine/World.h"
#include "Engine/Engine.h"
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
    HttpClient = nullptr;
}

void ARobotManager::BeginPlay()
{
    Super::BeginPlay();

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Starting SmartLogistics simulation..."));
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot API: %s | Warehouse API: %s | MaxRobots: %d"),
        *RobotApiUrl, *WarehouseApiUrl, MaxRobots);

    // Show connection info on screen for debugging
    if (GEngine)
    {
        FString ConnInfo = FString::Printf(
            TEXT("[SmartLogistics Simulation]\n  Robot API: %s\n  Warehouse API: %s\n  STOMP: %s\n  MaxRobots: %d"),
            *RobotApiUrl, *WarehouseApiUrl, *RabbitStompUrl, MaxRobots);
        GEngine->AddOnScreenDebugMessage(-1, 15.0f, FColor::White, *ConnInfo);
    }

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

    ConnectToBackend();

    // Fetch robots from backend API after a short delay (let warehouse env build first)
    FTimerHandle RobotFetchTimerHandle;
    GetWorldTimerManager().SetTimer(RobotFetchTimerHandle, [this]()
    {
        FetchRobotsFromBackend();
        // Also fetch active packages that may have arrived before STOMP connected
        FetchActivePackages();
    }, 6.0f, false);

    // Wait for WarehouseEnvironment to finish its own layout fetch (it does this in BeginPlay),
    // then extract charging stations.
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
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Still no WarehouseEnv after delay!"));
        }
    }, 5.0f, false);
}

void ARobotManager::EndPlay(const EEndPlayReason::Type EndPlayReason)
{
    // Mark as shutting down first to prevent Tick from accessing destroyed state
    bIsBackendConnected = false;
    DisconnectFromBackend();
    Super::EndPlay(EndPlayReason);
}

void ARobotManager::Tick(float DeltaTime)
{
    Super::Tick(DeltaTime);

    // Skip all processing during shutdown (EndPlay already called DisconnectFromBackend)
    if (!bIsBackendConnected && !HttpClient && !StompClient)
    {
        return;
    }

    if (HttpClient && HttpClient->IsConnected())
    {
        // Poll for pending packages/commands
        LastPollTime += DeltaTime;
        if (LastPollTime >= PollInterval)
        {
            LastPollTime = 0.0f;
            HttpClient->PollForCommands();
        }

        // Publish telemetry
        if (TelemetryInterval > 0.0f)
        {
            LastTelemetryTime += DeltaTime;
            if (LastTelemetryTime >= TelemetryInterval)
            {
                LastTelemetryTime = 0.0f;
                PublishTelemetry();
            }
        }
    }

    bIsBackendConnected = HttpClient && HttpClient->IsConnected();

    // Auto-reconnect STOMP if it drops (StompClient auto-resubscribes on CONNECTED)
    StompReconnectTimer += DeltaTime;
    if (StompClient && !StompClient->IsConnected() && StompReconnectTimer >= StompReconnectDelay)
    {
        StompReconnectTimer = 0.0f;
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] STOMP disconnected — attempting reconnect to %s (delay=%.1fs)"),
            *RabbitStompUrl, StompReconnectDelay);
        if (GEngine)
        {
            GEngine->AddOnScreenDebugMessage(-1, 5.0f, FColor::Orange,
                FString::Printf(TEXT("[!] STOMP disconnected -- reconnecting (delay=%.1fs)..."), StompReconnectDelay));
        }
        StompClient->Connect(RabbitStompUrl, TEXT("guest"), TEXT("guest"));

        // Exponential backoff: double the delay for next attempt, capped at max
        StompReconnectDelay = FMath::Min(StompReconnectDelay * 2.0f, StompReconnectMaxDelay);
    }

    // Reset reconnect delay when STOMP is connected (successful connection resets backoff)
    if (StompClient && StompClient->IsConnected())
    {
        if (StompReconnectDelay != StompReconnectMinDelay)
        {
            StompReconnectDelay = StompReconnectMinDelay;
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] STOMP reconnected — reset backoff to %.1fs"), StompReconnectDelay);
        }
        StompReconnectTimer = 0.0f;
    }

    // Update robot CurrentLocationCode while moving (for real-time telemetry)
    if (WarehouseEnv)
    {
        for (auto& Pair : RobotActors)
        {
            AWarehouseRobot* Robot = Pair.Value;
            if (!Robot || !Robot->IsMoving()) continue;

            // Resolve current world position to nearest root point code
            FString NearestCode = WarehouseEnv->FindNearestRootPointCode(Robot->GetActorLocation());
            if (!NearestCode.IsEmpty() && NearestCode != Robot->CurrentLocationCode)
            {
                Robot->CurrentLocationCode = NearestCode;
            }
        }
    }

    // HTTP polling fallback for packages when STOMP is not connected
    bool bStompConnected = StompClient && StompClient->IsConnected();
    if (!bStompConnected && bIsBackendConnected)
    {
        PackagePollTimer += DeltaTime;
        if (PackagePollTimer >= PackagePollInterval)
        {
            PackagePollTimer = 0.0f;
            FetchActivePackages();
        }
    }

    // Show connection status on screen every 5 seconds
    StatusDisplayTimer += DeltaTime;
    if (StatusDisplayTimer >= 5.0f && GEngine)
    {
        StatusDisplayTimer = 0.0f;
        FString StatusMsg = FString::Printf(
            TEXT("[LINK] HTTP: %s | STOMP: %s | Robots: %d | Events: %d"),
            bIsBackendConnected ? TEXT("[OK] Connected") : TEXT("[X] Disconnected"),
            (StompClient && StompClient->IsConnected()) ? TEXT("[OK] Connected") : TEXT("[X] Disconnected"),
            RobotActors.Num(),
            TotalEventsReceived);
        GEngine->AddOnScreenDebugMessage(1, 4.5f, FColor::White, *StatusMsg);
    }
}

void ARobotManager::ConnectToBackend()
{
    if (HttpClient && HttpClient->IsConnected())
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Already connected to backend"));
        return;
    }

    HttpClient = NewObject<UHttpRobotClient>(this, TEXT("HttpClient"));
    if (HttpClient)
    {
        HttpClient->Initialize(RobotApiUrl);
        HttpClient->SetWarehouseUrl(WarehouseApiUrl);
        HttpClient->OnRobotCommandReceived.AddDynamic(this, &ARobotManager::HandleRobotCommand);
        HttpClient->OnMissionCommandReceived.AddDynamic(this, &ARobotManager::HandleMissionCommand);
        HttpClient->OnPackageReceived.AddDynamic(this, &ARobotManager::HandlePackageReceived);
        HttpClient->StartPolling();
        bIsBackendConnected = true;
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] HTTP client created and connected to %s (warehouse: %s)"), *RobotApiUrl, *WarehouseApiUrl);
    }
    else
    {
        UE_LOG(LogTemp, Error, TEXT("[RobotManager] Failed to create HTTP client!"));
    }

    // Connect to RabbitMQ via STOMP-over-WebSocket for real-time events
    if (!RabbitStompUrl.IsEmpty())
    {
        StompClient = NewObject<UStompClient>(this, TEXT("StompClient"));
        if (StompClient)
        {
            StompClient->OnMessageReceived.AddDynamic(this, &ARobotManager::HandleStompMessage);
            StompClient->OnConnected.AddDynamic(this, &ARobotManager::OnStompConnected);
            StompClient->Connect(RabbitStompUrl, TEXT("guest"), TEXT("guest"));

            UE_LOG(LogTemp, Log, TEXT("[RobotManager] STOMP client connecting to %s"), *RabbitStompUrl);
        }
    }
}

void ARobotManager::DisconnectFromBackend()
{
    if (StompClient)
    {
        StompClient->Disconnect();
        StompClient = nullptr;
    }
    if (HttpClient)
    {
        HttpClient->Disconnect();
        HttpClient = nullptr;
    }
    bIsBackendConnected = false;
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Disconnected from backend"));
}

void ARobotManager::HandleRobotCommand(const FString& RobotId, const FString& CommandType, const FString& TargetLocation)
{
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Command: %s -> %s (%s)"), *RobotId, *CommandType, *TargetLocation);
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
            float SpreadX = 200.0f * (SlotIndex % 5);
            float SpreadY = 200.0f * (SlotIndex / 5);
            SpawnPos += FVector(SpreadX, SpreadY, 0.0f);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' spawned at spread position (slot %d): %s"),
                *Data.RobotId, SlotIndex, *SpawnPos.ToString());
        }
    }
    else
    {
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
    if (!HttpClient || !HttpClient->IsConnected()) return;
    if (RobotActors.Num() == 0) return;

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

        // Send per-robot telemetry via HTTP PUT
        FString LocationCode = Data.CurrentLocation;
        // If the robot has a CurrentLocationCode (RP-Rxx-Cyy), use that instead of raw coordinates
        if (!Pair.Value->CurrentLocationCode.IsEmpty())
        {
            LocationCode = Pair.Value->CurrentLocationCode;
        }

        FString TelemetryJson = FString::Printf(
            TEXT("{\"batteryLevel\":%d,\"currentLocation\":\"%s\",\"operationalMode\":\"%s\"}"),
            Data.BatteryLevel,
            *LocationCode,
            *ModeStr
        );

        HttpClient->SendTelemetry(Data.RobotId, TelemetryJson);
        Count++;
    }

    UE_LOG(LogTemp, Verbose, TEXT("[RobotManager] Published telemetry for %d robots via HTTP"), Count);
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

void ARobotManager::OnStompConnected()
{
    if (!StompClient || !StompClient->IsConnected()) return;

    // Subscribe to logistics.exchange with the correct routing keys
    // This creates server-generated exclusive queues so UE5 gets its own copy
    // (won't compete with Java consumers on the durable queues)
    StompClient->SubscribeToExchange(TEXT("logistics.exchange"), TEXT("robot.command"), TEXT("auto"));
    StompClient->SubscribeToExchange(TEXT("logistics.exchange"), TEXT("package.dispatched"), TEXT("auto"));
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] STOMP connected — subscribed to logistics.exchange (robot.command, package.dispatched)"));

    if (GEngine)
    {
        GEngine->AddOnScreenDebugMessage(-1, 5.0f, FColor::Green,
            TEXT("[STOMP] Connected & subscribed to logistics.exchange"));
    }
}

void ARobotManager::HandleStompMessage(const FString& Destination, const FString& Body)
{
    TotalEventsReceived++;
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] STOMP message on '%s': %s"), *Destination, *Body.Left(500));

    TSharedPtr<FJsonObject> RootObj;
    TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Body);

    if (!FJsonSerializer::Deserialize(Reader, RootObj) || !RootObj.IsValid())
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Failed to parse STOMP message JSON from '%s'"), *Destination);
        return;
    }

    // Route based on STOMP destination
    if (Destination.Contains(TEXT("robot.command")))
    {
        // Check if this is a mission command (has missionType) or a simple command (has command)
        if (RootObj->HasField(TEXT("missionType")))
        {
            FString RobotId = RootObj->GetStringField(TEXT("robotId"));
            FString MissionType = RootObj->GetStringField(TEXT("missionType"));

            int64 PackageId = 0;
            if (RootObj->HasField(TEXT("packageId")))
                PackageId = static_cast<int64>(RootObj->GetNumberField(TEXT("packageId")));
            else if (RootObj->HasField(TEXT("orderId")))
                PackageId = static_cast<int64>(RootObj->GetNumberField(TEXT("orderId")));

            FString ReceptionSpot, TargetSpot;
            if (MissionType == TEXT("STOCK_OUT"))
            {
                ReceptionSpot = RootObj->HasField(TEXT("pickupSpotCode")) ? RootObj->GetStringField(TEXT("pickupSpotCode")) : TEXT("");
                TargetSpot = RootObj->HasField(TEXT("deliverySpotCode")) ? RootObj->GetStringField(TEXT("deliverySpotCode")) : TEXT("");
            }
            else
            {
                ReceptionSpot = RootObj->HasField(TEXT("receptionSpotCode")) ? RootObj->GetStringField(TEXT("receptionSpotCode")) : TEXT("");
                TargetSpot = RootObj->HasField(TEXT("targetSpotCode")) ? RootObj->GetStringField(TEXT("targetSpotCode")) : TEXT("");
            }

            FString ItemSku = RootObj->HasField(TEXT("itemSku")) ? RootObj->GetStringField(TEXT("itemSku")) : TEXT("");
            int32 Quantity = RootObj->HasField(TEXT("quantity")) ? RootObj->GetIntegerField(TEXT("quantity")) : 0;

            HandleMissionCommand(RobotId, PackageId, MissionType, ReceptionSpot, TargetSpot, ItemSku, Quantity);
        }
        else
        {
            FString RobotId = RootObj->GetStringField(TEXT("robotId"));
            FString Command = RootObj->GetStringField(TEXT("command"));
            FString Target = RootObj->HasField(TEXT("targetLocation")) ? RootObj->GetStringField(TEXT("targetLocation")) : TEXT("");
            HandleRobotCommand(RobotId, Command, Target);
        }
    }
    else if (Destination.Contains(TEXT("package.dispatch")))
    {
        int64 PackageId = static_cast<int64>(RootObj->GetNumberField(TEXT("packageId")));
        FString Sku = RootObj->GetStringField(TEXT("sku"));
        int32 Quantity = RootObj->GetIntegerField(TEXT("quantity"));
        FString ReceptionSpot = RootObj->GetStringField(TEXT("receptionSpotCode"));
        FString TargetSpot = RootObj->GetStringField(TEXT("targetSpotCode"));

        HandlePackageReceived(PackageId, Sku, Quantity, ReceptionSpot, TargetSpot);
    }
    else
    {
        UE_LOG(LogTemp, Verbose, TEXT("[RobotManager] Unhandled STOMP destination: %s"), *Destination);
    }
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

    LayoutData.LayoutId = static_cast<int64>(RootObj->GetNumberField(TEXT("id")));
    LayoutData.LayoutName = RootObj->GetStringField(TEXT("name"));
    LayoutData.Rows = RootObj->GetIntegerField(TEXT("rows"));
    LayoutData.Cols = RootObj->GetIntegerField(TEXT("cols"));

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

    if (WarehouseEnv)
    {
        WarehouseEnv->BuildFromLayout(LayoutData);

        ChargingStations.Empty();
        TArray<FWarehouseLocation> ChargeLocations;
        WarehouseEnv->GetLocationsByType(TEXT("CHARGING"), ChargeLocations);
        for (const auto& Loc : ChargeLocations)
        {
            ChargingStations.Add(Loc.Position);
        }

        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Layout applied! %d charging stations extracted"), ChargingStations.Num());
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

    AWarehouseRobot** Found = RobotActors.Find(InRobotId);
    if (!Found || !*Found)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Robot '%s' not found for mission"), *InRobotId);
        return;
    }

    AWarehouseRobot* Robot = *Found;

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

    FRobotMissionData Mission;
    Mission.PackageId = InPackageId;
    Mission.MissionType = InMissionType;
    Mission.ReceptionSpotCode = InReceptionSpotCode;
    Mission.TargetSpotCode = InTargetSpotCode;
    Mission.ReceptionSpotPosition = ReceptionPos;
    Mission.TargetSpotPosition = TargetPos;
    Mission.MissionPhase = TEXT("GO_TO_RECEPTION");

    Robot->SetMission(Mission);

    // Notify backend of state change: robot is now MOVING (dispatched for mission)
    NotifyRobotStateChanged(Robot, TEXT("MOVING"));

    if (InMissionType == TEXT("STOCK_IN"))
    {
        // STOCK_IN: Robot goes to reception dock first to pick up, then to shelf to store
        RequestRouteAndFollowWaypoints(Robot, Robot->CurrentLocationCode, InReceptionSpotCode, false);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' STOCK_IN: routing to RECEPTION '%s'"),
            *InRobotId, *InReceptionSpotCode);
    }
    else if (InMissionType == TEXT("STOCK_OUT"))
    {
        // STOCK_OUT: Robot goes to shelf (pickup) first to pick items, then to delivery dock
        // ReceptionSpotCode = pickupSpotCode (shelf), TargetSpotCode = deliverySpotCode (dock)
        RequestRouteAndFollowWaypoints(Robot, Robot->CurrentLocationCode, InReceptionSpotCode, false);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' STOCK_OUT: routing to PICKUP '%s' (shelf)"),
            *InRobotId, *InReceptionSpotCode);

        if (GEngine)
        {
            FString OrderMsg = FString::Printf(TEXT("[ORDER] Robot '%s' dispatched → pick %s x%d from shelf %s → deliver to %s"),
                *InRobotId, *InItemSku, InQuantity, *InReceptionSpotCode, *InTargetSpotCode);
            GEngine->AddOnScreenDebugMessage(-1, 8.0f, FColor::Cyan, *OrderMsg);
        }
    }
    else
    {
        RequestRouteAndFollowWaypoints(Robot, Robot->CurrentLocationCode, InTargetSpotCode, false);
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' routing to TARGET '%s'"),
            *InRobotId, *InTargetSpotCode);
    }
}

void ARobotManager::HandlePackageReceived(int64 PackageId, const FString& Sku, int32 Quantity,
    const FString& ReceptionSpotCode, const FString& TargetSpotCode)
{
    // Deduplicate: skip if we already processed this package
    if (ProcessedPackageIds.Contains(PackageId))
    {
        UE_LOG(LogTemp, Verbose, TEXT("[RobotManager] Package #%lld already processed — skipping"), PackageId);
        return;
    }
    ProcessedPackageIds.Add(PackageId);

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Package received: pkg=%lld sku=%s qty=%d at reception=%s -> target=%s"),
        PackageId, *Sku, Quantity, *ReceptionSpotCode, *TargetSpotCode);

    // On-screen debug message so user can see package arrival in viewport
    if (GEngine)
    {
        FString DebugMsg = FString::Printf(TEXT("[PKG] Package #%lld arrived! SKU=%s x%d\n  At: %s -> %s"),
            PackageId, *Sku, Quantity, *ReceptionSpotCode, *TargetSpotCode);
        GEngine->AddOnScreenDebugMessage(-1, 8.0f, FColor::Yellow, *DebugMsg);
    }

    if (!WarehouseEnv)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] No WarehouseEnv - cannot spawn package visual"));
        if (GEngine)
        {
            GEngine->AddOnScreenDebugMessage(-1, 5.0f, FColor::Red,
                TEXT("[ERROR] No WarehouseEnv - cannot show package!"));
        }
        return;
    }

    WarehouseEnv->SpawnItemVisualAtSpot(ReceptionSpotCode, PackageId, Sku, Quantity);

    // Auto-dispatch: find an available (idle) robot with enough battery
    AWarehouseRobot* BestRobot = nullptr;
    float BestDist = FLT_MAX;

    for (auto& Pair : RobotActors)
    {
        AWarehouseRobot* Robot = Pair.Value;
        if (!Robot) continue;

        const FRobotMissionData& CurMission = Robot->ActiveMission;
        bool bIdle = CurMission.PackageId == 0 && CurMission.MissionType.IsEmpty();

        if (!bIdle) continue;

        if (Robot->GetBatteryLevel() < 20.0f)
        {
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Skipping robot '%s' for dispatch - low battery (%.0f%%)"),
                *Robot->RobotId, Robot->GetBatteryLevel());
            AutoChargeRobot(Robot);
            continue;
        }

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
            BestRobot = Robot;
        }
    }

    if (!BestRobot)
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] No idle robot available for package %lld"), PackageId);
        return;
    }

    UE_LOG(LogTemp, Log, TEXT("[RobotManager] Auto-dispatching robot '%s' for package %lld (dist=%.0f)"),
        *BestRobot->RobotId, PackageId, BestDist);

    if (GEngine)
    {
        FString DispatchMsg = FString::Printf(TEXT("[BOT] Robot '%s' dispatched -> pick up pkg #%lld\n  Reception: %s -> Target: %s"),
            *BestRobot->RobotId, PackageId, *ReceptionSpotCode, *TargetSpotCode);
        GEngine->AddOnScreenDebugMessage(-1, 6.0f, FColor::Cyan, *DispatchMsg);
    }

    FVector ReceptionPos = FVector::ZeroVector;
    WarehouseEnv->GetSpotPosition(ReceptionSpotCode, ReceptionPos);

    FVector TargetPos = FVector::ZeroVector;
    if (!WarehouseEnv->GetSpotPosition(TargetSpotCode, TargetPos))
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Target spot '%s' not found for auto-dispatch"), *TargetSpotCode);
    }

    FRobotMissionData Mission;
    Mission.PackageId = PackageId;
    Mission.MissionType = TEXT("STOCK_IN");
    Mission.ReceptionSpotCode = ReceptionSpotCode;
    Mission.TargetSpotCode = TargetSpotCode;
    Mission.ReceptionSpotPosition = ReceptionPos;
    Mission.TargetSpotPosition = TargetPos;
    Mission.MissionPhase = TEXT("GO_TO_RECEPTION");

    BestRobot->SetMission(Mission);
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

    if (Mission.MissionPhase == TEXT("GO_TO_RECEPTION"))
    {
        Robot->CurrentLocationCode = Mission.ReceptionSpotCode;
    }
    else if (Mission.MissionPhase == TEXT("GO_TO_TARGET"))
    {
        Robot->CurrentLocationCode = Mission.TargetSpotCode;
    }

    // Handle multi-phase missions: both STOCK_IN and STOCK_OUT have 2 phases
    // Phase 1 (GO_TO_RECEPTION): Go to pickup point → pick up items → switch to Phase 2
    // Phase 2 (GO_TO_TARGET):    Go to delivery point → drop off items → complete mission
    if (Mission.MissionPhase == TEXT("GO_TO_RECEPTION"))
    {
        Robot->CurrentLocationCode = Mission.ReceptionSpotCode;

        // Notify backend: robot arrived at pickup point → PICKING
        NotifyRobotStateChanged(Robot, TEXT("PICKING"));

        if (GEngine)
        {
            FString PickMsg = FString::Printf(TEXT("[%s] Robot '%s' picked up items at %s -> heading to %s"),
                *MissionType, *Robot->RobotId, *Mission.ReceptionSpotCode, *Mission.TargetSpotCode);
            GEngine->AddOnScreenDebugMessage(-1, 6.0f, FColor::Green, *PickMsg);
        }

        Robot->PickUpItem();

        if (MissionType == TEXT("STOCK_IN") && WarehouseEnv)
        {
            WarehouseEnv->RemoveItemVisual(Mission.PackageId);
        }

        // Notify backend that items were picked up
        if (HttpClient && HttpClient->IsConnected())
        {
            FString TakenJson = FString::Printf(
                TEXT("{\"packageId\":%lld,\"robotId\":\"%s\",\"missionType\":\"%s\"}"),
                Mission.PackageId, *Robot->RobotId, *MissionType);
            HttpClient->PublishEvent(TEXT("package.taken"), TakenJson);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Notified package.taken for pkg=%lld robot=%s"),
                Mission.PackageId, *Robot->RobotId);
        }

        Robot->ActiveMission.MissionPhase = TEXT("GO_TO_TARGET");
        RequestRouteAndFollowWaypoints(Robot, Mission.ReceptionSpotCode, Mission.TargetSpotCode, true);

        // Notify backend: robot moving to delivery point → MOVING
        NotifyRobotStateChanged(Robot, TEXT("MOVING"));

        PublishMissionEvent(TEXT("PACKAGE_PICKED"), Robot->RobotId,
            Mission.PackageId, Mission.ReceptionSpotCode, Mission.MissionType);

        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' picked up at %s, routing to target '%s'"),
            *Robot->RobotId, *Mission.ReceptionSpotCode, *Mission.TargetSpotCode);
        return;
    }

    if (Mission.MissionPhase == TEXT("GO_TO_TARGET"))
    {
        Robot->CurrentLocationCode = Mission.TargetSpotCode;
        Robot->DropOffItems();

        // Notify backend: robot delivered items → IDLE (mission complete)
        NotifyRobotStateChanged(Robot, TEXT("IDLE"));

        // Notify backend that items were delivered
        if (HttpClient && HttpClient->IsConnected())
        {
            FString DeliveredJson = FString::Printf(
                TEXT("{\"packageId\":%lld,\"missionType\":\"%s\"}"),
                Mission.PackageId, *MissionType);
            HttpClient->PublishEvent(TEXT("package.delivered"), DeliveredJson);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Notified package.delivered for pkg=%lld"),
                Mission.PackageId);
        }

        if (GEngine)
        {
            FString DoneMsg = FString::Printf(TEXT("[%s] Robot '%s' delivered items at %s ✓"),
                *MissionType, *Robot->RobotId, *Mission.TargetSpotCode);
            GEngine->AddOnScreenDebugMessage(-1, 6.0f, FColor::Green, *DoneMsg);
        }
    }

    PublishMissionEvent(TEXT("PACKAGE_DELIVERED"), Robot->RobotId,
        Mission.PackageId, Mission.TargetSpotCode, Mission.MissionType);

    Robot->ClearMission();
}

void ARobotManager::PublishMissionEvent(const FString& EventType, const FString& RobotId,
    int64 PackageId, const FString& SpotCode, const FString& MissionType)
{
    if (!HttpClient || !HttpClient->IsConnected()) return;

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

    HttpClient->PublishEvent(EventType, Json);

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
            // Support both "id" and "robotId" field names
            if (Obj->HasField(TEXT("robotId")))
                Data.RobotId = Obj->GetStringField(TEXT("robotId"));
            else
                Data.RobotId = Obj->GetStringField(TEXT("id"));

            if (Obj->HasField(TEXT("name")))
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

void ARobotManager::FetchActivePackages()
{
    if (WarehouseApiUrl.IsEmpty()) return;

    // Fetch packages that are still in RECEIVED status (not yet delivered)
    FString Url = WarehouseApiUrl + TEXT("/api/packages");
    UE_LOG(LogTemp, Verbose, TEXT("[RobotManager] Fetching active packages from: %s"), *Url);

    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> Request = FHttpModule::Get().CreateRequest();
    Request->SetURL(Url);
    Request->SetVerb(TEXT("GET"));
    Request->SetHeader(TEXT("Content-Type"), TEXT("application/json"));
    Request->SetTimeout(10.0f);

    Request->OnProcessRequestComplete().BindLambda([this](FHttpRequestPtr Req, FHttpResponsePtr Resp, bool bSuccess)
    {
        if (!bSuccess || !Resp.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Active packages fetch failed"));
            return;
        }

        int32 Code = Resp->GetResponseCode();
        FString Body = Resp->GetContentAsString();

        if (Code != 200)
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Active packages fetch returned HTTP %d"), Code);
            return;
        }

        TArray<TSharedPtr<FJsonValue>> JsonArray;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Body);

        if (!FJsonSerializer::Deserialize(Reader, JsonArray))
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Failed to parse packages JSON"));
            return;
        }

        int32 VisualCount = 0;
        for (const TSharedPtr<FJsonValue>& Item : JsonArray)
        {
            TSharedPtr<FJsonObject> Obj = Item->AsObject();
            if (!Obj.IsValid()) continue;

            FString Status = Obj->HasField(TEXT("status")) ? Obj->GetStringField(TEXT("status")) : TEXT("");
            // Only show visuals for packages still at reception (not yet picked up)
            if (Status != TEXT("RECEIVED") && Status != TEXT("PENDING")) continue;

            int64 PackageId = static_cast<int64>(Obj->GetNumberField(TEXT("id")));
            FString Sku = Obj->HasField(TEXT("sku")) ? Obj->GetStringField(TEXT("sku")) : TEXT("PKG");
            int32 Quantity = Obj->HasField(TEXT("quantity")) ? Obj->GetIntegerField(TEXT("quantity")) : 1;
            FString ReceptionSpot = Obj->HasField(TEXT("receptionSpotCode"))
                ? Obj->GetStringField(TEXT("receptionSpotCode")) : TEXT("");
            FString TargetSpot = Obj->HasField(TEXT("targetSpotCode"))
                ? Obj->GetStringField(TEXT("targetSpotCode")) : TEXT("");

            if (!ReceptionSpot.IsEmpty())
            {
                // Only count as new if not already processed
                if (!ProcessedPackageIds.Contains(PackageId))
                {
                    HandlePackageReceived(PackageId, Sku, Quantity, ReceptionSpot, TargetSpot);
                    VisualCount++;
                }
            }
        }

        if (VisualCount > 0)
        {
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Fetched active packages: %d new packages processed"), VisualCount);
        }
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

    FString ResolvedFrom = FromCode;
    if (ResolvedFrom.IsEmpty() && WarehouseEnv)
    {
        ResolvedFrom = WarehouseEnv->FindNearestRootPointCode(Robot->GetActorLocation());
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' no CurrentLocationCode, resolved from world pos → %s"),
            *Robot->RobotId, *ResolvedFrom);
    }

    if (ResolvedFrom.IsEmpty() || ToCode.IsEmpty())
    {
        UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Robot '%s' missing location code (from=%s, to=%s), using direct move"),
            *Robot->RobotId, *ResolvedFrom, *ToCode);

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

    if (ResolvedFrom == ToCode)
    {
        UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' already at destination '%s'"), *Robot->RobotId, *ToCode);

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

    FString RobotId = Robot->RobotId;
    bool bPickUp = bPickUpAtDestination;

    Request->OnProcessRequestComplete().BindLambda([this, RobotId, bPickUp](FHttpRequestPtr Req, FHttpResponsePtr Resp, bool bSuccess)
    {
        // Safety: skip if shutting down (EndPlay already called)
        if (!bIsBackendConnected) return;

        AWarehouseRobot** Found = RobotActors.Find(RobotId);
        if (!Found || !*Found)
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Route response for robot '%s' but robot no longer exists"), *RobotId);
            return;
        }
        AWarehouseRobot* Robot = *Found;

        if (!bSuccess || !Resp.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Route request failed for robot '%s'"), *RobotId);
            return;
        }

        int32 Code = Resp->GetResponseCode();
        FString Body = Resp->GetContentAsString();

        if (Code != 200)
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Route API returned HTTP %d for robot '%s': %s"),
                Code, *RobotId, *Body);
            return;
        }

        // Parse route response: { "waypoints": [{"x":..,"y":..,"z":..}, ...] }
        TSharedPtr<FJsonObject> RootObj;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Body);

        if (!FJsonSerializer::Deserialize(Reader, RootObj) || !RootObj.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] Failed to parse route JSON for robot '%s'"), *RobotId);
            return;
        }

        TArray<FVector> Waypoints;

        // Try "waypoints" array first
        const TArray<TSharedPtr<FJsonValue>>* WpArray = nullptr;
        if (RootObj->TryGetArrayField(TEXT("waypoints"), WpArray))
        {
            for (const auto& WpVal : *WpArray)
            {
                TSharedPtr<FJsonObject> WpObj = WpVal->AsObject();
                if (!WpObj.IsValid()) continue;

                float X = 0.f, Y = 0.f, Z = 0.f;
                if (WpObj->HasField(TEXT("x"))) X = (float)WpObj->GetNumberField(TEXT("x"));
                if (WpObj->HasField(TEXT("y"))) Y = (float)WpObj->GetNumberField(TEXT("y"));
                if (WpObj->HasField(TEXT("z"))) Z = (float)WpObj->GetNumberField(TEXT("z"));

                FVector RawPos(X, Y, Z);

                // Apply navigation offset: convert raw waypoint to nearest cell,
                // then use CellToNavigationPosition which offsets toward grid interior
                // to prevent the robot from clipping through walls/shelves at cell edges
                if (WarehouseEnv)
                {
                    FString NearestCode = WarehouseEnv->FindNearestRootPointCode(RawPos);
                    if (!NearestCode.IsEmpty())
                    {
                        FVector OffsetPos;
                        if (WarehouseEnv->GetSpotPosition(NearestCode, OffsetPos))
                        {
                            Waypoints.Add(OffsetPos);
                            continue;
                        }
                    }
                }

                Waypoints.Add(RawPos);
            }
        }

        // If we have waypoints, follow them; otherwise direct move to target
        if (Waypoints.Num() > 0)
        {
            Robot->FollowWaypoints(Waypoints);
            UE_LOG(LogTemp, Log, TEXT("[RobotManager] Robot '%s' following %d waypoints"), *RobotId, Waypoints.Num());
        }
        else
        {
            // No waypoints — use the target position from the mission
            UE_LOG(LogTemp, Warning, TEXT("[RobotManager] No waypoints in route for robot '%s', using direct move"), *RobotId);
            if (Robot->bOnMission)
            {
                if (bPickUp)
                {
                    Robot->MoveTo(Robot->ActiveMission.TargetSpotPosition);
                }
                else
                {
                    Robot->MoveTo(Robot->ActiveMission.ReceptionSpotPosition);
                }
            }
        }
    });

    Request->ProcessRequest();
}

void ARobotManager::NotifyRobotStateChanged(AWarehouseRobot* Robot, const FString& NewMode)
{
    if (!Robot || !HttpClient || !HttpClient->IsConnected()) return;

    const FSmartLogisticRobotData& Data = Robot->GetCurrentData();
    FString LocationCode = Robot->CurrentLocationCode.IsEmpty()
        ? Data.CurrentLocation
        : Robot->CurrentLocationCode;

    FString TelemetryJson = FString::Printf(
        TEXT("{\"batteryLevel\":%d,\"currentLocation\":\"%s\",\"operationalMode\":\"%s\"}"),
        Data.BatteryLevel,
        *LocationCode,
        *NewMode
    );

    HttpClient->SendTelemetry(Data.RobotId, TelemetryJson);
    UE_LOG(LogTemp, Log, TEXT("[RobotManager] State change notified: robot=%s → %s at %s"),
        *Data.RobotId, *NewMode, *LocationCode);
}