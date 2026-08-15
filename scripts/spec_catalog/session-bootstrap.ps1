<#
.SYNOPSIS
    One-call session bootstrap for /spec-next and /spec-do (S1596).

.DESCRIPTION
    The start of a ticket session was a fixed ritual of five separate calls - round state,
    device probe, device persist, selection preflight, ticket claim - and each one cost the
    agent a turn. Measured over 2026-08-05..11 the pairs run 59, 63, 44 and 38 times, which is
    a ritual, not an investigation. This script runs the same components in one call and
    returns one JSON payload, so the driver spends one turn instead of five.

    COMPOSITION, NOT REIMPLEMENTATION. Every block shells out to the component that already
    owns that job. Nothing here re-derives ranking, skip-cache policy, release-queue order or
    the drift verdict - a second implementation of "which ticket is next" would drift from the
    first, and four of the five components have live callers outside session start, so a copy
    would not even replace them. Session identity comes from scripts/utils/agent-lock.ps1 for
    the same reason: two scripts already share that resolution and a third copy would drift.

    PARTIAL FAILURE IS VISIBLE. Each block carries its own status, the child's own exit code
    and the child's own reason text, unreinterpreted. A caller that reads only the process exit
    code still learns whether anything failed; a caller that reads the payload learns which.

    The package is additive. Every component keeps its own parameter surface and exit codes and
    stays callable on its own.

.PARAMETER Resume
    Adopt the previous round's session state (Resume verb) instead of starting a new one (Init).

.PARAMETER SkipDevice
    Do not probe the device and do not persist a device state. For a ticket that needs no hardware.

.PARAMETER DeviceTimeoutSec
    Wall-clock bound on the device probe (default 90). On expiry the device block reports
    status=failed with exit 124 and the round continues with DEVICE_ONLINE=false, instead of the
    package hanging with no output (S1633).

.PARAMETER Claim
    Also claim the selected ticket. Off by default: the claim is the one irreversible act in the
    package, and the driver's drift gate sits between selection and claim.

.PARAMETER Exclude
    Ticket ids already processed this run, forwarded to the selection block. CSV or repeated.

.PARAMETER Threshold
    Context-token threshold forwarded to the session block. 0 leaves the component default.

.PARAMETER Reason
    Free text recorded in the lease when -Claim is given.

.PARAMETER Format
    json (default) - one compressed line for the driver to parse.
    table          - a readable per-block summary.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/session-bootstrap.ps1
    Starts a round, probes the device, ranks the queue. Does not claim.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/session-bootstrap.ps1 -Resume -Claim -Exclude S0101,S0102
    Adopts the previous round, re-ranks with two ids excluded, claims the winner.

.EXIT CODES
    0 - every requested block succeeded.
    1 - at least one block failed.
    2 - usage error, or a component script is missing.
    3 - a candidate was selected but the claim was lost to a live sibling session.
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$Resume,

    [switch]$SkipDevice,

    [switch]$Claim,

    [string[]]$Exclude = @(),

    [ValidateRange(5, 600)]
    [int]$DeviceTimeoutSec = 90,

    [int]$Threshold = 0,

    [string]$Reason = 'session-bootstrap',

    [ValidateSet('json', 'table')]
    [string]$Format = 'json'
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\utils\agent-lock.ps1')

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# Component calls run in separate pwsh processes. Keep their fallback identity aligned when
# the host did not supply an agent-session id, so Device and Claim see the state Init created.
if ([string]::IsNullOrWhiteSpace($env:CLAUDE_CODE_SESSION_ID)) {
    $env:CLAUDE_CODE_SESSION_ID = "pid-$PID"
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    'pwsh'
}

$sessionScript = Join-Path $PSScriptRoot 'spec-next-session.ps1'
$selectionScript = Join-Path $PSScriptRoot 'spec-next-preflight.ps1'
$leaseScript = Join-Path $PSScriptRoot 'ticket-lease.ps1'
$deviceScript = Join-Path $root 'scripts\devtest\device-ready.ps1'

foreach ($component in @($sessionScript, $selectionScript, $leaseScript, $deviceScript)) {
    if (-not (Test-Path -LiteralPath $component)) {
        Write-Error "session-bootstrap: component script not found: $component" -ErrorAction Continue
        exit 2
    }
}

$debugPackage = 'com.sza.fastmediasorter.debug'

function New-Block {
    param(
        [Parameter(Mandatory)][ValidateSet('ok', 'failed', 'skipped')][string]$Status,
        [int]$ExitCode = 0,
        [string]$BlockReason = '',
        $Payload = $null
    )
    return [PSCustomObject]@{
        status   = $Status
        exitCode = $ExitCode
        reason   = $BlockReason
        payload  = $Payload
    }
}

