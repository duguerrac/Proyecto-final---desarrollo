// Copyright Epic Games, Inc. All Rights Reserved.

#include "StompClient.h"
#include "TimerManager.h"
#include "Engine/World.h"
#include "Engine/Engine.h"

// STOMP 1.2 uses \n (LF, 0x0A) as line terminator, NULL (0x00) as body terminator
static const TCHAR STOMP_LF = '\n';
static const TCHAR STOMP_NULL = '\0';
static const TCHAR STOMP_COLON = ':';

UStompClient::UStompClient()
{
}

// ─── Public API ──────────────────────────────────────────────────

void UStompClient::Connect(const FString& Url, const FString& Login, const FString& Passcode)
{
    if (WebSocket.IsValid())
    {
        UE_LOG(LogTemp, Warning, TEXT("[StompClient] Already connected or connecting"));
        return;
    }

    // Ensure WebSockets module is loaded
    if (!FModuleManager::Get().IsModuleLoaded("WebSockets"))
    {
        FModuleManager::Get().LoadModule("WebSockets");
    }

    // Store login/passcode for CONNECT frame (sent after WS open)
    LastLogin = Login;
    LastPasscode = Passcode;
    LastUrl = Url;

    TArray<FString> Protocols;
    Protocols.Add(TEXT("v10.stomp"));
    Protocols.Add(TEXT("v11.stomp"));
    Protocols.Add(TEXT("v12.stomp"));

    WebSocket = FWebSocketsModule::Get().CreateWebSocket(Url, Protocols);

    WebSocket->OnConnected().AddLambda([this]() { OnWsConnected(); });
    WebSocket->OnConnectionError().AddLambda([this](const FString& Err) { OnWsConnectionError(Err); });
    WebSocket->OnClosed().AddLambda([this](int32 Code, const FString& Reason, bool bClean) { OnWsClosed(Code, Reason, bClean); });
    WebSocket->OnMessage().AddLambda([this](const FString& Msg) { OnWsMessage(Msg); });

    UE_LOG(LogTemp, Log, TEXT("[StompClient] Connecting to %s ..."), *Url);
    WebSocket->Connect();
}

void UStompClient::Disconnect()
{
    if (!WebSocket.IsValid())
        return;

    // Send DISCONNECT frame with receipt
    if (bSessionConnected)
    {
        TMap<FString, FString> Headers;
        Headers.Add(TEXT("receipt"), TEXT("disconnect-receipt"));
        SendFrame(TEXT("DISCONNECT"), Headers);
        bSessionConnected = false;
    }

    // Stop heartbeat
    if (GEngine && GEngine->GetWorld() != nullptr)
    {
        GEngine->GetWorld()->GetTimerManager().ClearTimer(HeartbeatTimerHandle);
    }

    WebSocket->Close();
    Subscriptions.Empty();
    UE_LOG(LogTemp, Log, TEXT("[StompClient] Disconnected"));
}

bool UStompClient::IsConnected() const
{
    return bSessionConnected && WebSocket.IsValid();
}

FString UStompClient::Subscribe(const FString& QueueName, const FString& AckMode)
{
    if (!bSessionConnected)
    {
        UE_LOG(LogTemp, Warning, TEXT("[StompClient] Cannot subscribe — not connected"));
        return TEXT("");
    }

    FString SubId = FString::Printf(TEXT("sub-%d"), SubIdCounter++);

    // RabbitMQ Web STOMP: destination = "/queue/<name>" or "/exchange/<exchange>/<routing-key>"
    FString Destination = FString::Printf(TEXT("/queue/%s"), *QueueName);

    TMap<FString, FString> Headers;
    Headers.Add(TEXT("id"), SubId);
    Headers.Add(TEXT("destination"), Destination);
    Headers.Add(TEXT("ack"), AckMode);

    SendFrame(TEXT("SUBSCRIBE"), Headers);

    Subscriptions.Add(SubId, Destination);
    UE_LOG(LogTemp, Log, TEXT("[StompClient] Subscribed to %s (id=%s)"), *Destination, *SubId);

    return SubId;
}

void UStompClient::Unsubscribe(const FString& SubscriptionId)
{
    if (!bSessionConnected) return;

    TMap<FString, FString> Headers;
    Headers.Add(TEXT("id"), SubscriptionId);

    SendFrame(TEXT("UNSUBSCRIBE"), Headers);
    Subscriptions.Remove(SubscriptionId);

    UE_LOG(LogTemp, Log, TEXT("[StompClient] Unsubscribed %s"), *SubscriptionId);
}

