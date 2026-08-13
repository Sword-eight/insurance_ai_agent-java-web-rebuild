[CmdletBinding()]
param(
    [string]$PythonExecutable = (Get-Command python -ErrorAction Stop).Source,
    [string]$NodeExecutable = (Get-Command node -ErrorAction Stop).Source,
    [string]$RuntimeRoot = [IO.Path]::Combine(
        [IO.Path]::GetTempPath(), 'insurance-resume-evidence')
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runtimeRootPath = [IO.Path]::GetFullPath($RuntimeRoot)
$statePath = Join-Path $runtimeRootPath 'state.json'
$javaDocumentRoot = Join-Path $runtimeRootPath 'java-documents'
$pythonDocumentRoot = Join-Path $runtimeRootPath 'python-documents'
$huggingFaceCache = Join-Path $runtimeRootPath 'huggingface'
$sentenceTransformersCache = Join-Path $runtimeRootPath 'sentence-transformers'
$jarPath = Join-Path $repositoryRoot 'java-backend\target\insurance-platform-backend-0.6.0-SNAPSHOT.jar'
$vitePath = Join-Path $repositoryRoot 'web-client\node_modules\vite\bin\vite.js'
$mysqlPort = 33306
$redisPort = 16379
$pythonPort = 8000
$javaPort = 8080
$vuePort = 5173
$suffix = $PID
$mysqlContainer = "insurance-resume-evidence-mysql-$suffix"
$redisContainer = "insurance-resume-evidence-redis-$suffix"
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
                -Headers @{ 'X-Trace-Id' = '01JRESUMEEVIDENCESTART' } `
                -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return }
        } catch {
            Start-Sleep -Milliseconds 500
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
            -RedirectStandardOutput (Join-Path $runtimeRootPath "$name.stdout.log") `
            -RedirectStandardError (Join-Path $runtimeRootPath "$name.stderr.log") `
            -WindowStyle Hidden -PassThru
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
    Assert-File $PythonExecutable 'Evidence Python executable'
    Assert-File $NodeExecutable 'Evidence Node executable'
    Assert-File $vitePath 'Installed Vite entry point'
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'docker command is not available.'
    }
    foreach ($port in @($mysqlPort, $redisPort, $pythonPort, $javaPort, $vuePort)) {
        Assert-PortAvailable $port
    }

    $deepSeekKey = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Process')
    if ([string]::IsNullOrWhiteSpace($deepSeekKey)) {
        $deepSeekKey = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'User')
    }
    if ([string]::IsNullOrWhiteSpace($deepSeekKey)) {
        $deepSeekKey = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Machine')
    }
    if ([string]::IsNullOrWhiteSpace($deepSeekKey)) {
        throw 'DEEPSEEK_API_KEY is not available to this process.'
    }

    New-Item -ItemType Directory -Force -Path `
        $runtimeRootPath, $javaDocumentRoot, $pythonDocumentRoot | Out-Null

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

    & docker run -d --name $mysqlContainer `
        --label 'insurance.validation=resume-evidence' `
        --tmpfs '/var/lib/mysql:rw' `
        -e 'MYSQL_DATABASE=resume_evidence' `
        -e 'MYSQL_USER=evidence_user' `
        -e "MYSQL_PASSWORD=$databasePassword" `
        -e "MYSQL_ROOT_PASSWORD=$databasePassword" `
        -p "127.0.0.1:${mysqlPort}:3306" mysql:8.4 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start the Evidence MySQL container.' }
    $startedContainers += $mysqlContainer

    & docker run -d --name $redisContainer `
        --label 'insurance.validation=resume-evidence' `
        -p "127.0.0.1:${redisPort}:6379" redis:7.4-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start the Evidence Redis container.' }
    $startedContainers += $redisContainer

    $deadline = [DateTime]::UtcNow.AddMinutes(2)
    do {
        & docker exec -e "MYSQL_PWD=$databasePassword" $mysqlContainer `
            mysqladmin ping -h 127.0.0.1 -u evidence_user --silent *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw 'Evidence MySQL readiness timed out.' }

    $python = Start-IsolatedProcess $PythonExecutable `
        @('-m', 'uvicorn', 'api.main:app', '--host', '127.0.0.1', '--port', "$pythonPort") `
        $repositoryRoot `
        @{
            DEEPSEEK_API_KEY = $deepSeekKey
            HF_HOME = $huggingFaceCache
            SENTENCE_TRANSFORMERS_HOME = $sentenceTransformersCache
            TRANSFORMERS_OFFLINE = '1'
            HF_HUB_OFFLINE = '1'
            PYTHONUNBUFFERED = '1'
            DOCUMENT_STORAGE_ROOT = $pythonDocumentRoot
        } `
        'python'
    $deepSeekKey = $null
    # CPU-only BGE/FAISS startup can exceed 90 seconds on the validation host.
    Wait-Http "http://127.0.0.1:$pythonPort/internal/v1/health/ready" 300

    $java = Start-IsolatedProcess (Get-Command java).Source `
        @('-jar', "`"$jarPath`"") `
        (Join-Path $repositoryRoot 'java-backend') `
        @{
            MYSQL_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/resume_evidence"
            MYSQL_USERNAME = 'evidence_user'
            MYSQL_PASSWORD = $databasePassword
            REDIS_HOST = '127.0.0.1'
            REDIS_PORT = "$redisPort"
            PYTHON_AI_BASE_URL = "http://127.0.0.1:$pythonPort"
            DOCUMENT_STORAGE_ROOT = $javaDocumentRoot
            JWT_SECRET_BASE64 = $jwtSecret
            APP_ENV = 'resume-evidence'
            JAVA_BACKEND_PORT = "$javaPort"
        } `
        'java'
    Wait-Http "http://127.0.0.1:$javaPort/actuator/health/readiness" 90

    $node = Start-IsolatedProcess $NodeExecutable `
        @("`"$vitePath`"", '--host', '127.0.0.1', '--port', "$vuePort", '--strictPort') `
        (Join-Path $repositoryRoot 'web-client') @{} 'vue'
    Wait-Http "http://127.0.0.1:$vuePort/" 30

    $state = [ordered]@{
        repositoryRoot = $repositoryRoot
        runtimeRoot = $runtimeRootPath
        startedAt = [DateTime]::UtcNow.ToString('o')
        mysqlContainer = $mysqlContainer
        redisContainer = $redisContainer
        processes = @(
            @{ name = 'python'; id = $python.Id; executable = $PythonExecutable },
            @{ name = 'java'; id = $java.Id; executable = (Get-Command java).Source },
            @{ name = 'vue'; id = $node.Id; executable = $NodeExecutable }
        )
        urls = @{
            vue = "http://127.0.0.1:$vuePort/"
            java = "http://127.0.0.1:$javaPort"
            python = "http://127.0.0.1:$pythonPort"
        }
    }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Output "Resume Evidence environment ready: http://127.0.0.1:$vuePort/"
} catch {
    $deepSeekKey = $null
    Remove-OwnedResources
    throw
}
