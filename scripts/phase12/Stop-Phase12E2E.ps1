[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runtimeRoot = Join-Path $repositoryRoot '.phase12-runtime'
$statePath = Join-Path $runtimeRoot 'state.json'

if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    throw "Phase 12 state file not found: $statePath"
}

$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
if ($state.repositoryRoot -ne $repositoryRoot) {
    throw 'Refusing cleanup because the recorded workspace does not match this repository.'
}

foreach ($entry in $state.processes) {
    $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    if ($null -eq $process) { continue }
    $actualPath = $process.Path
    if ($actualPath -and $actualPath -ne $entry.executable) {
        throw "Refusing to stop reused PID $($entry.id): executable mismatch."
    }
    Stop-Process -Id $entry.id -Force
}

foreach ($container in @($state.mysqlContainer, $state.redisContainer)) {
    if (-not $container) { continue }
    $ownedContainerId = & docker ps -aq `
        --filter "name=^/$container$" `
        --filter 'label=insurance.phase=12'
    if ($LASTEXITCODE -eq 0 -and $ownedContainerId) {
        & docker rm -f $container | Out-Null
    }
}

$state | Add-Member -NotePropertyName stoppedAt -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
$state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
Write-Output 'Phase 12 E2E environment stopped; ignored runtime logs were retained.'
