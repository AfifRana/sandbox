$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$projectName = "weather-watch-e2e-$PID-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$environmentNames = @(
    "APP_PORT",
    "E2E_STUB_PORT",
    "OPEN_METEO_BASE_URL",
    "WEATHER_BATCH_FIXED_DELAY",
    "WEATHER_BATCH_GRID_SIZE",
    "WEATHER_BATCH_MAX_WORKERS",
    "WEATHER_BATCH_CHUNK_SIZE",
    "WEATHER_BATCH_RETRY_LIMIT",
    "WEATHER_BATCH_ENABLED",
    "FORECAST_CACHE_TTL",
    "OPEN_METEO_READ_TIMEOUT"
)
$originalEnvironment = @{}
foreach ($name in $environmentNames) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name)
}
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$appPort = $listener.LocalEndpoint.Port
$stubListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$stubListener.Start()
$stubPort = $stubListener.LocalEndpoint.Port
$listener.Stop()
$stubListener.Stop()
$appBaseUrl = "http://127.0.0.1:$appPort"
$stubBaseUrl = "http://127.0.0.1:$stubPort"
$failure = $null

$env:APP_PORT = "$appPort"
$env:E2E_STUB_PORT = "$stubPort"
$env:OPEN_METEO_BASE_URL = "http://open-meteo-stub:8080"
$env:WEATHER_BATCH_FIXED_DELAY = "PT30S"
$env:WEATHER_BATCH_GRID_SIZE = "3"
$env:WEATHER_BATCH_MAX_WORKERS = "2"
$env:WEATHER_BATCH_CHUNK_SIZE = "2"
$env:WEATHER_BATCH_RETRY_LIMIT = "2"
$env:WEATHER_BATCH_ENABLED = "true"
$env:FORECAST_CACHE_TTL = "PT10S"
$env:OPEN_METEO_READ_TIMEOUT = "2s"

function Invoke-Compose {
    param([string[]]$ComposeArguments)

    Push-Location $repositoryRoot
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & docker compose --project-name $projectName --profile e2e @ComposeArguments 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose $($ComposeArguments -join ' ') failed with exit code $LASTEXITCODE"
        }
        return ($output -join [Environment]::NewLine).Trim()
    }
    finally {
        $ErrorActionPreference = $previousPreference
        Pop-Location
    }
}

function Wait-For {
    param(
        [scriptblock]$Condition,
        [string]$Description,
        [int]$TimeoutSeconds = 120
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (& $Condition) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for $Description"
}

function Get-ProviderCallCount {
    $result = Invoke-RestMethod -Uri "$stubBaseUrl/__admin/requests" -TimeoutSec 5
    return @($result.requests | Where-Object { $_.request.url -like "/v1/forecast*" }).Count
}

Add-Type -AssemblyName System.Net.Http
$httpClient = [System.Net.Http.HttpClient]::new()
$httpClient.Timeout = [TimeSpan]::FromSeconds(90)
$results = [System.Collections.Generic.List[object]]::new()
$runId = [guid]::NewGuid().ToString('N').Substring(0, 6)

function Invoke-Api {
    param([string]$Method, [string]$Url, $Body = $null)

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 10 }
        $request.Content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, "application/json")
    }
    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $parsed = $null
    if ($raw) {
        try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $null }
    }
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Raw = $raw; Json = $parsed }
}

