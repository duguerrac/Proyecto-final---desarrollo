// Copyright Epic Games, Inc. All Rights Reserved.

#include "NatsWebSocketClient.h"
#include "Serialization/JsonSerializer.h"
#include "Dom/JsonObject.h"
#include "Sockets.h"
#include "SocketSubsystem.h"
#include "Interfaces/IPv4/IPv4Address.h"
#include "TimerManager.h"

UNatsWebSocketClient::UNatsWebSocketClient()
{
}

void UNatsWebSocketClient::Connect(const FString& ServerUrl)
{
    UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] Connecting to %s..."), *ServerUrl);

    FString Host = ServerUrl;
    int32 Port = 4222;
    int32 ColonPos;
    if (Host.FindChar(':', ColonPos))
    {
        FString PortStr = Host.RightChop(ColonPos + 1);
        Host = Host.Left(ColonPos);
        Port = FCString::Atoi(*PortStr);
    }

    ISocketSubsystem* SocketSub = ISocketSubsystem::Get(PLATFORM_SOCKETSUBSYSTEM);
    if (!SocketSub)
    {
        UE_LOG(LogTemp, Error, TEXT("[NATS-TCP] No socket subsystem!"));
        return;
    }

    TSharedRef<FInternetAddr> Addr = SocketSub->CreateInternetAddr();
    bool bIsValid;
    Addr->SetIp(*Host, bIsValid);
    if (!bIsValid)
    {
        UE_LOG(LogTemp, Error, TEXT("[NATS-TCP] Cannot resolve: %s"), *Host);
        return;
    }
    Addr->SetPort(Port);

    NatsSocket = SocketSub->CreateSocket(NAME_Stream, TEXT("NatsTCP"), false);
    if (!NatsSocket)
    {
        UE_LOG(LogTemp, Error, TEXT("[NATS-TCP] Failed to create socket!"));
        return;
    }

    if (!NatsSocket->Connect(*Addr))
    {
        UE_LOG(LogTemp, Error, TEXT("[NATS-TCP] Connect failed to %s:%d"), *Host, Port);
        SocketSub->DestroySocket(NatsSocket);
        NatsSocket = nullptr;
        return;
    }

    NatsSocket->SetNonBlocking(true);
    UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] Socket connected, waiting for INFO..."));

    if (UWorld* World = GetWorld())
    {
        FTimerDelegate TimerDel;
        TimerDel.BindUObject(this, &UNatsWebSocketClient::TickReadSocket);
        World->GetTimerManager().SetTimer(TickTimerHandle, TimerDel, 0.05f, true);
    }
}

void UNatsWebSocketClient::Disconnect()
{
    if (UWorld* World = GetWorld())
    {
        World->GetTimerManager().ClearTimer(TickTimerHandle);
    }

    if (NatsSocket)
    {
        ISocketSubsystem* SocketSub = ISocketSubsystem::Get(PLATFORM_SOCKETSUBSYSTEM);
        if (SocketSub) SocketSub->DestroySocket(NatsSocket);
        NatsSocket = nullptr;
    }

    bNatsConnected = false;
    RxBuffer.Empty();
    PendingMsgHeader.Empty();
    PendingMsgSubject.Empty();
    UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] Disconnected"));
}

bool UNatsWebSocketClient::IsConnected() const
{
    return bNatsConnected;
}

bool UNatsWebSocketClient::Publish(const FString& Subject, const FString& JsonPayload)
{
    if (!NatsSocket || !bNatsConnected)
    {
        UE_LOG(LogTemp, Warning, TEXT("[NATS-TCP] Cannot publish - not connected"));
        return false;
    }

    FString PubCmd = FString::Printf(TEXT("PUB %s %d\r\n%s\r\n"),
        *Subject, JsonPayload.Len(), *JsonPayload);
    bool bSuccess = SendString(PubCmd);

    if (bSuccess)
    {
        UE_LOG(LogTemp, Verbose, TEXT("[NATS-TCP] Published to '%s' (%d bytes)"), *Subject, JsonPayload.Len());
    }
    else
    {
        UE_LOG(LogTemp, Error, TEXT("[NATS-TCP] Failed to publish to '%s'"), *Subject);
    }

    return bSuccess;
}

