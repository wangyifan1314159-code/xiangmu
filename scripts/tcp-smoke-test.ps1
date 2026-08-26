param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$TcpHost = '127.0.0.1',
    [int]$TcpPort = 1884,
    [string]$Username = 'admin',
    [string]$Password = 'admin123'
)

$ErrorActionPreference = 'Stop'
$deviceId = $null
$token = $null
$client = $null

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Send-JsonLine($Writer, $Payload) {
    $Writer.WriteLine(($Payload | ConvertTo-Json -Compress -Depth 10))
}

try {
    Write-Host '[1/7] Login'
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -ContentType 'application/json' `
        -Body (@{ username = $Username; password = $Password } | ConvertTo-Json -Compress)
    $token = $login.data.token
    $headers = @{ Authorization = "Bearer $token" }

    Write-Host '[2/7] Create temporary device'
    $payload = @{
        name = 'TCP Smoke Test Device'
        type = 'test-device'
        status = 'OFFLINE'
        location = 'automated-test'
        description = 'Deleted automatically after TCP smoke test'
        sensors = @(
            @{ id = 'tcp_test_temp'; name = 'TCP Test Temperature'; type = 'temperature'; unit = 'C'; value = 0; minVal = -40; maxVal = 100 },
            @{ id = 'tcp_test_fan'; name = 'TCP Test Fan'; type = 'fan'; unit = ''; value = 0; minVal = 0; maxVal = 1 }
        )
    }
    $created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/devices" -Headers $headers `
        -ContentType 'application/json' -Body ($payload | ConvertTo-Json -Compress -Depth 10)
    $deviceId = $created.data.deviceId
    $apiKey = $created.data.apiKey
    Assert-True ($deviceId -and $apiKey) 'Device credentials were not returned'

    Write-Host "[3/7] Connect TCP $TcpHost`:$TcpPort and authenticate"
    $client = [System.Net.Sockets.TcpClient]::new()
    $client.Connect($TcpHost, $TcpPort)
    $stream = $client.GetStream()
    $stream.ReadTimeout = 8000
    $encoding = [System.Text.UTF8Encoding]::new($false)
    $reader = [System.IO.StreamReader]::new($stream, $encoding, $false, 1024, $true)
    $writer = [System.IO.StreamWriter]::new($stream, $encoding, 1024, $true)
    $writer.NewLine = "`n"
    $writer.AutoFlush = $true

    Send-JsonLine $writer @{ type = 'auth'; deviceId = $deviceId; apiKey = $apiKey }
    $authResult = $reader.ReadLine() | ConvertFrom-Json
    Assert-True ($authResult.type -eq 'auth_result' -and $authResult.success) 'TCP authentication failed'

    Write-Host '[4/7] Upload telemetry value 26.5'
    Send-JsonLine $writer @{ type = 'telemetry'; sensorId = 'tcp_test_temp'; value = 26.5; sensorType = 'temperature'; unit = 'C' }
    Start-Sleep -Seconds 2
    $latest = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/data/$deviceId/latest?sensorId=tcp_test_temp&limit=1" -Headers $headers
    Assert-True ($latest.data.Count -eq 1 -and [double]$latest.data[0].value -eq 26.5) 'Telemetry was not persisted'

    Write-Host '[5/7] Send fan ON control command'
    $commandBody = @{ command = 'on'; params = @{ actuator = 'TCP Test Fan' } } | ConvertTo-Json -Compress -Depth 5
    $null = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/data/$deviceId/command" -Headers $headers `
        -ContentType 'application/json' -Body $commandBody
    $command = $reader.ReadLine() | ConvertFrom-Json
    Assert-True ($command.type -eq 'command' -and $command.command -eq 'on' -and $command.params.actuator -eq 'TCP Test Fan') `
        'TCP device did not receive the expected control command'

    Write-Host '[6/7] Return command result'
    Send-JsonLine $writer @{ type = 'command_result'; command = 'on'; success = $true; message = 'executed' }

    Write-Host '[7/7] PASS: auth, telemetry, persistence, command delivery and receipt all verified'
}
finally {
    if ($client) { $client.Dispose() }
    if ($deviceId -and $token) {
        try {
            Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/devices/$deviceId" `
                -Headers @{ Authorization = "Bearer $token" } | Out-Null
            Write-Host "Cleanup complete: $deviceId"
        } catch {
            Write-Warning "Cleanup failed for $deviceId`: $($_.Exception.Message)"
        }
    }
}
