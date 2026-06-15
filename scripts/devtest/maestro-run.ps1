<#
.SYNOPSIS
  Run a Maestro YAML flow against a device and emit a compact, off-context verdict.

.DESCRIPTION
  The execution engine for the repeatable regression subset of the device-test sweep
  (strategic spec S0420). Drives per-tap automation out of the LLM loop: Maestro matches
  elements by id/text natively, so a flow runs at native speed and costs ~0 LLM tokens.

  The full Maestro output (per-step trace, failures, timing) is written to a log file
  under temp/ - it is NOT echoed to stdout, to keep it out of the LLM context. stdout
  carries only the one-line verdict (or, with -Json, a single machine object).

  Targeting parity with device-ready.ps1: same binary-discovery pattern, same -Json
  contract, same stable exit-code table.

  Exit codes:
    0 - flow passed (all steps / assertions green)
    1 - bad arguments or flow file not found
    2 - Maestro CLI not found (checked PATH, %USERPROFILE%\.maestro\bin, MAESTRO_HOME)
    3 - flow failed (Maestro ran but a step / assertion failed)
    4 - Maestro execution error (no device, install/runtime error - flow never completed)

  Human output:  one verdict line; log path printed.
  Machine output (with -Json): single JSON object on stdout, all human noise suppressed.

.PARAMETER Flow
  Path to the Maestro flow YAML (e.g. scripts/devtest/maestro/S0398.yaml). Required.

.PARAMETER DeviceId
  Specific adb device id (the "serial" from `adb devices`). Passed to Maestro as the
  global --device flag. Omit when exactly one device is online.

.PARAMETER Env
  Optional Maestro flow variables, KEY=VALUE pairs, forwarded as repeated `-e KEY=VALUE`.

.PARAMETER Json
  Emit a single JSON object instead of a human-readable verdict line.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/maestro-run.ps1 -Flow scripts/devtest/maestro/S0398.yaml
  Run the S0398 flow on the sole online device.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/maestro-run.ps1 -Flow scripts/devtest/maestro/S0398.yaml -DeviceId emulator-5554 -Json
  Machine-readable run pinned to a specific emulator.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Flow,
    [string]$DeviceId,
    [string[]]$Env,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

# ---------- result shape ----------

$script:result = [ordered]@{
    passed       = $false
    exitCode     = 0
    flow         = $Flow
    maestroPath  = $null
    selectedDevice = $DeviceId
    logFile      = $null
    maestroExit  = $null
    reason       = $null
}

function Write-Line {
    param([string]$Text, [string]$Color = 'White')
    if (-not $Json) { Write-Host $Text -ForegroundColor $Color }
}

function Fail {
    param([int]$Code, [string]$Reason)
    $script:result.exitCode = $Code
    $script:result.reason   = $Reason
    if ($Json) {
        $script:result | ConvertTo-Json -Compress
    } else {
        Write-Host "FAIL ($Code) - $Reason" -ForegroundColor Red
    }
    exit $Code
}

function Find-Maestro {
    # Priority: PATH, then MAESTRO_HOME\bin, then the default ~/.maestro/bin install.
    foreach ($name in 'maestro', 'maestro.bat', 'maestro.cmd', 'maestro.ps1') {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    $roots = @()
    if ($env:MAESTRO_HOME) { $roots += $env:MAESTRO_HOME }
    if ($env:USERPROFILE)  { $roots += (Join-Path $env:USERPROFILE '.maestro') }
    if ($env:HOME)         { $roots += (Join-Path $env:HOME '.maestro') }
    foreach ($root in $roots) {
        foreach ($leaf in 'bin\maestro.bat', 'bin\maestro.cmd', 'bin\maestro') {
            $candidate = Join-Path $root $leaf
            if (Test-Path -Path $candidate -PathType Leaf) { return $candidate }
        }
    }
    return $null
}

# ---------- step 1: flow file ----------

if (-not (Test-Path -Path $Flow -PathType Leaf)) {
    Fail 1 "flow file not found: $Flow"
}
$flowFull = (Resolve-Path -Path $Flow).Path

# ---------- step 2: Maestro CLI ----------

$maestro = Find-Maestro
if (-not $maestro) {
    Fail 2 "Maestro CLI not found (checked PATH, MAESTRO_HOME, %USERPROFILE%\.maestro\bin) - see scripts/devtest/maestro/README.md"
}
$script:result.maestroPath = $maestro
Write-Line "OK maestro: $maestro" 'Green'

# ---------- step 3: run ----------

# Log lands under temp/ (repo convention: no artifacts at root). Off-context by design.
$repoRoot = (Resolve-Path -Path (Join-Path $PSScriptRoot '..\..')).Path
$tempDir  = Join-Path $repoRoot 'temp'
if (-not (Test-Path -Path $tempDir -PathType Container)) {
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
}
$flowName = [System.IO.Path]::GetFileNameWithoutExtension($flowFull)
$stamp    = (Get-Date).ToString('yyyyMMdd_HHmm')
$logFile  = Join-Path $tempDir "$($flowName)_maestro_$stamp.log"
$script:result.logFile = $logFile

# Global flags (--device) precede the `test` subcommand in the Maestro CLI.
$argList = @()
if ($DeviceId) { $argList += @('--device', $DeviceId) }
$argList += @('test')
foreach ($pair in @($Env)) {
    if ($pair) { $argList += @('-e', $pair) }
}
$argList += $flowFull

Write-Line "RUN maestro $($argList -join ' ')" 'Cyan'

# Capture combined stdout+stderr to the log; keep it out of this process's stdout.
& $maestro @argList *> $logFile
$maestroExit = $LASTEXITCODE
$script:result.maestroExit = $maestroExit

# ---------- step 4: verdict ----------

if ($maestroExit -eq 0) {
    $script:result.passed   = $true
    $script:result.exitCode = 0
    if ($Json) {
        $script:result | ConvertTo-Json -Compress
    } else {
        Write-Host "PASS - flow=$flowName device=$(if($DeviceId){$DeviceId}else{'(auto)'}) log=$logFile" -ForegroundColor Green
    }
    exit 0
}

# Non-zero: distinguish a real assertion/step failure from an infra error (no device, etc.).
# Maestro prints "no devices"/"Unable to launch"/connection text on infra failure; a step
# failure prints "Assertion is false"/"Element not found"/a flow step trace.
$logText = ''
if (Test-Path -Path $logFile -PathType Leaf) {
    $logText = (Get-Content -Path $logFile -Raw -ErrorAction SilentlyContinue)
}
$infraPattern = 'no devices|no connected device|not enough devices|Missing \d+ device|it is not connected|Unable to launch|Unable to find|connection refused|locked a portion of the file|MAESTRO_CLI|java\.lang|ADB'
if ($logText -match $infraPattern -and $logText -notmatch 'Assertion|Element .*not|not visible|did not') {
    Fail 4 "Maestro execution error (no device or runtime error); see $logFile"
}
Fail 3 "flow failed (step/assertion); see $logFile"