void UNatsWebSocketClient::TickReadSocket()
{
    if (!NatsSocket) return;

    uint32 PendingDataSize = 0;
    while (NatsSocket->HasPendingData(PendingDataSize) && PendingDataSize > 0)
    {
        int32 BufSize = FMath::Min(PendingDataSize, 65536u);
        TArray<uint8> Buffer;
        Buffer.SetNumUninitialized(BufSize + 1);

        int32 BytesRead = 0;
        if (NatsSocket->Recv(Buffer.GetData(), BufSize, BytesRead))
        {
            if (BytesRead > 0)
            {
                Buffer[BytesRead] = 0;
                RxBuffer += ANSI_TO_TCHAR(reinterpret_cast<ANSICHAR*>(Buffer.GetData()));
            }
        }
        else
        {
            return;
        }
    }

    while (true)
    {
        int32 NewLineIdx;
        if (!RxBuffer.FindChar('\n', NewLineIdx)) break;

        FString Line = RxBuffer.Left(NewLineIdx);
        RxBuffer = RxBuffer.RightChop(NewLineIdx + 1);
        Line.TrimEndInline();

        if (!Line.IsEmpty())
        {
            ProcessLine(Line);
        }
    }
}

void UNatsWebSocketClient::ProcessLine(const FString& Line)
{
    UE_LOG(LogTemp, Verbose, TEXT("[NATS-TCP] << %s"), *Line);

    if (Line.StartsWith(TEXT("INFO")))
    {
        UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] INFO received"));
        SendNatsConnect();
        SendNatsSubscribe(TEXT("smartlogistic.robot.status.>"));
        SendNatsSubscribe(TEXT("smartlogistic.robot.command.>"));
        SendNatsSubscribe(TEXT("smartlogistic.package.>"));
    }
    else if (Line.StartsWith(TEXT("+OK")))
    {
        UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] +OK"));
        bNatsConnected = true;
    }
    else if (Line.StartsWith(TEXT("-ERR")))
    {
        UE_LOG(LogTemp, Error, TEXT("[NATS-TCP] Error: %s"), *Line);
    }
    else if (Line.StartsWith(TEXT("MSG")))
    {
        PendingMsgHeader = Line;
        // Extract subject: MSG <subject> <sid> [reply-to] <size>
        TArray<FString> MsgParts;
        Line.ParseIntoArrayWS(MsgParts);
        PendingMsgSubject = MsgParts.Num() >= 2 ? MsgParts[1] : TEXT("");
    }
    else if (Line.StartsWith(TEXT("PING")))
    {
        SendString(TEXT("PONG\r\n"));
    }
    else if (!PendingMsgHeader.IsEmpty())
    {
        FString JsonPayload = Line;
        FString Subject = PendingMsgSubject;
        PendingMsgHeader.Empty();
        PendingMsgSubject.Empty();

        TSharedPtr<FJsonObject> JsonObject;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(JsonPayload);

        if (!FJsonSerializer::Deserialize(Reader, JsonObject) || !JsonObject.IsValid())
        {
            UE_LOG(LogTemp, Warning, TEXT("[NATS-TCP] JSON parse failed on subject '%s': %s"), *Subject, *JsonPayload.Left(200));
            return;
        }

        // ── Route by NATS subject ──────────────────────────────────

        if (Subject == TEXT("smartlogistic.package.received"))
        {
            // From PackageController: {packageId, sku, itemId, quantity, receptionSpotCode, targetSpotCode}
            int64 PackageId = (int64)JsonObject->GetNumberField(TEXT("packageId"));
            FString Sku = JsonObject->HasField(TEXT("sku")) ? JsonObject->GetStringField(TEXT("sku")) : TEXT("");
            int32 Quantity = JsonObject->HasField(TEXT("quantity")) ? JsonObject->GetIntegerField(TEXT("quantity")) : 1;
            FString ReceptionSpot = JsonObject->HasField(TEXT("receptionSpotCode")) ? JsonObject->GetStringField(TEXT("receptionSpotCode")) : TEXT("");
            FString TargetSpot = JsonObject->HasField(TEXT("targetSpotCode")) ? JsonObject->GetStringField(TEXT("targetSpotCode")) : TEXT("");

            OnPackageReceived.Broadcast(PackageId, Sku, Quantity, ReceptionSpot, TargetSpot);
            UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] PackageReceived: pkg=%lld sku=%s qty=%d reception=%s target=%s"),
                PackageId, *Sku, Quantity, *ReceptionSpot, *TargetSpot);
        }
        else if (Subject.StartsWith(TEXT("smartlogistic.robot.command")))
        {
            // Could be from robot-status ms (has "event":"ROBOT_COMMAND") or from PackageDispatchService (has "missionType")
            if (JsonObject->HasField(TEXT("missionType")) || JsonObject->HasField(TEXT("mission")))
            {
                // STOCK_IN dispatch from PackageDispatchService
                FString RobotId = JsonObject->GetStringField(TEXT("robotId"));
                FString Mission = JsonObject->HasField(TEXT("missionType"))
                    ? JsonObject->GetStringField(TEXT("missionType"))
                    : JsonObject->GetStringField(TEXT("mission"));
                int64 PackageId = 0;
                if (JsonObject->HasField(TEXT("packageId")))
                    PackageId = (int64)JsonObject->GetNumberField(TEXT("packageId"));
                FString ReceptionSpot = JsonObject->HasField(TEXT("receptionSpotCode")) ? JsonObject->GetStringField(TEXT("receptionSpotCode")) : TEXT("");
                FString TargetSpot = JsonObject->HasField(TEXT("targetSpotCode")) ? JsonObject->GetStringField(TEXT("targetSpotCode")) : TEXT("");
                FString ItemSku = JsonObject->HasField(TEXT("sku")) ? JsonObject->GetStringField(TEXT("sku")) : TEXT("");
                int32 Quantity = JsonObject->HasField(TEXT("quantity")) ? JsonObject->GetIntegerField(TEXT("quantity")) : 0;

                OnPackageMissionReceived.Broadcast(RobotId, PackageId, Mission,
                    ReceptionSpot, TargetSpot, ItemSku, Quantity);
                UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] PackageMission: %s -> pkg=%lld mission=%s from=%s to=%s"),
                    *RobotId, PackageId, *Mission, *ReceptionSpot, *TargetSpot);
            }
            else if (JsonObject->HasField(TEXT("event")) && JsonObject->GetStringField(TEXT("event")) == TEXT("ROBOT_COMMAND"))
            {
                // Original command format
                FString RobotId = JsonObject->GetStringField(TEXT("robotId"));
                FString CmdType = JsonObject->GetStringField(TEXT("commandType"));
                FString TargetLoc;
                if (JsonObject->HasField(TEXT("targetLocation")))
                    TargetLoc = JsonObject->GetStringField(TEXT("targetLocation"));
                OnRobotCommandReceived.Broadcast(RobotId, CmdType, TargetLoc);
                UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] Command: %s -> %s (%s)"), *RobotId, *CmdType, *TargetLoc);
            }
            else
            {
                UE_LOG(LogTemp, Warning, TEXT("[NATS-TCP] Unknown robot.command format: %s"), *JsonPayload.Left(200));
            }
        }
        else if (Subject.StartsWith(TEXT("smartlogistic.robot.status")))
        {
            // Robot status events (have "event" field)
            FString EventType = JsonObject->HasField(TEXT("event")) ? JsonObject->GetStringField(TEXT("event")) : TEXT("");

            if (EventType == TEXT("STATUS_UPDATE"))
            {
                const TSharedPtr<FJsonObject>* RobotObj;
                if (JsonObject->TryGetObjectField(TEXT("robot"), RobotObj))
                {
                    FSmartLogisticRobotData RobotData = ParseRobotJson(*RobotObj);
                    OnRobotStatusReceived.Broadcast(RobotData);
                    UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] Robot: %s (%s)"), *RobotData.RobotId, *RobotData.RobotName);
                }
            }
            else if (EventType == TEXT("STATUS_BATCH"))
            {
                const TArray<TSharedPtr<FJsonValue>>* RobotsArray;
                if (JsonObject->TryGetArrayField(TEXT("robots"), RobotsArray))
                {
                    for (const auto& Item : *RobotsArray)
                    {
                        TSharedPtr<FJsonObject> RobotObj = Item->AsObject();
                        if (RobotObj.IsValid())
                        {
                            FSmartLogisticRobotData RobotData = ParseRobotJson(RobotObj);
                            OnRobotStatusReceived.Broadcast(RobotData);
                            UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] Batch Robot: %s (%s)"), *RobotData.RobotId, *RobotData.RobotName);
                        }
                    }
                }
            }
            else
            {
                UE_LOG(LogTemp, Warning, TEXT("[NATS-TCP] Unknown status event '%s': %s"), *EventType, *JsonPayload.Left(200));
            }
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[NATS-TCP] Unhandled subject '%s': %s"), *Subject, *JsonPayload.Left(200));
        }
    }
}

