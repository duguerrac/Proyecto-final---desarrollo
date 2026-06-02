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
    if (bSessionConnected && WebSocket.IsValid())
    {
        UE_LOG(LogTemp, Warning, TEXT("[StompClient] Already connected — skipping"));
        return;
    }

    if (bIsConnecting)
    {
        UE_LOG(LogTemp, Verbose, TEXT("[StompClient] Connection already in progress — skipping"));
        return;
    }

    bIsShuttingDown = false;
    bIsConnecting = true;

    // Clean up any stale/closed WebSocket from a previous session
    if (WebSocket.IsValid())
    {
        UE_LOG(LogTemp, Log, TEXT("[StompClient] Cleaning up stale WebSocket for reconnection"));
        WebSocket->Close();
        WebSocket.Reset();
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

    // Use overload without subprotocols - RabbitMQ Web STOMP works without them
    WebSocket = FWebSocketsModule::Get().CreateWebSocket(Url);

    if (!WebSocket.IsValid())
    {
        UE_LOG(LogTemp, Error, TEXT("[StompClient] Failed to create WebSocket — is the WebSockets plugin enabled in .uproject?"));
        bIsConnecting = false;
        return;
    }

    // Use TWeakObjectPtr to safely check if this UObject is still alive in callbacks.
    // This prevents crashes when PIE stops and GC destroys the StompClient
    // while the WebSocket still has pending async callbacks.
    TWeakObjectPtr<UStompClient> WeakThis(this);

    WebSocket->OnConnected().AddLambda([WeakThis]()
    {
        if (WeakThis.IsValid())
        {
            WeakThis->OnWsConnected();
        }
    });
    WebSocket->OnConnectionError().AddLambda([WeakThis](const FString& Err)
    {
        if (WeakThis.IsValid())
        {
            WeakThis->OnWsConnectionError(Err);
        }
    });
    WebSocket->OnClosed().AddLambda([WeakThis](int32 Code, const FString& Reason, bool bClean)
    {
        if (WeakThis.IsValid())
        {
            WeakThis->OnWsClosed(Code, Reason, bClean);
        }
    });
    WebSocket->OnMessage().AddLambda([WeakThis](const FString& Msg)
    {
        if (WeakThis.IsValid())
        {
            WeakThis->OnWsMessage(Msg);
        }
    });

    UE_LOG(LogTemp, Log, TEXT("[StompClient] Connecting to %s ..."), *Url);
    WebSocket->Connect();
}

void UStompClient::Disconnect()
{
    // Mark as shutting down FIRST to prevent any pending callbacks from accessing state
    bIsShuttingDown = true;
    bSessionConnected = false;
    bIsConnecting = false;

    // Stop heartbeat timer
    if (UWorld* World = GetWorld())
    {
        World->GetTimerManager().ClearTimer(HeartbeatTimerHandle);
    }

    if (!WebSocket.IsValid())
        return;

    // Send DISCONNECT frame with receipt (only if session was established)
    if (bSessionConnected)
    {
        TMap<FString, FString> Headers;
        Headers.Add(TEXT("receipt"), TEXT("disconnect-receipt"));
        SendFrame(TEXT("DISCONNECT"), Headers);
    }

    // Close the WebSocket — the OnClosed callback will fire but
    // bIsShuttingDown prevents it from touching invalid state
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

FString UStompClient::SubscribeToExchange(const FString& ExchangeName, const FString& RoutingKey, const FString& AckMode)
{
    if (!bSessionConnected)
    {
        UE_LOG(LogTemp, Warning, TEXT("[StompClient] Cannot subscribe to exchange — not connected"));
        return TEXT("");
    }

    FString SubId = FString::Printf(TEXT("sub-%d"), SubIdCounter++);

    // RabbitMQ Web STOMP: destination = "/exchange/<exchange>/<routing-key>"
    // This creates a server-generated exclusive queue bound to the exchange with the routing key
    FString Destination = FString::Printf(TEXT("/exchange/%s/%s"), *ExchangeName, *RoutingKey);

    TMap<FString, FString> Headers;
    Headers.Add(TEXT("id"), SubId);
    Headers.Add(TEXT("destination"), Destination);
    Headers.Add(TEXT("ack"), AckMode);

    SendFrame(TEXT("SUBSCRIBE"), Headers);

    Subscriptions.Add(SubId, Destination);
    SavedSubscriptions.AddUnique(Destination);
    UE_LOG(LogTemp, Log, TEXT("[StompClient] Subscribed to exchange %s with key %s (dest=%s, id=%s)"),
        *ExchangeName, *RoutingKey, *Destination, *SubId);

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
        bIsConnecting = false;

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
                // Server silence timeout = 3x the server's send interval (Cx)
                // If server promises to send every Cx ms, we tolerate up to 3*Cx before declaring dead.
                // Using 3x (instead of 2x) to account for network jitter and GC pauses in UE5.
                if (ServerCx > 0)
                {
                    ServerSilenceTimeoutSec = FMath::Max(30.0f, (ServerCx * 3) / 1000.0f);
                }
            }
        }

        // Record connection time as last server activity
        LastServerActivityTime = GWorld ? GWorld->GetTimeSeconds() : 0.0f;

        UE_LOG(LogTemp, Log, TEXT("[StompClient] CONNECTED — session established (heartbeat=%dms, silence_timeout=%.1fs)"),
            HeartbeatIntervalMs, ServerSilenceTimeoutSec);

        // Notify listeners that STOMP session is ready for subscriptions
        OnConnected.Broadcast();

        // Start heartbeat timer — use GetWorld() from outer chain (more reliable than GEngine->GetWorld())
        UWorld* World = GetWorld();
        if (World && HeartbeatIntervalMs > 0)
        {
            World->GetTimerManager().SetTimer(HeartbeatTimerHandle, [this]()
            {
                SendHeartbeat();
            }, HeartbeatIntervalMs / 1000.0f, true);
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[StompClient] Cannot start heartbeat timer — GetWorld()=%s, interval=%dms"),
                World ? TEXT("valid") : TEXT("NULL"), HeartbeatIntervalMs);
        }

        // Auto-resubscribe to saved subscriptions after reconnect
        Subscriptions.Empty();
        for (const FString& Dest : SavedSubscriptions)
        {
            FString SubId = FString::Printf(TEXT("sub-%d"), SubIdCounter++);
            TMap<FString, FString> SubHeaders;
            SubHeaders.Add(TEXT("id"), SubId);
            SubHeaders.Add(TEXT("destination"), Dest);
            SubHeaders.Add(TEXT("ack"), TEXT("auto"));
            SendFrame(TEXT("SUBSCRIBE"), SubHeaders);
            Subscriptions.Add(SubId, Dest);
            UE_LOG(LogTemp, Log, TEXT("[StompClient] Auto-resubscribed to %s (id=%s)"), *Dest, *SubId);
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

        UE_LOG(LogTemp, Log, TEXT("[StompClient] MESSAGE on %s: %s"), *Destination, *Body.Left(200));

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
    if (bIsShuttingDown) return;

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

    // Request heart-beat: we send every 30s, expect server every 30s.
    // Using 30s instead of 10s reduces traffic and avoids Cowboy idle timeout issues
    // (Cowboy's idle timeout is set to 300s in rabbitmq.conf, well above this interval).
    Headers.Add(TEXT("heart-beat"), TEXT("30000,30000"));

    SendFrame(TEXT("CONNECT"), Headers);
}

