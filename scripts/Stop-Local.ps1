[CmdletBinding()]
param(
    [string]$RuntimeRoot
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($RuntimeRoot)) {
    $RuntimeRoot = Join-Path $repositoryRoot '.local-runtime'
}
$runtimeRootPath = [IO.Path]::GetFullPath($RuntimeRoot)
$statePath = Join-Path $runtimeRootPath 'state.json'

if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    throw "Local launcher state file not found: $statePath"
}

$state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($state.repositoryRoot -ne $repositoryRoot -or $state.runtimeRoot -ne $runtimeRootPath) {
    throw 'Refusing cleanup because the recorded repository or runtime root does not match.'
}
if ($state.stoppedAt) {
    Write-Output "Local environment was already stopped at $($state.stoppedAt)."
    exit 0
}

function Stop-ProcessTree([int]$processId) {
    try {
        $children = Get-CimInstance Win32_Process `
            -Filter "ParentProcessId=$processId" -ErrorAction SilentlyContinue
        foreach ($child in $children) { Stop-ProcessTree ([int]$child.ProcessId) }
    } catch {
        # The verified owned root process is still stopped if enumeration is unavailable.
    }
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}

foreach ($entry in $state.processes) {
    $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    if ($null -eq $process) { continue }
    $process.Refresh()
    $actualPath = $process.Path
    $actualStart = $process.StartTime.ToUniversalTime()
    $expectedStart = [DateTime]::Parse($entry.startTimeUtc).ToUniversalTime()
    if ($actualPath -and -not [string]::Equals(
            $actualPath, $entry.executable, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to stop reused PID $($entry.id): executable mismatch."
    }
    if ([Math]::Abs(($actualStart - $expectedStart).TotalSeconds) -gt 2) {
        throw "Refusing to stop reused PID $($entry.id): start time mismatch."
    }
    Stop-ProcessTree ([int]$entry.id)
}

foreach ($container in $state.containers) {
    if (-not $container) { continue }
    $ownedContainerId = & docker ps -aq `
        --filter "name=^/$container$" `
        --filter 'label=insurance.local-launcher=true'
    if ($LASTEXITCODE -eq 0 -and $ownedContainerId) {
        & docker rm -f $container | Out-Null
    }
}

$state | Add-Member -NotePropertyName stoppedAt `
    -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
$state | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statePath -Encoding UTF8
Write-Output 'Insurance AI Platform local processes stopped. Runtime logs were retained.'
