[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$PythonExecutable,

    [Parameter(Mandatory = $true)]
    [string]$NodeExecutable
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runtimeRoot = Join-Path $repositoryRoot '.phase12-runtime'
$statePath = Join-Path $runtimeRoot 'state.json'
$javaDocumentRoot = Join-Path $runtimeRoot 'java-documents'
$pythonDocumentRoot = Join-Path $runtimeRoot 'python-documents'
$jarPath = Join-Path $repositoryRoot 'java-backend\target\insurance-platform-backend-0.6.0-SNAPSHOT.jar'
$pythonPath = $PythonExecutable
$nodePath = $NodeExecutable
$vitePath = Join-Path $repositoryRoot 'web-client\node_modules\vite\bin\vite.js'
$mysqlPort = 33306
$redisPort = 16379
$pythonPort = 8000
$javaPort = 8080
$vuePort = 5173
$suffix = $PID
$mysqlContainer = "insurance-phase12-mysql-$suffix"
$redisContainer = "insurance-phase12-redis-$suffix"
$startedProcesses = @()
$startedContainers = @()

function Assert-File([string]$path, [string]$description) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "$description not found: $path"
    }
}

function Assert-PortAvailable([int]$port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.ConnectAsync('127.0.0.1', $port)
        if ($connection.Wait(250) -and $client.Connected) {
            throw "Port $port is already in use."
        }
    } finally {
        $client.Dispose()
    }
}

function Wait-Http([string]$url, [int]$seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url `
                -Headers @{ 'X-Trace-Id' = '01J4PHASE12STARTUP' } `
                -UseBasicParsing `
                -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return }
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    throw "Timed out waiting for $url"
}

function Start-IsolatedProcess(
    [string]$filePath,
    [string[]]$arguments,
    [string]$workingDirectory,
    [hashtable]$environment,
    [string]$name
) {
    $previous = @{}
    try {
        foreach ($entry in $environment.GetEnumerator()) {
            $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
            [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
        }
        $process = Start-Process -FilePath $filePath `
            -ArgumentList $arguments `
            -WorkingDirectory $workingDirectory `
            -RedirectStandardOutput (Join-Path $runtimeRoot "$name.stdout.log") `
            -RedirectStandardError (Join-Path $runtimeRoot "$name.stderr.log") `
            -WindowStyle Hidden `
            -PassThru
        $script:startedProcesses += $process
        return $process
    } finally {
        foreach ($entry in $environment.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $previous[$entry.Key], 'Process')
        }
    }
}

function Remove-OwnedResources {
    foreach ($process in $script:startedProcesses) {
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    foreach ($container in $script:startedContainers) {
        & docker rm -f $container *> $null
    }
}

try {
    Assert-File $jarPath 'Packaged Java backend'
    Assert-File $pythonPath 'Phase 12 Python executable'
    Assert-File $nodePath 'Phase 12 Node executable'
    Assert-File $vitePath 'Installed Vite entry point'
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'docker command is not available.'
    }
    foreach ($port in @($mysqlPort, $redisPort, $pythonPort, $javaPort, $vuePort)) {
        Assert-PortAvailable $port
    }

    New-Item -ItemType Directory -Force -Path $runtimeRoot, $javaDocumentRoot, $pythonDocumentRoot | Out-Null

    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $passwordBytes = New-Object byte[] 24
        $jwtBytes = New-Object byte[] 32
        $random.GetBytes($passwordBytes)
        $random.GetBytes($jwtBytes)
    } finally {
        $random.Dispose()
    }
    $databasePassword = [Convert]::ToBase64String($passwordBytes)
    $jwtSecret = [Convert]::ToBase64String($jwtBytes)

    $mysqlArgs = @(
        'run', '-d', '--name', $mysqlContainer,
        '--label', 'insurance.phase=12',
        '--tmpfs', '/var/lib/mysql:rw',
        '-e', 'MYSQL_DATABASE=phase12_platform',
        '-e', 'MYSQL_USER=phase12_user',
        '-e', "MYSQL_PASSWORD=$databasePassword",
        '-e', "MYSQL_ROOT_PASSWORD=$databasePassword",
        '-p', "127.0.0.1:${mysqlPort}:3306",
        'mysql:8.4'
    )
    & docker @mysqlArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start the Phase 12 MySQL container.' }
    $startedContainers += $mysqlContainer

    $redisArgs = @(
        'run', '-d', '--name', $redisContainer,
        '--label', 'insurance.phase=12',
        '-p', "127.0.0.1:${redisPort}:6379",
        'redis:7.4-alpine'
    )
    & docker @redisArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start the Phase 12 Redis container.' }
    $startedContainers += $redisContainer

    $deadline = [DateTime]::UtcNow.AddMinutes(2)
    do {
        & docker exec -e "MYSQL_PWD=$databasePassword" $mysqlContainer `
            mysqladmin ping -h 127.0.0.1 -u phase12_user --silent *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw 'Phase 12 MySQL readiness timed out.' }

    $python = Start-IsolatedProcess $pythonPath `
        @('-m', 'uvicorn', 'tests.support.phase12_e2e_api:app', '--host', '127.0.0.1', '--port', "$pythonPort") `
        $repositoryRoot `
        @{ PHASE12_PYTHON_DOCUMENT_DIR = $pythonDocumentRoot } `
        'python'
    Wait-Http "http://127.0.0.1:$pythonPort/internal/v1/health/ready" 30

    $java = Start-IsolatedProcess (Get-Command java).Source `
        @('-jar', "`"$jarPath`"") `
        (Join-Path $repositoryRoot 'java-backend') `
        @{
            MYSQL_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/phase12_platform"
            MYSQL_USERNAME = 'phase12_user'
            MYSQL_PASSWORD = $databasePassword
            REDIS_HOST = '127.0.0.1'
            REDIS_PORT = "$redisPort"
            PYTHON_AI_BASE_URL = "http://127.0.0.1:$pythonPort"
            PYTHON_AI_READ_TIMEOUT = '500ms'
            DOCUMENT_STORAGE_ROOT = $javaDocumentRoot
            JWT_SECRET_BASE64 = $jwtSecret
            APP_ENV = 'phase12-e2e'
            JAVA_BACKEND_PORT = "$javaPort"
        } `
        'java'
    Wait-Http "http://127.0.0.1:$javaPort/actuator/health/readiness" 60

    $node = Start-IsolatedProcess $nodePath `
        @("`"$vitePath`"", '--host', '127.0.0.1', '--port', "$vuePort", '--strictPort') `
        (Join-Path $repositoryRoot 'web-client') `
        @{} `
        'vue'
    Wait-Http "http://127.0.0.1:$vuePort/" 30

    $state = [ordered]@{
        repositoryRoot = $repositoryRoot
        startedAt = [DateTime]::UtcNow.ToString('o')
        mysqlContainer = $mysqlContainer
        redisContainer = $redisContainer
        processes = @(
            @{ name = 'python'; id = $python.Id; executable = $pythonPath },
            @{ name = 'java'; id = $java.Id; executable = (Get-Command java).Source },
            @{ name = 'vue'; id = $node.Id; executable = $nodePath }
        )
        urls = @{
            vue = "http://127.0.0.1:$vuePort/"
            javaReadiness = "http://127.0.0.1:$javaPort/actuator/health/readiness"
            pythonReadiness = "http://127.0.0.1:$pythonPort/internal/v1/health/ready"
        }
    }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Output "Phase 12 E2E environment ready: http://127.0.0.1:$vuePort/"
} catch {
    Remove-OwnedResources
    throw
}
