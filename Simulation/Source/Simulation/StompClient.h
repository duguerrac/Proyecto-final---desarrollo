// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "WebSocketsModule.h"
#include "IWebSocket.h"
#include "StompClient.generated.h"

/** Delegate broadcast when a STOMP message arrives on a subscribed destination */
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnStompMessage, const FString&, Destination, const FString&, Body);

/**
 * Minimal STOMP 1.2 client over WebSocket.
 * Connects to RabbitMQ Web STOMP plugin (ws://host:15674/ws).
 * Supports SUBSCRIBE to queues and SEND to exchanges with routing keys.
 */
UCLASS(BlueprintType, Category = "SmartLogistics")
class UStompClient : public UObject
{
    GENERATED_BODY()

public:
    UStompClient();

    /** Connect to a STOMP-over-WebSocket endpoint (e.g. ws://localhost:15674/ws) */
    void Connect(const FString& Url, const FString& Login = TEXT(""), const FString& Passcode = TEXT(""));

    /** Disconnect gracefully (SEND DISCONNECT frame) */
    void Disconnect();

    /** Is the client connected and session established? */
    bool IsConnected() const;

    /** Subscribe to a RabbitMQ queue. Returns subscription ID. */
    FString Subscribe(const FString& QueueName, const FString& AckMode = TEXT("auto"));

    /** Subscribe to a RabbitMQ exchange with a routing key (topic). Returns subscription ID. */
    FString SubscribeToExchange(const FString& ExchangeName, const FString& RoutingKey, const FString& AckMode = TEXT("auto"));

    /** Unsubscribe a previous subscription by ID */
    void Unsubscribe(const FString& SubscriptionId);

    /** Send a message to an exchange with a routing key */
    void Send(const FString& Exchange, const FString& RoutingKey, const FString& Body);

    /** Delegate: message received on any subscribed destination */
    UPROPERTY(BlueprintAssignable, Category = "SmartLogistics")
    FOnStompMessage OnMessageReceived;

private:
    /** Underlying WebSocket connection */
    TSharedPtr<IWebSocket> WebSocket;

    /** Session established flag */
    bool bSessionConnected = false;

    /** Connection in-progress flag (prevents double-connect) */
    bool bIsConnecting = false;

    /** Timestamp when connection attempt started (for timeout) */
    float ConnectStartTime = 0.0f;

    /** Auto-incrementing subscription counter */
    int32 SubIdCounter = 0;

    /** Map subscription ID → destination for routing messages */
    TMap<FString, FString> Subscriptions;

    /** Saved subscription destinations for auto-resubscribe after reconnect */
    TArray<FString> SavedSubscriptions;

    /** Stored login for STOMP CONNECT (sent after WS open) */
    FString LastLogin;

    /** Stored passcode for STOMP CONNECT */
    FString LastPasscode;

    /** Stored URL for reconnection */
    FString LastUrl;

    /** Heartbeat timer handle */
    FTimerHandle HeartbeatTimerHandle;

    /** Heartbeat interval (ms) negotiated with server */
    int32 HeartbeatIntervalMs = 10000;

    /** Build a STOMP frame string */
    static FString BuildFrame(const FString& Command, const TMap<FString, FString>& Headers, const FString& Body = TEXT(""));

    /** Send a raw STOMP frame over the WebSocket */
    void SendFrame(const FString& Command, const TMap<FString, FString>& Headers, const FString& Body = TEXT(""));

    /** Parse incoming STOMP frames (may be multiple per WebSocket message) */
    void ParseFrames(const FString& RawData);

    /** Handle a single parsed STOMP frame */
    void HandleFrame(const FString& Command, const TMap<FString, FString>& Headers, const FString& Body);

    /** WebSocket event handlers */
    void OnWsConnected();
    void OnWsConnectionError(const FString& Error);
    void OnWsClosed(int32 StatusCode, const FString& Reason, bool bWasClean);
    void OnWsMessage(const FString& Message);

    /** Send heartbeat ping (NULL frame) */
    void SendHeartbeat();
};