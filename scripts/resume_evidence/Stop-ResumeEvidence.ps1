[CmdletBinding()]
param(
    [string]$RuntimeRoot = [IO.Path]::Combine(
        [IO.Path]::GetTempPath(), 'insurance-resume-evidence')
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runtimeRootPath = [IO.Path]::GetFullPath($RuntimeRoot)
$statePath = Join-Path $runtimeRootPath 'state.json'

if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    throw "Resume Evidence state file not found: $statePath"
}

$state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($state.repositoryRoot -ne $repositoryRoot -or $state.runtimeRoot -ne $runtimeRootPath) {
    throw 'Refusing cleanup because the recorded workspace or runtime root does not match.'
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
        --filter 'label=insurance.validation=resume-evidence'
    if ($LASTEXITCODE -eq 0 -and $ownedContainerId) {
        & docker rm -f $container | Out-Null
    }
}

$state | Add-Member -NotePropertyName stoppedAt `
    -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
$state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
Write-Output 'Resume Evidence environment stopped; local runtime logs were retained.'
