// Copyright Epic Games, Inc. All Rights Reserved.

#include "HttpRobotClient.h"
#include "HttpModule.h"
#include "Interfaces/IHttpRequest.h"
#include "Interfaces/IHttpResponse.h"
#include "Serialization/JsonSerializer.h"
#include "Dom/JsonObject.h"
#include "TimerManager.h"
#include "Engine/World.h"

UHttpRobotClient::UHttpRobotClient()
{
    bIsPolling = false;
    PollInterval = 1.0f;
}

void UHttpRobotClient::Initialize(const FString& InApiBaseUrl)
{
    ApiBaseUrl = InApiBaseUrl;
    // Remove trailing slash
    while (ApiBaseUrl.EndsWith(TEXT("/")))
    {
        ApiBaseUrl.RemoveAt(ApiBaseUrl.Len() - 1);
    }
    UE_LOG(LogTemp, Log, TEXT("[HttpRobotClient] Initialized with API URL: %s"), *ApiBaseUrl);
}

void UHttpRobotClient::StartPolling()
{
    if (bIsPolling) return;

    bIsPolling = true;
    UE_LOG(LogTemp, Log, TEXT("[HttpRobotClient] Starting command polling (interval=%.1fs)"), PollInterval);

    // Use a timer on the outer object (we need a UWorld for timer manager)
    // The RobotManager will drive polling via its own Tick instead
    // This keeps it simpler — RobotManager calls PollForCommands() each tick
}

void UHttpRobotClient::Disconnect()
{
    bIsPolling = false;
    UE_LOG(LogTemp, Log, TEXT("[HttpRobotClient] Disconnected"));
}

bool UHttpRobotClient::IsConnected() const
{
    return bIsPolling && !ApiBaseUrl.IsEmpty();
}

void UHttpRobotClient::SendTelemetry(const FString& RobotId, const FString& JsonPayload)
{
    if (ApiBaseUrl.IsEmpty()) return;

    FString Url = FString::Printf(TEXT("%s/api/robots/%s/telemetry"), *ApiBaseUrl, *RobotId);
    MakeRequest(TEXT("PUT"), Url, JsonPayload, [RobotId](int32 Code, const FString& Body)
    {
        if (Code == 200)
        {
            UE_LOG(LogTemp, Verbose, TEXT("[HttpRobotClient] Telemetry OK for robot %s"), *RobotId);
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[HttpRobotClient] Telemetry FAILED for robot %s (HTTP %d)"), *RobotId, Code);
        }
    });
}

void UHttpRobotClient::SendTelemetryBatch(const FString& JsonBatchPayload)
{
    // The backend doesn't have a batch endpoint yet — send individually
    // For now, this is a no-op. Individual telemetry is sent per-robot.
    UE_LOG(LogTemp, Verbose, TEXT("[HttpRobotClient] Batch telemetry: %s"), *JsonBatchPayload.Left(200));
}

void UHttpRobotClient::PublishEvent(const FString& EventType, const FString& JsonPayload)
{
    // Events are now published via the REST API or RabbitMQ by the backend.
    // The simulation publishes telemetry → backend publishes to RabbitMQ.
    // This is kept for backward compat but logs a note.
    UE_LOG(LogTemp, Verbose, TEXT("[HttpRobotClient] PublishEvent: %s -> %s"), *EventType, *JsonPayload.Left(200));
}

void UHttpRobotClient::NotifyRouteComplete(const FString& RobotId)
{
    if (ApiBaseUrl.IsEmpty()) return;

    FString Url = FString::Printf(TEXT("%s/api/robots/%s/route-complete"), *ApiBaseUrl, *RobotId);
    MakeRequest(TEXT("POST"), Url, TEXT("{}"), [RobotId](int32 Code, const FString& Body)
    {
        if (Code == 200)
        {
            UE_LOG(LogTemp, Log, TEXT("[HttpRobotClient] Route complete notified for robot %s"), *RobotId);
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[HttpRobotClient] Route complete FAILED for robot %s (HTTP %d)"), *RobotId, Code);
        }
    });
}

void UHttpRobotClient::PollForCommands()
{
    // Currently the backend dispatches commands via RabbitMQ subscribers.
    // The simulation doesn't need to poll — missions arrive via the RabbitMQ 
    // subscribers in the Java backend which then get exposed via REST events.
    // 
    // Future: Add SSE or WebSocket endpoint in ms-robot-status for real-time commands.
    // For now, mission dispatching is handled by the backend consuming from RabbitMQ
    // and the simulation picks up state changes via telemetry polling.
}

void UHttpRobotClient::MakeRequest(const FString& Method, const FString& Url, const FString& Body,
    TFunction<void(int32, const FString&)> Callback)
{
    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> Request = FHttpModule::Get().CreateRequest();
    Request->SetURL(Url);
    Request->SetVerb(Method);
    Request->SetHeader(TEXT("Content-Type"), TEXT("application/json"));
    Request->SetTimeout(5.0f);

    if (!Body.IsEmpty())
    {
        Request->SetContentAsString(Body);
    }

    Request->OnProcessRequestComplete().BindLambda([Callback](FHttpRequestPtr Req, FHttpResponsePtr Resp, bool bSuccess)
    {
        if (!bSuccess || !Resp.IsValid())
        {
            Callback(0, TEXT(""));
            return;
        }
        Callback(Resp->GetResponseCode(), Resp->GetContentAsString());
    });

    Request->ProcessRequest();
}