function Invoke-Component {
    param(
        [Parameter(Mandatory)][string]$Path,
        [string[]]$Arguments = @()
    )
    # A native command's stderr merged with 2>&1 arrives as ErrorRecord objects, and under
    # $ErrorActionPreference = 'Stop' that terminates this script instead of being captured.
    # The preference is lowered only around the child call, never around our own logic.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $captured = & $pwshExe -NoProfile -File $Path @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    return [PSCustomObject]@{
        output   = ($captured | Out-String).Trim()
        exitCode = $code
    }
}

function Invoke-ComponentBounded {
    param(
        [Parameter(Mandatory)][string]$Path,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory)][int]$TimeoutSec
    )
    # S1633: the device block is the only component that talks to hardware, and a probe that
    # never returns used to hang Stage 0 forever with an empty stdout - no ticket session could
    # start at all, and the symptom read as "the agent went silent" rather than as an error.
    # Two differences from Invoke-Component, both required:
    #   - FILE redirection instead of a pipe. A daemon the probe leaves behind (adb forks one)
    #     can inherit a file handle harmlessly; an inherited PIPE keeps the read side open past
    #     the child's own exit, which is the hang itself.
    #   - a wall-clock bound, so a probe that cannot finish degrades into a failed block rather
    #     than stopping the package. That is the script's stated contract for every other block.
    $outFile = [System.IO.Path]::GetTempFileName()
    $errFile = [System.IO.Path]::GetTempFileName()
    try {
        $proc = Start-Process -FilePath $pwshExe -PassThru -NoNewWindow `
            -ArgumentList (@('-NoProfile', '-File', $Path) + $Arguments) `
            -RedirectStandardOutput $outFile -RedirectStandardError $errFile
        if ($proc.WaitForExit($TimeoutSec * 1000)) {
            $text = ((Get-Content -LiteralPath $outFile, $errFile -Raw -ErrorAction SilentlyContinue) -join "`n").Trim()
            return [PSCustomObject]@{ output = $text; exitCode = $proc.ExitCode }
        }
        # Kill the tree: whatever the probe spawned is what is holding it, and leaving it behind
        # would keep the temp files locked and the hazard alive for the next round.
        try { $proc.Kill($true) } catch { <# raced with its own exit; nothing left to stop #> }
        return [PSCustomObject]@{
            output   = "device probe exceeded ${TimeoutSec}s and was stopped: $Path"
            exitCode = 124
        }
    }
    finally {
        Remove-Item -LiteralPath $outFile, $errFile -Force -ErrorAction SilentlyContinue
    }
}

function Read-ChildJson {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    foreach ($line in ($Text -split "`r?`n")) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith('{') -or $trimmed.StartsWith('[')) {
            try { return ($trimmed | ConvertFrom-Json) } catch { continue }
        }
    }
    return $null
}

# ---------------------------------------------------------------- block: session

$sessionArgs = @('-Verb', $(if ($Resume) { 'Resume' } else { 'Init' }))
if ($Threshold -gt 0) { $sessionArgs += @('-Threshold', "$Threshold") }

$sessionRun = Invoke-Component -Path $sessionScript -Arguments $sessionArgs
$sessionBlock = New-Block `
    -Status $(if ($sessionRun.exitCode -eq 0) { 'ok' } else { 'failed' }) `
    -ExitCode $sessionRun.exitCode `
    -BlockReason $sessionRun.output `
    -Payload (Read-ChildJson -Text $sessionRun.output)

# ---------------------------------------------------------------- block: device

if ($SkipDevice) {
    $deviceBlock = New-Block -Status 'skipped' -BlockReason 'skipped by -SkipDevice'
} else {
    $probeRun = Invoke-ComponentBounded -Path $deviceScript -TimeoutSec $DeviceTimeoutSec -Arguments @(
        '-Package', $debugPackage, '-CheckMcp', '-Json'
    )
    $probe = Read-ChildJson -Text $probeRun.output

    # device-ready reports "no device" at exit 0 with ready=false - absence is an answer, not a
    # failure of the probe. Only a non-zero exit means the probe itself could not run.
    $online = if ($probe -and $probe.ready) { 'true' } else { 'false' }
    $persistArgs = @('-Verb', 'Device', '-Online', $online)
    if ($probe -and $probe.selectedDevice) {
        $persistArgs += @('-SelectedDevice', [string]$probe.selectedDevice)
    }
    $persistRun = Invoke-Component -Path $sessionScript -Arguments $persistArgs

    $deviceFailed = ($probeRun.exitCode -ne 0) -or ($persistRun.exitCode -ne 0)
    $deviceExit = if ($probeRun.exitCode -ne 0) { $probeRun.exitCode } else { $persistRun.exitCode }
    $deviceReason = if ($probeRun.exitCode -ne 0) { $probeRun.output } elseif ($persistRun.exitCode -ne 0) { $persistRun.output } else { [string]$probe.state }

    $deviceBlock = New-Block `
        -Status $(if ($deviceFailed) { 'failed' } else { 'ok' }) `
        -ExitCode $deviceExit `
        -BlockReason $deviceReason `
        -Payload $probe
}