function Assert-That {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function New-Location {
    param([string]$Name, [double]$Latitude, [double]$Longitude = 106.8)
    $response = Invoke-Api POST "$appBaseUrl/api/locations" @{ name = $Name; latitude = $Latitude; longitude = $Longitude }
    Assert-That ($response.Status -eq 201) "Create '$Name' expected 201 but got $($response.Status): $($response.Raw)"
    return [long]$response.Json.id
}

function Get-LatitudeCalls {
    param([string]$Latitude)
    $body = @{ method = "GET"; urlPath = "/v1/forecast"; queryParameters = @{ latitude = @{ equalTo = $Latitude } } }
    $count = Invoke-Api POST "$stubBaseUrl/__admin/requests/count" $body
    return [int]$count.Json.count
}

function Add-StubMapping {
    param($Mapping)
    $response = Invoke-Api POST "$stubBaseUrl/__admin/mappings" $Mapping
    Assert-That ($response.Status -eq 201) "Stub mapping was not created: $($response.Raw)"
}

function Reset-Stub {
    Invoke-Api POST "$stubBaseUrl/__admin/mappings/reset" | Out-Null
}

function Get-Sql {
    param([string]$Query)
    return Invoke-Compose @("exec", "-T", "postgres", "psql", "-U", "weather", "-d", "weather", "-tAc", $Query)
}

function Get-SnapshotCount {
    param([long]$LocationId)
    return [int](Get-Sql "SELECT count(*) FROM forecast_snapshots WHERE location_id = $LocationId")
}

function Wait-ForSnapshot {
    param([long]$LocationId, [int]$TimeoutSeconds = 150)
    Wait-For -Description "snapshot for location $LocationId" -TimeoutSeconds $TimeoutSeconds -Condition {
        try { return (Get-SnapshotCount $LocationId) -gt 0 } catch { return $false }
    }
}

function Invoke-Scenario {
    param([string]$Name, [scriptblock]$Body)
    Write-Host "--> $Name"
    try {
        & $Body
        $results.Add([pscustomobject]@{ Scenario = $Name; Result = "PASS"; Detail = "" })
    }
    catch {
        $results.Add([pscustomobject]@{ Scenario = $Name; Result = "FAIL"; Detail = "$_" })
    }
}

function Wait-ForApplication {
    Wait-For -Description "application readiness" -Condition {
        try {
            $health = Invoke-RestMethod -Uri "$appBaseUrl/actuator/health/readiness" -TimeoutSec 2
            return $health.status -eq "UP"
        }
        catch { return $false }
    }
}

try {
    Write-Host "Starting isolated Compose project '$projectName' on port $appPort"
    Invoke-Compose @("up", "--build", "-d") | Out-Null

    Wait-For -Description "Open-Meteo stub" -Condition {
        try {
            Invoke-RestMethod -Uri "$stubBaseUrl/__admin/mappings" -TimeoutSec 2 | Out-Null
            return $true
        }
        catch { return $false }
    }
    Wait-ForApplication

    Invoke-Scenario "CRUD: create, read, list, update, delete, then 404" {
        $id = New-Location "crud-$runId" 41.1
        $read = Invoke-Api GET "$appBaseUrl/api/locations/$id"
        Assert-That ($read.Status -eq 200 -and $read.Json.name -eq "crud-$runId") "Read after create failed"
        $list = Invoke-Api GET "$appBaseUrl/api/locations"
        Assert-That ($list.Status -eq 200 -and $list.Raw -like "*crud-$runId*") "Created location missing from list"
        $update = Invoke-Api PUT "$appBaseUrl/api/locations/$id" @{ name = "crud-$runId-v2"; latitude = 41.2; longitude = 106.9 }
        Assert-That ($update.Status -eq 200) "Update returned $($update.Status)"
        $reread = Invoke-Api GET "$appBaseUrl/api/locations/$id"
        Assert-That ($reread.Json.name -eq "crud-$runId-v2") "Update was not persisted"
        $delete = Invoke-Api DELETE "$appBaseUrl/api/locations/$id"
        Assert-That ($delete.Status -in 200, 204) "Delete returned $($delete.Status)"
        Assert-That ((Invoke-Api GET "$appBaseUrl/api/locations/$id").Status -eq 404) "Deleted location still readable"
        Assert-That ((Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast").Status -eq 404) "Forecast for deleted location was not 404"
    }

    Invoke-Scenario "Validation: invalid coordinates return 400 and store nothing" {
        $bad = Invoke-Api POST "$appBaseUrl/api/locations" @{ name = "invalid-$runId"; latitude = 95; longitude = 106.8 }
        Assert-That ($bad.Status -eq 400) "Expected 400 but got $($bad.Status)"
        $list = Invoke-Api GET "$appBaseUrl/api/locations"
        Assert-That ($list.Raw -notlike "*invalid-$runId*") "Invalid location was stored"
    }

    Invoke-Scenario "Cache: miss then reuse, with matching payloads" {
        $id = New-Location "cache-$runId" 42.1
        $first = Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast"
        $callsAfterFirst = Get-LatitudeCalls "42.1"
        $second = Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast"
        Assert-That ($first.Status -eq 200 -and $second.Status -eq 200) "Forecast did not return 200"
        Assert-That ($callsAfterFirst -ge 1) "First forecast did not call the provider"
        Assert-That ((Get-LatitudeCalls "42.1") -eq $callsAfterFirst) "Second forecast called the provider again"
        Assert-That ($first.Json.current.temperature2m -eq $second.Json.current.temperature2m) "Cached payload differs"
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
    }

    Invoke-Scenario "Cache: TTL expiry causes a new provider call" {
        $id = New-Location "ttl-$runId" 43.1
        Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast" | Out-Null
        $before = Get-LatitudeCalls "43.1"
        Start-Sleep -Seconds 12
        $after = Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast"
        Assert-That ($after.Status -eq 200) "Forecast after TTL returned $($after.Status)"
        Assert-That ((Get-LatitudeCalls "43.1") -gt $before) "Cache entry did not expire"
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
    }

    Invoke-Scenario "Kafka: update invalidates the cached forecast" {
        $id = New-Location "inval-$runId" 44.1
        Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast" | Out-Null
        $before = Get-LatitudeCalls "44.1"
        Assert-That ((Invoke-Api PUT "$appBaseUrl/api/locations/$id" @{ name = "inval-$runId"; latitude = 44.1; longitude = 106.9 }).Status -eq 200) "Update failed"
        Wait-For -Description "cache invalidation" -TimeoutSeconds 8 -Condition {
            Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast" | Out-Null
            return (Get-LatitudeCalls "44.1") -gt $before
        }
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
    }

    Invoke-Scenario "Kafka: repeated events are harmless and the cache refills once" {
        $id = New-Location "repeat-$runId" 45.1
        Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast" | Out-Null
        1..3 | ForEach-Object {
            Assert-That ((Invoke-Api PUT "$appBaseUrl/api/locations/$id" @{ name = "repeat-$runId"; latitude = 45.1; longitude = 106.8 }).Status -eq 200) "Update failed"
        }
        Start-Sleep -Seconds 3
        $beforeRefill = Get-LatitudeCalls "45.1"
        Assert-That ((Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast").Status -eq 200) "Forecast failed after repeated events"
        $afterRefill = Get-LatitudeCalls "45.1"
        Assert-That ((Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast").Status -eq 200) "Forecast failed after refill"
        Assert-That ($afterRefill -eq $beforeRefill + 1) "Expected one refill call, saw $($afterRefill - $beforeRefill)"
        Assert-That ((Get-LatitudeCalls "45.1") -eq $afterRefill) "Cache was not reused after refill"
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
    }

    Invoke-Scenario "Kafka: keyed CREATED, UPDATED, DELETED events are published in order" {
        $id = New-Location "events-$runId" 46.1
        Invoke-Api PUT "$appBaseUrl/api/locations/$id" @{ name = "events-$runId"; latitude = 46.1; longitude = 106.9 } | Out-Null
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
        Push-Location $repositoryRoot
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $lines = & docker compose --project-name $projectName --profile e2e exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh `
                --bootstrap-server kafka:9092 --topic location-events --from-beginning --timeout-ms 10000 `
                --property print.key=true 2>&1 | ForEach-Object { "$_" }
        }
        finally {
            $ErrorActionPreference = $previousPreference
            Pop-Location
        }
        $types = @($lines | Where-Object { $_ -match "^$id\s" } | ForEach-Object { ($_ -replace "^$id\s+", "" | ConvertFrom-Json).eventType })
        Assert-That (($types -join ",") -eq "CREATED,UPDATED,DELETED") "Unexpected events for key ${id}: $($types -join ',')"
    }

    Invoke-Scenario "Provider failure: explicit 502 and nothing cached" {
        Add-StubMapping @{ priority = 1; request = @{ method = "GET"; urlPath = "/v1/forecast"; queryParameters = @{ latitude = @{ equalTo = "47.1" } } }; response = @{ status = 500 } }
        $id = New-Location "pfail-$runId" 47.1
        Assert-That ((Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast").Status -eq 502) "Expected 502 on provider failure"
        Reset-Stub
        $callsBefore = Get-LatitudeCalls "47.1"
        Assert-That ((Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast").Status -eq 200) "Forecast did not recover"
        Assert-That ((Get-LatitudeCalls "47.1") -gt $callsBefore) "Failure response appears to have been cached"
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
    }

    Invoke-Scenario "Provider timeout: bounded 502 instead of hanging" {
        Add-StubMapping @{ priority = 1; request = @{ method = "GET"; urlPath = "/v1/forecast"; queryParameters = @{ latitude = @{ equalTo = "48.1" } } }; response = @{ status = 200; fixedDelayMilliseconds = 6000 } }
        $id = New-Location "timeout-$runId" 48.1
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $response = Invoke-Api GET "$appBaseUrl/api/locations/$id/forecast"
        $watch.Stop()
        Reset-Stub
        Assert-That ($response.Status -eq 502) "Expected 502 on timeout but got $($response.Status)"
        Assert-That ($watch.Elapsed.TotalSeconds -lt 5.5) "Read timeout was not enforced ($([int]$watch.Elapsed.TotalSeconds)s)"
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
    }

    $batchIds = @()
    Invoke-Scenario "Batch: partitioned scheduled run snapshots every location" {
        $script:batchIds = 1..7 | ForEach-Object { New-Location "batch-$runId-$_" (50 + $_ / 10) }
        foreach ($batchId in $script:batchIds) { Wait-ForSnapshot $batchId }
        $badSteps = Get-Sql @"
SELECT count(*) FROM (
  SELECT e.job_execution_id FROM batch_job_execution e
  JOIN batch_step_execution s ON s.job_execution_id = e.job_execution_id AND s.step_name LIKE 'forecastRefreshWorkerStep%'
  WHERE e.status = 'COMPLETED' GROUP BY e.job_execution_id HAVING count(*) > 3) t
"@
        Assert-That ([int]$badSteps -eq 0) "A completed job used more worker partitions than the grid size"
        $completed = Get-Sql "SELECT count(*) FROM batch_job_execution WHERE status = 'COMPLETED'"
        Assert-That ([int]$completed -ge 1) "No completed batch execution recorded"
        $full = Get-Sql @"
SELECT count(*) FROM (
  SELECT e.job_execution_id FROM batch_job_execution e
  JOIN batch_step_execution s ON s.job_execution_id = e.job_execution_id AND s.step_name LIKE 'forecastRefreshWorkerStep%'
  WHERE e.status = 'COMPLETED' GROUP BY e.job_execution_id HAVING count(*) = 3) t
"@
        Assert-That ([int]$full -ge 1) "No completed job fanned out into the 3 configured worker partitions"
    }

    Invoke-Scenario "Batch: repeat runs update snapshots instead of duplicating them" {
        $probe = $script:batchIds[0]
        $runsNow = [int](Get-Sql "SELECT count(*) FROM batch_job_execution WHERE status = 'COMPLETED'")
        Wait-For -Description "another completed run" -TimeoutSeconds 120 -Condition {
            [int](Get-Sql "SELECT count(*) FROM batch_job_execution WHERE status = 'COMPLETED'") -gt $runsNow
        }
        Assert-That ((Get-SnapshotCount $probe) -eq 1) "Snapshot count for a location is not 1 after repeated runs"
        $duplicates = Get-Sql "SELECT count(*) FROM (SELECT 1 FROM forecast_snapshots GROUP BY location_id, forecast_time HAVING count(*) > 1) d"
        Assert-That ([int]$duplicates -eq 0) "Duplicate snapshots found"
    }

    Invoke-Scenario "Batch: scheduled runs never overlap" {
        $overlaps = Get-Sql @"
SELECT count(*) FROM batch_job_execution a JOIN batch_job_execution b
  ON a.job_execution_id < b.job_execution_id AND a.start_time < b.end_time AND b.start_time < a.end_time
"@
        Assert-That ([int]$overlaps -eq 0) "Overlapping batch job executions detected"
    }

    Invoke-Scenario "Batch: transient provider failure is retried and completes" {
        Add-StubMapping @{ priority = 1; scenarioName = "transient-$runId"; requiredScenarioState = "Started"; newScenarioState = "recovered"
            request = @{ method = "GET"; urlPath = "/v1/forecast"; queryParameters = @{ latitude = @{ equalTo = "60.1" } } }; response = @{ status = 500 } }
        Add-StubMapping @{ priority = 1; scenarioName = "transient-$runId"; requiredScenarioState = "recovered"
            request = @{ method = "GET"; urlPath = "/v1/forecast"; queryParameters = @{ latitude = @{ equalTo = "60.1" } } }
            response = @{ status = 200; bodyFileName = "open-meteo-current.json"; headers = @{ "Content-Type" = "application/json" } } }
        $id = New-Location "transient-$runId" 60.1
        Wait-ForSnapshot $id
        Assert-That ((Get-LatitudeCalls "60.1") -ge 2) "Provider was not retried"
        Reset-Stub
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
    }

    Invoke-Scenario "Batch: permanent failure fails the job visibly, then recovers" {
        Add-StubMapping @{ priority = 1; request = @{ method = "GET"; urlPath = "/v1/forecast"; queryParameters = @{ latitude = @{ equalTo = "61.1" } } }; response = @{ status = 500 } }
        $before = [int](Get-Sql "SELECT count(*) FROM batch_job_execution WHERE status = 'FAILED'")
        $id = New-Location "permanent-$runId" 61.1
        Wait-For -Description "FAILED batch execution" -TimeoutSeconds 120 -Condition {
            [int](Get-Sql "SELECT count(*) FROM batch_job_execution WHERE status = 'FAILED'") -gt $before
        }
        Assert-That ((Get-SnapshotCount $id) -eq 0) "Failed location unexpectedly has a snapshot"
        Reset-Stub
        Wait-ForSnapshot $id
        Invoke-Api DELETE "$appBaseUrl/api/locations/$id" | Out-Null
    }

    Invoke-Scenario "Kafka outage: write returns 503, rolls back, and service recovers" {
        Invoke-Compose @("stop", "kafka") | Out-Null
        try {
            $response = Invoke-Api POST "$appBaseUrl/api/locations" @{ name = "outage-$runId"; latitude = 70.1; longitude = 106.8 }
            Assert-That ($response.Status -eq 503) "Expected 503 during broker outage but got $($response.Status)"
            Assert-That ((Invoke-Api GET "$appBaseUrl/api/locations").Raw -notlike "*outage-$runId*") "Location persisted despite publish failure"
        }
        finally {
            Invoke-Compose @("start", "kafka") | Out-Null
        }
        Wait-For -Description "writes after broker recovery" -TimeoutSeconds 90 -Condition {
            try {
                $retry = Invoke-Api POST "$appBaseUrl/api/locations" @{ name = "recovered-$runId"; latitude = 70.2; longitude = 106.8 }
                if ($retry.Status -eq 201) {
                    Invoke-Api DELETE "$appBaseUrl/api/locations/$($retry.Json.id)" | Out-Null
                    return $true
                }
                return $false
            }
            catch { return $false }
        }
    }

    Write-Host ""
    $results | Format-Table Scenario, Result, Detail -AutoSize -Wrap | Out-String | Write-Host
    $failed = @($results | Where-Object Result -eq "FAIL")
    if ($failed.Count -gt 0) {
        throw "$($failed.Count) of $($results.Count) E2E scenarios failed"
    }
    Write-Host "E2E passed: all $($results.Count) container scenarios."
}
catch {
    $failure = $_
    Write-Host "E2E failed: $failure"
    try {
        Invoke-Compose @("logs", "--no-color", "app", "postgres", "redis", "kafka", "open-meteo-stub")
    }
    catch {
        Write-Warning "Unable to collect Compose logs: $_"
    }
}
finally {
    try {
        Invoke-Compose @("down", "--volumes", "--remove-orphans") | Out-Null
        Write-Host "Removed isolated E2E stack and its dedicated volumes."
    }
    catch {
        if ($null -eq $failure) {
            $failure = $_
        }
        Write-Warning "E2E cleanup failed; remove only project '$projectName' with docker compose --project-name $projectName --profile e2e down --volumes --remove-orphans"
    }

    foreach ($name in $environmentNames) {
        if ($null -eq $originalEnvironment[$name]) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item "Env:$name" $originalEnvironment[$name]
        }
    }
}

if ($null -ne $failure) {
    throw $failure
}
