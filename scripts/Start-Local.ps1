[CmdletBinding()]
param(
    [string]$PythonExecutable,
    [string]$MavenExecutable,
    [string]$NpmExecutable,
    [string]$RuntimeRoot,
    [string]$HuggingFaceCache,
    [string]$SentenceTransformersCache,
    [ValidateSet('langchain', 'llamaindex')]
    [string]$RagEngine = 'langchain',
    [switch]$UseExistingInfrastructure,
    [switch]$InstallDependencies,
    [switch]$ValidateOnly,
    [string]$MySqlUrl,
    [string]$MySqlUsername,
    [string]$MySqlPassword,
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 6379,
    [string]$RedisPassword = '',
    [int]$DockerMySqlPort = 33306,
    [int]$DockerRedisPort = 16379,
    [int]$PythonPort = 8000,
    [int]$VuePort = 5173
)

$ErrorActionPreference = 'Stop'
$JavaPort = 8080 # The checked-in Vite proxy is intentionally fixed to this backend port.

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($RuntimeRoot)) {
    $RuntimeRoot = Join-Path $repositoryRoot '.local-runtime'
}
$runtimeRootPath = [IO.Path]::GetFullPath($RuntimeRoot)
$statePath = Join-Path $runtimeRootPath 'state.json'
$logRoot = Join-Path $runtimeRootPath 'logs'
$javaDocumentRoot = Join-Path $runtimeRootPath 'java-documents'
$pythonDocumentRoot = Join-Path $runtimeRootPath 'python-documents'
$pythonVectorStoreRoot = Join-Path $runtimeRootPath 'vectorstore'
$startedProcesses = @()
$startedContainers = @()

function Get-EnvironmentValue([string]$name) {
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $value = [Environment]::GetEnvironmentVariable($name, $scope)
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($HuggingFaceCache)) {
    $HuggingFaceCache = Get-EnvironmentValue 'HF_HOME'
}
if ([string]::IsNullOrWhiteSpace($HuggingFaceCache)) {
    $HuggingFaceCache = Join-Path $runtimeRootPath 'huggingface'
}
$huggingFaceCachePath = [IO.Path]::GetFullPath($HuggingFaceCache)

if ([string]::IsNullOrWhiteSpace($SentenceTransformersCache)) {
    $SentenceTransformersCache = Get-EnvironmentValue 'SENTENCE_TRANSFORMERS_HOME'
}
if ([string]::IsNullOrWhiteSpace($SentenceTransformersCache)) {
    $SentenceTransformersCache = Join-Path $runtimeRootPath 'sentence-transformers'
}
$sentenceTransformersCachePath = [IO.Path]::GetFullPath($SentenceTransformersCache)

function Resolve-Executable(
    [string]$provided,
    [string]$environmentName,
    [string[]]$commandNames,
    [string]$description
) {
    $candidate = $provided
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $candidate = Get-EnvironmentValue $environmentName
    }
    if (-not [string]::IsNullOrWhiteSpace($candidate)) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
        $resolved = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($resolved) { return $resolved.Source }
        throw "$description not found: $candidate"
    }
    foreach ($name in $commandNames) {
        $resolved = Get-Command $name -ErrorAction SilentlyContinue
        if ($resolved) { return $resolved.Source }
    }
    throw "$description not found. Pass the matching script parameter or set $environmentName."
}

function Assert-File([string]$path, [string]$description) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "$description not found: $path"
    }
}

function Assert-PortAvailable([int]$port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        try {
            $client.Connect('127.0.0.1', $port)
        } catch [Net.Sockets.SocketException] {
            return
        }
        if ($client.Connected) { throw "Port $port is already in use." }
    } finally {
        $client.Dispose()
    }
}

function Wait-Http([string]$url, [int]$seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url `
                -Headers @{ 'X-Trace-Id' = 'LOCAL-LAUNCHER-READINESS' } `
                -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Timed out waiting for $url"
}

