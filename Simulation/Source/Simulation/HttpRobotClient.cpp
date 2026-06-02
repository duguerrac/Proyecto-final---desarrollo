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
    if (ApiBaseUrl.IsEmpty()) return;

    // Publish mission events to the robot-status backend
    FString Url = FString::Printf(TEXT("%s/api/robots/events"), *ApiBaseUrl);
    FString Body = FString::Printf(TEXT("{\"eventType\":\"%s\",\"payload\":%s}"), *EventType, *JsonPayload);
    MakeRequest(TEXT("POST"), Url, Body, [EventType](int32 Code, const FString& Body)
    {
        if (Code == 200)
        {
            UE_LOG(LogTemp, Log, TEXT("[HttpRobotClient] Event %s published OK"), *EventType);
        }
        else
        {
            UE_LOG(LogTemp, Warning, TEXT("[HttpRobotClient] Event %s FAILED (HTTP %d)"), *EventType, Code);
        }
    });
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

void UHttpRobotClient::SetWarehouseUrl(const FString& InWarehouseUrl)
{
    WarehouseApiUrl = InWarehouseUrl;
    while (WarehouseApiUrl.EndsWith(TEXT("/")))
    {
        WarehouseApiUrl.RemoveAt(WarehouseApiUrl.Len() - 1);
    }
    UE_LOG(LogTemp, Log, TEXT("[HttpRobotClient] Warehouse URL set: %s"), *WarehouseApiUrl);
}

void UHttpRobotClient::PollForCommands()
{
    if (!bIsPolling) return;

    // NOTE: Package reception is handled via STOMP (RabbitMQ) events, NOT HTTP polling.
    // See RobotManager::HandleStompMessage() → package.dispatched
    PollForRobotCommands();
}

void UHttpRobotClient::PollForPendingPackages()
{
    if (WarehouseApiUrl.IsEmpty())
    {
        // Only log once to avoid spam
        static bool bWarnedEmptyUrl = false;
        if (!bWarnedEmptyUrl)
        {
            UE_LOG(LogTemp, Error, TEXT("[HttpRobotClient] WarehouseApiUrl is EMPTY - cannot poll for packages! Set WarehouseApiUrl on RobotManager."));
            bWarnedEmptyUrl = true;
        }
        return;
    }

    // Poll GET /api/packages/status/RECEIVED from warehouse-core
    FString Url = FString::Printf(TEXT("%s/api/packages/status/RECEIVED"), *WarehouseApiUrl);

    MakeRequest(TEXT("GET"), Url, TEXT(""), [this, Url](int32 Code, const FString& Body)
    {
        if (!bIsPolling) return;
        
        if (Code != 200)
        {
            // Log connection issues at Warning level (Code=0 means connection refused)
            UE_LOG(LogTemp, Warning, TEXT("[HttpRobotClient] Package poll FAILED (HTTP %d) from %s"), Code, *Url);
            return;
        }
        if (Body.IsEmpty() || Body == TEXT("[]"))
        {
            UE_LOG(LogTemp, Verbose, TEXT("[HttpRobotClient] Package poll: no RECEIVED packages"));
            return;
        }

        // Parse JSON array of packages
        TArray<TSharedPtr<FJsonValue>> Packages;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Body);
        if (!FJsonSerializer::Deserialize(Reader, Packages))
        {
            UE_LOG(LogTemp, Warning, TEXT("[HttpRobotClient] Failed to parse packages JSON: %s"), *Body.Left(200));
            return;
        }
        if (!Packages.Num())
        {
            return;
        }
        
        UE_LOG(LogTemp, Log, TEXT("[HttpRobotClient] Found %d RECEIVED package(s)"), Packages.Num());

        for (const TSharedPtr<FJsonValue>& PkgVal : Packages)
        {
            TSharedPtr<FJsonObject> Pkg = PkgVal->AsObject();
            if (!Pkg.IsValid()) continue;

            int64 PackageId = Pkg->GetIntegerField(TEXT("id"));
            if (ProcessedPackageIds.Contains(PackageId)) continue;

            // Mark as processed to avoid duplicates
            ProcessedPackageIds.Add(PackageId);

            FString Sku = Pkg->GetStringField(TEXT("sku"));
            int32 Quantity = Pkg->GetIntegerField(TEXT("quantity"));
            FString ReceptionSpot = Pkg->GetStringField(TEXT("receptionSpotCode"));
            FString TargetSpot = Pkg->GetStringField(TEXT("targetSpotCode"));

            UE_LOG(LogTemp, Log, TEXT("[HttpRobotClient] 📦 Package #%lld RECEIVED (SKU=%s) → %s → %s"),
                PackageId, *Sku, *ReceptionSpot, *TargetSpot);

            // Broadcast to RobotManager
            OnPackageReceived.Broadcast(PackageId, Sku, Quantity, ReceptionSpot, TargetSpot);
        }
    });
}

void UHttpRobotClient::PollForRobotCommands()
{
    if (ApiBaseUrl.IsEmpty()) return;

    // Poll all robots to check for assigned missions
    FString Url = FString::Printf(TEXT("%s/api/robots"), *ApiBaseUrl);

    MakeRequest(TEXT("GET"), Url, TEXT(""), [this](int32 Code, const FString& Body)
    {
        if (!bIsPolling) return;
        if (Code != 200 || Body.IsEmpty()) return;

        TArray<TSharedPtr<FJsonValue>> Robots;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Body);
        if (!FJsonSerializer::Deserialize(Reader, Robots)) return;

        for (const TSharedPtr<FJsonValue>& RobotVal : Robots)
        {
            TSharedPtr<FJsonObject> Robot = RobotVal->AsObject();
            if (!Robot.IsValid()) continue;

            FString RobotId = Robot->GetStringField(TEXT("robotId"));
            FString Mode = Robot->GetStringField(TEXT("operationalMode"));
            FString Location = Robot->GetStringField(TEXT("currentLocation"));
            bool bAvailable = Robot->GetBoolField(TEXT("available"));

            // If robot is not available and mode is MISSION, it has an active command
            if (!bAvailable && Mode == TEXT("MISSION"))
            {
                UE_LOG(LogTemp, Verbose, TEXT("[HttpRobotClient] Robot %s on MISSION at %s"), *RobotId, *Location);
                // The mission details are handled via the package flow
            }
        }
    });
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