void UStompClient::Send(const FString& Exchange, const FString& RoutingKey, const FString& Body)
{
    if (!bSessionConnected)
    {
        UE_LOG(LogTemp, Warning, TEXT("[StompClient] Cannot send — not connected"));
        return;
    }

    // RabbitMQ Web STOMP: destination = "/exchange/<exchange>/<routing-key>"
    FString Destination = FString::Printf(TEXT("/exchange/%s/%s"), *Exchange, *RoutingKey);

    TMap<FString, FString> Headers;
    Headers.Add(TEXT("destination"), Destination);
    Headers.Add(TEXT("content-type"), TEXT("application/json"));

    SendFrame(TEXT("SEND"), Headers, Body);

    UE_LOG(LogTemp, Verbose, TEXT("[StompClient] SEND to %s (%d bytes)"), *Destination, Body.Len());
}

// ─── Frame Building / Parsing ────────────────────────────────────

FString UStompClient::BuildFrame(const FString& Command, const TMap<FString, FString>& Headers, const FString& Body)
{
    FString Frame = Command + STOMP_LF;

    for (const auto& Pair : Headers)
    {
        // STOMP 1.2: escape \ and : in header values
        FString EscapedValue = Pair.Value;
        EscapedValue.ReplaceInline(TEXT("\\"), TEXT("\\\\"));
        EscapedValue.ReplaceInline(TEXT(":"), TEXT("\\c"));
        EscapedValue.ReplaceInline(TEXT("\n"), TEXT("\\n"));
        EscapedValue.ReplaceInline(TEXT("\r"), TEXT("\\r"));

        FString EscapedKey = Pair.Key;
        EscapedKey.ReplaceInline(TEXT("\\"), TEXT("\\\\"));
        EscapedKey.ReplaceInline(TEXT(":"), TEXT("\\c"));

        Frame += EscapedKey + STOMP_COLON + EscapedValue + STOMP_LF;
    }

    Frame += STOMP_LF; // blank line = end of headers
    Frame += Body;
    Frame += STOMP_NULL;

    return Frame;
}

void UStompClient::SendFrame(const FString& Command, const TMap<FString, FString>& Headers, const FString& Body)
{
    if (!WebSocket.IsValid()) return;

    FString Frame = BuildFrame(Command, Headers, Body);

    // IWebSocket::Send() handles UTF-8 conversion internally
    WebSocket->Send(Frame);
}

void UStompClient::ParseFrames(const FString& RawData)
{
    // STOMP frames are separated by NULL bytes
    // We may receive multiple frames in one WebSocket message
    int32 Pos = 0;
    int32 Len = RawData.Len();

    while (Pos < Len)
    {
        // Skip any leading whitespace/newlines (heartbeat keep-alive)
        while (Pos < Len && (RawData[Pos] == '\n' || RawData[Pos] == '\r'))
            Pos++;

        if (Pos >= Len)
            break;

        // Read command (first line)
        FString Command;
        while (Pos < Len && RawData[Pos] != '\n' && RawData[Pos] != '\r')
            Command += RawData[Pos++];

        // Skip newline
        while (Pos < Len && (RawData[Pos] == '\n' || RawData[Pos] == '\r'))
            Pos++;

        if (Command.IsEmpty())
            continue;

        // Read headers until blank line
        TMap<FString, FString> Headers;
        while (Pos < Len)
        {
            FString Line;
            while (Pos < Len && RawData[Pos] != '\n' && RawData[Pos] != '\r')
                Line += RawData[Pos++];

            // Skip newline
            while (Pos < Len && (RawData[Pos] == '\n' || RawData[Pos] == '\r'))
                Pos++;

            if (Line.IsEmpty())
                break; // blank line = end of headers

            int32 ColonPos;
            if (Line.FindChar(':', ColonPos))
            {
                FString Key = Line.Left(ColonPos);
                FString Value = Line.Right(Line.Len() - ColonPos - 1);

                // Unescape STOMP 1.2 escapes
                Value.ReplaceInline(TEXT("\\c"), TEXT(":"));
                Value.ReplaceInline(TEXT("\\\\"), TEXT("\\"));
                Value.ReplaceInline(TEXT("\\n"), TEXT("\n"));
                Value.ReplaceInline(TEXT("\\r"), TEXT("\r"));

                Headers.Add(Key, Value);
            }
        }

        // Read body until NULL character
        FString Body;
        while (Pos < Len && RawData[Pos] != '\0')
            Body += RawData[Pos++];

        // Skip the NULL terminator
        if (Pos < Len && RawData[Pos] == '\0')
            Pos++;

        HandleFrame(Command, Headers, Body);
    }
}