# ---------------------------------------------------------------- block: selection

$selectionArgs = @('-Format', 'json')
$excludeIds = @()
foreach ($entry in $Exclude) {
    foreach ($id in ($entry -split ',')) { if ($id.Trim()) { $excludeIds += $id.Trim() } }
}
if ($excludeIds.Count -gt 0) { $selectionArgs += @('-Exclude', ($excludeIds -join ',')) }

$selectionRun = Invoke-Component -Path $selectionScript -Arguments $selectionArgs
$selection = Read-ChildJson -Text $selectionRun.output
$selectionOk = ($selectionRun.exitCode -eq 0) -and ($null -ne $selection)
$selectionBlock = New-Block `
    -Status $(if ($selectionOk) { 'ok' } else { 'failed' }) `
    -ExitCode $selectionRun.exitCode `
    -BlockReason $(if ($selectionOk) { '' } else { $selectionRun.output }) `
    -Payload $selection

# ---------------------------------------------------------------- block: lease

$claimLost = $false
if (-not $Claim) {
    $leaseBlock = New-Block -Status 'skipped' -BlockReason 'claim not requested'
} elseif (-not $selectionOk -or -not $selection.selected) {
    $leaseBlock = New-Block -Status 'skipped' -BlockReason 'no candidate to claim'
} else {
    $leaseRun = Invoke-Component -Path $leaseScript -Arguments @(
        '-Verb', 'Claim', '-Id', [string]$selection.selected.id, '-Reason', $Reason, '-Json'
    )
    if ($leaseRun.exitCode -eq 3) { $claimLost = $true }
    $leaseBlock = New-Block `
        -Status $(if ($leaseRun.exitCode -eq 0) { 'ok' } else { 'failed' }) `
        -ExitCode $leaseRun.exitCode `
        -BlockReason $(if ($leaseRun.exitCode -eq 0) { '' } else { $leaseRun.output }) `
        -Payload (Read-ChildJson -Text $leaseRun.output)
}

# ---------------------------------------------------------------- verdict

$blocks = [ordered]@{
    session   = $sessionBlock
    device    = $deviceBlock
    selection = $selectionBlock
    lease     = $leaseBlock
}

$failedBlocks = @($blocks.Keys | Where-Object { $blocks[$_].status -eq 'failed' })
$failedOutsideLease = @($failedBlocks | Where-Object { $_ -ne 'lease' })

# A lost claim is a normal outcome of a parallel session, not a defect, so it keeps its own code -
# but only when nothing else failed, because "another session got there first" would be a
# misleading verdict for a run whose session or selection block also broke.
$verdict = if ($failedOutsideLease.Count -gt 0) {
    1
} elseif ($claimLost) {
    3
} elseif ($failedBlocks.Count -gt 0) {
    1
} else {
    0
}

$result = [ordered]@{
    sessionId    = Get-AgentSessionId
    resumed      = [bool]$Resume
    claimed      = ($leaseBlock.status -eq 'ok')
    failedBlocks = $failedBlocks
    exitCode     = $verdict
    session      = $sessionBlock
    device       = $deviceBlock
    selection    = $selectionBlock
    lease        = $leaseBlock
}

if ($Format -eq 'json') {
    [PSCustomObject]$result | ConvertTo-Json -Compress -Depth 6
} else {
    Write-Host "session-bootstrap  session=$($result.sessionId)  verdict=$verdict"
    foreach ($name in $blocks.Keys) {
        $block = $blocks[$name]
        $colour = switch ($block.status) {
            'ok' { 'Green' }
            'skipped' { 'DarkGray' }
            default { 'Red' }
        }
        $suffix = if ($block.reason) { " - $($block.reason -replace "`r?`n", ' ')" } else { '' }
        Write-Host ("  {0,-10} {1,-8} exit={2}{3}" -f $name, $block.status, $block.exitCode, $suffix) -ForegroundColor $colour
    }
    if ($selection -and $selection.selected) {
        Write-Host "  selected   $($selection.selected.id) - $($selection.selected.status)"
    } else {
        Write-Host '  selected   none'
    }
}

if ($verdict -ne 0) {
    Write-Error "session-bootstrap: $($failedBlocks -join ', ') block(s) did not assemble; see the payload for each child's own reason." -ErrorAction Continue
}

exit $verdict