function Stop-ProcessTree([int]$processId) {
    try {
        $children = Get-CimInstance Win32_Process `
            -Filter "ParentProcessId=$processId" -ErrorAction SilentlyContinue
        foreach ($child in $children) { Stop-ProcessTree ([int]$child.ProcessId) }
    } catch {
        # The owned root process is still stopped if process enumeration is unavailable.
    }
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}

function Start-ManagedProcess(
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
            -RedirectStandardOutput (Join-Path $logRoot "$name.stdout.log") `
            -RedirectStandardError (Join-Path $logRoot "$name.stderr.log") `
            -WindowStyle Hidden -PassThru
        Start-Sleep -Milliseconds 300
        $process.Refresh()
        if ($process.HasExited) {
            throw "$name exited during startup. Check $logRoot."
        }
        $script:startedProcesses += $process
        return $process
    } finally {
        foreach ($entry in $environment.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $previous[$entry.Key], 'Process')
        }
    }
}

function Get-ProcessRecord([string]$name, $process) {
    $process.Refresh()
    return [ordered]@{
        name = $name
        id = $process.Id
        executable = $process.Path
        startTimeUtc = $process.StartTime.ToUniversalTime().ToString('o')
    }
}

function Remove-OwnedResources {
    foreach ($process in $script:startedProcesses) {
        if (-not $process.HasExited) { Stop-ProcessTree $process.Id }
    }
    foreach ($container in $script:startedContainers) {
        & docker rm -f $container *> $null
    }
}

$pythonPath = Resolve-Executable $PythonExecutable 'PYTHON_EXECUTABLE' `
    @('python', 'python.exe') 'Python executable'
$mavenPath = Resolve-Executable $MavenExecutable 'MAVEN_EXECUTABLE' `
    @('mvn.cmd', 'mvn') 'Maven executable'
$npmPath = Resolve-Executable $NpmExecutable 'NPM_EXECUTABLE' `
    @('npm.cmd', 'npm') 'NPM executable'
$nodePath = Join-Path (Split-Path $npmPath -Parent) 'node.exe'
if (-not (Test-Path -LiteralPath $nodePath -PathType Leaf)) {
    $resolvedNode = Get-Command node.exe -ErrorAction SilentlyContinue
    if (-not $resolvedNode) {
        throw 'Node executable not found beside NPM or on PATH.'
    }
    $nodePath = $resolvedNode.Source
}
$nodeDirectory = Split-Path $nodePath -Parent

Assert-File (Join-Path $repositoryRoot 'api\main.py') 'FastAPI entry point'
Assert-File (Join-Path $repositoryRoot 'java-backend\pom.xml') 'Java Maven project'
Assert-File (Join-Path $repositoryRoot 'web-client\package.json') 'Vue package manifest'

$deepSeekKey = Get-EnvironmentValue 'DEEPSEEK_API_KEY'
if ([string]::IsNullOrWhiteSpace($deepSeekKey)) {
    throw 'DEEPSEEK_API_KEY is not available in Process, User, or Machine environment variables.'
}

if ($InstallDependencies) {
    & $pythonPath -m pip install -r (Join-Path $repositoryRoot 'requirements-dev.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Python dependency installation failed.' }
    if ($RagEngine -eq 'llamaindex') {
        & $pythonPath -m pip install -r `
            (Join-Path $repositoryRoot 'requirements-llamaindex.txt')
        if ($LASTEXITCODE -ne 0) {
            throw 'LlamaIndex dependency installation failed.'
        }
    }
    Push-Location (Join-Path $repositoryRoot 'web-client')
    try {
        & $npmPath ci
        if ($LASTEXITCODE -ne 0) { throw 'Vue dependency installation failed.' }
    } finally {
        Pop-Location
    }
}

& $pythonPath -c `
    "import importlib.util; assert importlib.util.find_spec('fastapi'); assert importlib.util.find_spec('uvicorn')"
if ($LASTEXITCODE -ne 0) {
    throw 'Python runtime dependencies are missing. Run again with -InstallDependencies.'
}
Assert-File (Join-Path $repositoryRoot 'web-client\node_modules\vite\bin\vite.js') `
    'Installed Vite entry point; run again with -InstallDependencies'

if ($UseExistingInfrastructure) {
    if ([string]::IsNullOrWhiteSpace($MySqlUrl)) { $MySqlUrl = Get-EnvironmentValue 'MYSQL_URL' }
    if ([string]::IsNullOrWhiteSpace($MySqlUsername)) {
        $MySqlUsername = Get-EnvironmentValue 'MYSQL_USERNAME'
    }
    if ([string]::IsNullOrWhiteSpace($MySqlPassword)) {
        $MySqlPassword = Get-EnvironmentValue 'MYSQL_PASSWORD'
    }
    if ([string]::IsNullOrWhiteSpace($MySqlUrl) `
            -or [string]::IsNullOrWhiteSpace($MySqlUsername) `
            -or [string]::IsNullOrWhiteSpace($MySqlPassword)) {
        throw 'Existing infrastructure mode requires MYSQL_URL, MYSQL_USERNAME, and MYSQL_PASSWORD.'
    }
    $configuredRedisHost = Get-EnvironmentValue 'REDIS_HOST'
    $configuredRedisPort = Get-EnvironmentValue 'REDIS_PORT'
    $configuredRedisPassword = Get-EnvironmentValue 'REDIS_PASSWORD'
    if ($configuredRedisHost -and -not $PSBoundParameters.ContainsKey('RedisHost')) {
        $RedisHost = $configuredRedisHost
    }
    if ($configuredRedisPort -and -not $PSBoundParameters.ContainsKey('RedisPort')) {
        $RedisPort = [int]$configuredRedisPort
    }
    if ($configuredRedisPassword -and -not $PSBoundParameters.ContainsKey('RedisPassword')) {
        $RedisPassword = $configuredRedisPassword
    }
} elseif (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker is required for the default isolated MySQL/Redis mode.'
}

if ($ValidateOnly) {
    Write-Output 'Local launcher validation PASS.'
    Write-Output "Python: $pythonPath"
    Write-Output "Maven: $mavenPath"
    Write-Output "NPM: $npmPath"
    Write-Output "RAG engine: $RagEngine"
    Write-Output ('Infrastructure: ' + $(if ($UseExistingInfrastructure) { 'existing' } else { 'isolated Docker' }))
    exit 0
}

if (Test-Path -LiteralPath $statePath -PathType Leaf) {
    $previousState = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $previousState.stoppedAt) {
        throw "An active local-launcher state already exists. Run .\scripts\Stop-Local.ps1 first."
    }
}

$ports = @($PythonPort, $JavaPort, $VuePort)
if (-not $UseExistingInfrastructure) { $ports += @($DockerMySqlPort, $DockerRedisPort) }
foreach ($port in $ports) { Assert-PortAvailable $port }

New-Item -ItemType Directory -Force -Path `
    $runtimeRootPath, $logRoot, $javaDocumentRoot, $pythonDocumentRoot, `
    $pythonVectorStoreRoot, `
    $huggingFaceCachePath, $sentenceTransformersCachePath | Out-Null

$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtBytes = New-Object byte[] 32
    $random.GetBytes($jwtBytes)
    $jwtSecret = [Convert]::ToBase64String($jwtBytes)

    if (-not $UseExistingInfrastructure) {
        $databasePasswordBytes = New-Object byte[] 24
        $random.GetBytes($databasePasswordBytes)
        $MySqlPassword = [Convert]::ToBase64String($databasePasswordBytes)
    }
} finally {
    $random.Dispose()
}

$suffix = $PID
$mysqlContainer = $null
$redisContainer = $null

try {
    if (-not $UseExistingInfrastructure) {
        $mysqlContainer = "insurance-local-mysql-$suffix"
        $redisContainer = "insurance-local-redis-$suffix"
        & docker run -d --name $mysqlContainer `
            --label 'insurance.local-launcher=true' `
            --tmpfs '/var/lib/mysql:rw' `
            -e 'MYSQL_DATABASE=insurance_ai' `
            -e 'MYSQL_USER=insurance_local' `
            -e "MYSQL_PASSWORD=$MySqlPassword" `
            -e "MYSQL_ROOT_PASSWORD=$MySqlPassword" `
            -p "127.0.0.1:${DockerMySqlPort}:3306" mysql:8.4 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Unable to start local MySQL container.' }
        $startedContainers += $mysqlContainer

        & docker run -d --name $redisContainer `
            --label 'insurance.local-launcher=true' `
            -p "127.0.0.1:${DockerRedisPort}:6379" redis:7.4-alpine | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Unable to start local Redis container.' }
        $startedContainers += $redisContainer

        $deadline = [DateTime]::UtcNow.AddMinutes(2)
        do {
            & docker exec -e "MYSQL_PWD=$MySqlPassword" $mysqlContainer `
                mysqladmin ping -h 127.0.0.1 -u insurance_local --silent *> $null
            if ($LASTEXITCODE -eq 0) { break }
            Start-Sleep -Milliseconds 500
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($LASTEXITCODE -ne 0) { throw 'Local MySQL readiness timed out.' }

        $MySqlUrl = "jdbc:mysql://127.0.0.1:$DockerMySqlPort/insurance_ai"
        $MySqlUsername = 'insurance_local'
        $RedisHost = '127.0.0.1'
        $RedisPort = $DockerRedisPort
        $RedisPassword = ''
    }

    $python = Start-ManagedProcess $pythonPath `
        @('-m', 'uvicorn', 'api.main:app', '--host', '127.0.0.1', '--port', "$PythonPort") `
        $repositoryRoot `
        @{
            DEEPSEEK_API_KEY = $deepSeekKey
            RAG_ENGINE = $RagEngine
            AI_SERVICE_HOST = '127.0.0.1'
            AI_SERVICE_PORT = "$PythonPort"
            HF_HOME = $huggingFaceCachePath
            SENTENCE_TRANSFORMERS_HOME = $sentenceTransformersCachePath
            DOCUMENT_STORAGE_ROOT = $pythonDocumentRoot
            VECTOR_STORE_ROOT = $pythonVectorStoreRoot
            PYTHONUNBUFFERED = '1'
        } `
        'python'
    Wait-Http "http://127.0.0.1:$PythonPort/internal/v1/health/ready" 300

    $java = Start-ManagedProcess $mavenPath `
        @('spring-boot:run') `
        (Join-Path $repositoryRoot 'java-backend') `
        @{
            MYSQL_URL = $MySqlUrl
            MYSQL_USERNAME = $MySqlUsername
            MYSQL_PASSWORD = $MySqlPassword
            REDIS_HOST = $RedisHost
            REDIS_PORT = "$RedisPort"
            REDIS_PASSWORD = $RedisPassword
            PYTHON_AI_BASE_URL = "http://127.0.0.1:$PythonPort"
            DOCUMENT_STORAGE_ROOT = $javaDocumentRoot
            JWT_SECRET_BASE64 = $jwtSecret
            APP_ENV = 'local'
            JAVA_BACKEND_PORT = "$JavaPort"
        } `
        'java'
    Wait-Http "http://127.0.0.1:$JavaPort/actuator/health/readiness" 120

    $vue = Start-ManagedProcess $npmPath `
        @('run', 'dev', '--', '--host', '127.0.0.1', '--port', "$VuePort", '--strictPort') `
        (Join-Path $repositoryRoot 'web-client') `
        @{ PATH = "$nodeDirectory$([IO.Path]::PathSeparator)$env:PATH" } `
        'vue'
    Wait-Http "http://127.0.0.1:$VuePort/" 30

    $state = [ordered]@{
        repositoryRoot = $repositoryRoot
        runtimeRoot = $runtimeRootPath
        startedAt = [DateTime]::UtcNow.ToString('o')
        ragEngine = $RagEngine
        infrastructureMode = $(if ($UseExistingInfrastructure) { 'existing' } else { 'isolated-docker' })
        containers = @($mysqlContainer, $redisContainer) | Where-Object { $_ }
        processes = @(
            Get-ProcessRecord 'python' $python
            Get-ProcessRecord 'java' $java
            Get-ProcessRecord 'vue' $vue
        )
        urls = @{
            web = "http://127.0.0.1:$VuePort/"
            javaReadiness = "http://127.0.0.1:$JavaPort/actuator/health/readiness"
            pythonReadiness = "http://127.0.0.1:$PythonPort/internal/v1/health/ready"
        }
    }
    $state | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statePath -Encoding UTF8
    $deepSeekKey = $null
    $MySqlPassword = $null
    $jwtSecret = $null

    Write-Output 'Insurance AI Platform is ready.'
    Write-Output "Web: http://127.0.0.1:$VuePort/"
    Write-Output "Logs: $logRoot"
    Write-Output 'Stop: .\scripts\Stop-Local.ps1'
    if (-not $UseExistingInfrastructure) {
        Write-Output 'MySQL and Redis are isolated temporary containers; stopping removes their data.'
    }
} catch {
    $deepSeekKey = $null
    $MySqlPassword = $null
    $jwtSecret = $null
    Remove-OwnedResources
    throw
}