void UNatsWebSocketClient::SendNatsConnect()
{
    FString Json = TEXT("{\"verbose\":false,\"pedantic\":false,\"name\":\"UE5-Simulation\"}");
    SendString(FString::Printf(TEXT("CONNECT %s\r\n"), *Json));
    UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] Sent CONNECT"));
}

void UNatsWebSocketClient::SendNatsSubscribe(const FString& Subject)
{
    int32 Sid = NextSubId++;
    SendString(FString::Printf(TEXT("SUB %s %d\r\n"), *Subject, Sid));
    UE_LOG(LogTemp, Log, TEXT("[NATS-TCP] Subscribed to '%s' (sid=%d)"), *Subject, Sid);
}

bool UNatsWebSocketClient::SendString(const FString& Str)
{
    if (!NatsSocket) return false;
    FTCHARToUTF8 Convert(*Str);
    int32 BytesSent = 0;
    return NatsSocket->Send(reinterpret_cast<const uint8*>(Convert.Get()), Convert.Length(), BytesSent);
}

FSmartLogisticRobotData UNatsWebSocketClient::ParseRobotJson(const TSharedPtr<FJsonObject>& Json)
{
    FSmartLogisticRobotData Data;
    Data.RobotId = Json->GetStringField(TEXT("id"));
    Data.RobotName = Json->GetStringField(TEXT("name"));
    Data.BatteryLevel = Json->GetIntegerField(TEXT("batteryLevel"));
    Data.bAvailable = Json->GetBoolField(TEXT("available"));
    if (Json->HasField(TEXT("currentLocation")))
        Data.CurrentLocation = Json->GetStringField(TEXT("currentLocation"));
    if (Json->HasField(TEXT("operationalMode")))
        Data.OperationalMode = ParseOperationalMode(Json->GetStringField(TEXT("operationalMode")));
    if (Json->HasField(TEXT("timestamp")))
        Data.EventTimestamp = Json->GetStringField(TEXT("timestamp"));
    return Data;
}

ERobotOperationalMode UNatsWebSocketClient::ParseOperationalMode(const FString& Mode)
{
    if (Mode == TEXT("MOVING"))   return ERobotOperationalMode::MOVING;
    if (Mode == TEXT("PICKING"))  return ERobotOperationalMode::PICKING;
    if (Mode == TEXT("CHARGING")) return ERobotOperationalMode::CHARGING;
    if (Mode == TEXT("OFFLINE"))  return ERobotOperationalMode::OFFLINE;
    return ERobotOperationalMode::IDLE;
}