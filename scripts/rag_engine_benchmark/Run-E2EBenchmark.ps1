[CmdletBinding()]
param(
    [string]$PythonExecutable = 'D:\Phase12Python\platform-venv\Scripts\python.exe',
    [int]$PythonPort = 8101,
    [int]$JavaPort = 8181,
    [int]$MySqlPort = 34306,
    [int]$RedisPort = 17379
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$artifactRoot = Join-Path $repositoryRoot 'artifacts\rag_engine_benchmark'
$runtimeRoot = Join-Path $artifactRoot 'e2e-runtime'
$jarPath = (Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'java-backend\target') `
    -Filter 'insurance-platform-backend-*.jar' | Select-Object -First 1).FullName
$javaExecutable = (Get-Command java.exe -ErrorAction Stop).Source
$deepSeekKey = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'User')

if ([string]::IsNullOrWhiteSpace($deepSeekKey)) { throw 'User DEEPSEEK_API_KEY is unavailable.' }
if (-not (Test-Path -LiteralPath $PythonExecutable -PathType Leaf)) { throw 'Python executable is unavailable.' }
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { throw 'Built Spring Boot jar is unavailable.' }
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null

foreach ($port in @($PythonPort, $JavaPort, $MySqlPort, $RedisPort)) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        try { $client.Connect('127.0.0.1', $port) } catch [Net.Sockets.SocketException] { continue }
        if ($client.Connected) { throw "Benchmark port is already in use: $port" }
    } finally { $client.Dispose() }
}

function Wait-Http([string]$url, [int]$seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url `
                -Headers @{ 'X-Trace-Id' = 'RAG-BENCHMARK-READINESS' } `
                -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    throw "Timed out waiting for $url"
}

function Start-IsolatedProcess(
    [string]$filePath,
    [string[]]$arguments,
    [hashtable]$environment,
    [string]$stdout,
    [string]$stderr
) {
    $previous = @{}
    try {
        foreach ($entry in $environment.GetEnumerator()) {
            $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
            [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
        }
        return Start-Process -FilePath $filePath -ArgumentList $arguments `
            -WorkingDirectory $repositoryRoot -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    } finally {
        foreach ($entry in $environment.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $previous[$entry.Key], 'Process')
        }
    }
}

function Stop-OwnedProcess($process) {
    if ($null -eq $process) { return }
    $process.Refresh()
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
}

$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $databaseBytes = New-Object byte[] 24
    $jwtBytes = New-Object byte[] 32
    $random.GetBytes($databaseBytes)
    $random.GetBytes($jwtBytes)
    $databasePassword = [Convert]::ToBase64String($databaseBytes)
    $jwtSecret = [Convert]::ToBase64String($jwtBytes)
} finally { $random.Dispose() }

$suffix = $PID
$mysqlContainer = "insurance-benchmark-mysql-$suffix"
$redisContainer = "insurance-benchmark-redis-$suffix"
$containers = @()

try {
    & docker run -d --name $mysqlContainer --label 'insurance.rag-benchmark=true' `
        --tmpfs '/var/lib/mysql:rw' -e 'MYSQL_DATABASE=insurance_ai' `
        -e 'MYSQL_USER=insurance_benchmark' -e "MYSQL_PASSWORD=$databasePassword" `
        -e "MYSQL_ROOT_PASSWORD=$databasePassword" `
        -p "127.0.0.1:${MySqlPort}:3306" mysql:8.4 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start benchmark MySQL.' }
    $containers += $mysqlContainer
    & docker run -d --name $redisContainer --label 'insurance.rag-benchmark=true' `
        -p "127.0.0.1:${RedisPort}:6379" redis:7.4-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start benchmark Redis.' }
    $containers += $redisContainer

    $deadline = [DateTime]::UtcNow.AddMinutes(2)
    do {
        & docker exec -e "MYSQL_PWD=$databasePassword" $mysqlContainer `
            mysqladmin ping -h 127.0.0.1 -u insurance_benchmark --silent *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw 'Benchmark MySQL readiness timed out.' }

    foreach ($engine in @('langchain', 'llamaindex')) {
        $pythonProcess = $null
        $javaProcess = $null
        try {
            $pythonProcess = Start-IsolatedProcess $PythonExecutable `
                @('-m', 'uvicorn', 'api.main:app', '--host', '127.0.0.1', '--port', "$PythonPort") `
                @{
                    DEEPSEEK_API_KEY = $deepSeekKey
                    RAG_ENGINE = $engine
                    AI_SERVICE_HOST = '127.0.0.1'
                    AI_SERVICE_PORT = "$PythonPort"
                    DOCUMENT_STORAGE_ROOT = (Join-Path $artifactRoot 'corpus')
                    VECTOR_STORE_ROOT = (Join-Path $artifactRoot 'indexes')
                    PYTHONUNBUFFERED = '1'
                    HF_HUB_OFFLINE = '1'
                    TRANSFORMERS_OFFLINE = '1'
                } `
                (Join-Path $runtimeRoot "$engine-python.stdout.log") `
                (Join-Path $runtimeRoot "$engine-python.stderr.log")
            Wait-Http "http://127.0.0.1:$PythonPort/internal/v1/health/ready" 300

            $quotedJarPath = '"' + $jarPath + '"'
            $javaProcess = Start-IsolatedProcess $javaExecutable @('-jar', $quotedJarPath) `
                @{
                    MYSQL_URL = "jdbc:mysql://127.0.0.1:$MySqlPort/insurance_ai"
                    MYSQL_USERNAME = 'insurance_benchmark'
                    MYSQL_PASSWORD = $databasePassword
                    REDIS_HOST = '127.0.0.1'
                    REDIS_PORT = "$RedisPort"
                    REDIS_PASSWORD = ''
                    PYTHON_AI_BASE_URL = "http://127.0.0.1:$PythonPort"
                    DOCUMENT_STORAGE_ROOT = (Join-Path $runtimeRoot 'java-documents')
                    JWT_SECRET_BASE64 = $jwtSecret
                    APP_ENV = 'local'
                    JAVA_BACKEND_PORT = "$JavaPort"
                } `
                (Join-Path $runtimeRoot "$engine-java.stdout.log") `
                (Join-Path $runtimeRoot "$engine-java.stderr.log")
            Wait-Http "http://127.0.0.1:$JavaPort/actuator/health/readiness" 180

            & $PythonExecutable (Join-Path $PSScriptRoot 'run_e2e_benchmark.py') `
                --engine $engine --base-url "http://127.0.0.1:$JavaPort" `
                --output-dir $artifactRoot
            if ($LASTEXITCODE -ne 0) { throw "E2E client failed for $engine." }
        } finally {
            Stop-OwnedProcess $javaProcess
            Stop-OwnedProcess $pythonProcess
            Start-Sleep -Seconds 2
        }
    }
} finally {
    foreach ($container in $containers) {
        $owned = & docker ps -aq --filter "name=^/$container$" `
            --filter 'label=insurance.rag-benchmark=true'
        if ($LASTEXITCODE -eq 0 -and $owned) { & docker rm -f $container | Out-Null }
    }
}

Write-Output 'RAG engine public-API E2E benchmark completed.'