void UStompClient::OnWsConnectionError(const FString& Error)
{
    if (bIsShuttingDown) return;

    UE_LOG(LogTemp, Error, TEXT("[StompClient] WebSocket connection error: %s"), *Error);
    bSessionConnected = false;
    bIsConnecting = false;
    WebSocket.Reset();
}

void UStompClient::OnWsClosed(int32 StatusCode, const FString& Reason, bool bWasClean)
{
    if (bIsShuttingDown)
    {
        // Expected close during shutdown — just clean up silently
        WebSocket.Reset();
        return;
    }

    UE_LOG(LogTemp, Log, TEXT("[StompClient] WebSocket closed: %d (%s) clean=%s"),
        StatusCode, *Reason, bWasClean ? TEXT("yes") : TEXT("no"));
    bSessionConnected = false;
    bIsConnecting = false;

    if (UWorld* World = GetWorld())
    {
        World->GetTimerManager().ClearTimer(HeartbeatTimerHandle);
    }

    // Reset the WebSocket pointer so Connect() can create a fresh one
    WebSocket.Reset();
    UE_LOG(LogTemp, Log, TEXT("[StompClient] WebSocket pointer reset — ready for reconnection"));
}

void UStompClient::OnWsMessage(const FString& Message)
{
    if (bIsShuttingDown) return;

    // Record server activity for heartbeat monitoring
    if (UWorld* World = GetWorld())
    {
        LastServerActivityTime = World->GetTimeSeconds();
    }

    ParseFrames(Message);
}

void UStompClient::SendHeartbeat()
{
    if (!WebSocket.IsValid() || !bSessionConnected) return;

    // Check if server has been silent for too long (heartbeat monitoring)
    if (ServerSilenceTimeoutSec > 0.0f && LastServerActivityTime > 0.0f)
    {
        if (UWorld* World = GetWorld())
        {
            float Now = World->GetTimeSeconds();
            float SilenceDuration = Now - LastServerActivityTime;
            if (SilenceDuration > ServerSilenceTimeoutSec)
            {
                UE_LOG(LogTemp, Warning, TEXT("[StompClient] Server silent for %.1fs (timeout=%.1fs) — closing connection"),
                    SilenceDuration, ServerSilenceTimeoutSec);
                // Force close the WebSocket so RobotManager's reconnect logic kicks in
                bSessionConnected = false;
                if (UWorld* W = GetWorld())
                {
                    W->GetTimerManager().ClearTimer(HeartbeatTimerHandle);
                }
                WebSocket->Close(4000, TEXT("Heartbeat timeout — server silent"));
                return;
            }
        }
    }

    // STOMP 1.2 heartbeat: send a NULL byte (0x00) as end-of-frame marker.
    // This is the spec-compliant way — RabbitMQ Web STOMP recognizes both \n and \0,
    // but \0 is more reliable across implementations.
    FString HeartbeatFrame;
    HeartbeatFrame.AppendChar(STOMP_NULL);
    WebSocket->Send(HeartbeatFrame);
    UE_LOG(LogTemp, Verbose, TEXT("[StompClient] Heartbeat sent (NULL frame)"));
}