void UStompClient::HandleFrame(const FString& Command, const TMap<FString, FString>& Headers, const FString& Body)
{
    if (Command == TEXT("CONNECTED"))
    {
        bSessionConnected = true;

        // Parse heart-beat from server
        const FString* Heartbeat = Headers.Find(TEXT("heart-beat"));
        if (Heartbeat)
        {
            TArray<FString> Parts;
            Heartbeat->ParseIntoArray(Parts, TEXT(","), true);
            if (Parts.Num() >= 2)
            {
                int32 ServerCx = FCString::Atoi(*Parts[0]);
                int32 ServerCy = FCString::Atoi(*Parts[1]);
                // We send heartbeats if server expects them (Cy > 0)
                if (ServerCy > 0)
                {
                    HeartbeatIntervalMs = ServerCy;
                }
            }
        }

        UE_LOG(LogTemp, Log, TEXT("[StompClient] CONNECTED — session established (heartbeat=%dms)"), HeartbeatIntervalMs);

        // Start heartbeat timer
        UWorld* World = GEngine ? GEngine->GetWorld() : nullptr;
        if (World && HeartbeatIntervalMs > 0)
        {
            World->GetTimerManager().SetTimer(HeartbeatTimerHandle, [this]()
            {
                SendHeartbeat();
            }, HeartbeatIntervalMs / 1000.0f, true);
        }
    }
    else if (Command == TEXT("MESSAGE"))
    {
        const FString* Dest = Headers.Find(TEXT("destination"));
        FString Destination = Dest ? *Dest : TEXT("");

        // Remove "/queue/" prefix for cleaner routing
        FString CleanDest = Destination;
        CleanDest.RemoveFromStart(TEXT("/queue/"));
        CleanDest.RemoveFromStart(TEXT("/exchange/"));

        UE_LOG(LogTemp, Verbose, TEXT("[StompClient] MESSAGE on %s: %s"), *Destination, *Body.Left(200));

        OnMessageReceived.Broadcast(CleanDest, Body);
    }
    else if (Command == TEXT("RECEIPT"))
    {
        UE_LOG(LogTemp, Verbose, TEXT("[StompClient] RECEIPT received"));
    }
    else if (Command == TEXT("ERROR"))
    {
        const FString* Msg = Headers.Find(TEXT("message"));
        UE_LOG(LogTemp, Error, TEXT("[StompClient] ERROR: %s — %s"),
            Msg ? **Msg : TEXT("unknown"), *Body.Left(500));
    }
    else
    {
        UE_LOG(LogTemp, Verbose, TEXT("[StompClient] Received frame: %s"), *Command);
    }
}

// ─── WebSocket Event Handlers ────────────────────────────────────

void UStompClient::OnWsConnected()
{
    UE_LOG(LogTemp, Log, TEXT("[StompClient] WebSocket connected, sending STOMP CONNECT..."));

    // Now send the STOMP CONNECT frame
    TMap<FString, FString> Headers;
    Headers.Add(TEXT("accept-version"), TEXT("1.2"));
    Headers.Add(TEXT("host"), TEXT("/"));

    if (!LastLogin.IsEmpty())
    {
        Headers.Add(TEXT("login"), LastLogin);
        Headers.Add(TEXT("passcode"), LastPasscode);
    }

    // Request heart-beat: we send every 10s, expect server every 10s
    Headers.Add(TEXT("heart-beat"), TEXT("10000,10000"));

    SendFrame(TEXT("CONNECT"), Headers);
}

void UStompClient::OnWsConnectionError(const FString& Error)
{
    UE_LOG(LogTemp, Error, TEXT("[StompClient] WebSocket connection error: %s"), *Error);
    bSessionConnected = false;
}

void UStompClient::OnWsClosed(int32 StatusCode, const FString& Reason, bool bWasClean)
{
    UE_LOG(LogTemp, Log, TEXT("[StompClient] WebSocket closed: %d (%s) clean=%s"),
        StatusCode, *Reason, bWasClean ? TEXT("yes") : TEXT("no"));
    bSessionConnected = false;

    if (GEngine && GEngine->GetWorld())
    {
        GEngine->GetWorld()->GetTimerManager().ClearTimer(HeartbeatTimerHandle);
    }
}

void UStompClient::OnWsMessage(const FString& Message)
{
    ParseFrames(Message);
}

void UStompClient::SendHeartbeat()
{
    if (!WebSocket.IsValid() || !bSessionConnected) return;

    // STOMP heartbeat is just a newline
    WebSocket->Send(TEXT("\n"));
}

