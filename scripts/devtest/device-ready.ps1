<#
.SYNOPSIS
  Pre-flight readiness check for on-device testing skills (/spec-test-device).

.DESCRIPTION
  Single point of truth that answers: can the agent actually run a UI scenario right now?
  Verifies, in order:
    1. ADB executable is reachable.
    2. At least one device is online (DeviceId narrows the selection).
    2b. If -Module is given, only devices of that module's form factor are candidates.
    3. If -Package is given, the package is installed on the selected device.
    4. If -ExpectedVersion is given, the installed package's versionName matches.
    5. If -CheckMcp is set, the Maestro CLI is resolvable and carries the `mcp` subcommand that
       .mcp.json launches (S2918 replaced mobile-mcp with it).

  This is a STATUS QUERY (S1338 phase 09). "No device attached" is a normal answer to it,
  not a failure of the query, so the readiness verdict travels in the payload and the
  process exits 0 whenever the state could be determined. Read `ready` (bool) and `state`
  (string); `statusCode` carries the legacy numeric code for anything still keyed on it.
  Pass -StrictExit to restore the old behaviour where the numeric code IS the exit code.

  Exit codes:
    0 - state determined and reported: ready, or not-ready with a `state`/`reason`
    2 - the probe itself could not run

  With -StrictExit the not-ready states exit with their legacy code instead:
    1 - ADB executable not found          (state: no-adb)
    2 - no online device                  (state: no-device)
    3 - multiple online devices, no -DeviceId (state: multiple-devices)
    4 - target package not installed      (state: package-not-installed)
    5 - installed versionName mismatch    (state: version-mismatch)
    6 - Maestro MCP not resolvable        (state: mcp-unavailable)
    7 - every online device is leased by another session (state: all-devices-leased)
        or the named -DeviceId is                        (state: device-leased)
        Reachable only under -ClaimFree. Deliberately distinct from no-device and from
        multiple-devices (S1926): "there is nothing to test on" ends the device stage, while
        "somebody else is on all of them" means try again later.
    8 - devices are online but none is the form factor -Module asked for
                                          (state: form-factor-mismatch)
        Reachable only when -Module is passed. Distinct from no-device for exactly S1926's
        reason (S2600): "nothing to test on" ends the device stage, while "the wrong kind of
        device is attached" means boot the other emulator or attach the other device.

  The form factor of the selected device is reported unconditionally in `formFactor`
  ('watch' or 'phone'), with or without -Module. It cannot be derived from -Package: both
  modules publish under one applicationId (S1681), so the package name is the same on a
  phone and on a watch, and reading it as a form factor put a watch-sourced verdict into a
  phone ticket's spec once already (S2600).

  Human output:  one line per check + final verdict line.
  Machine output (with -Json): single JSON object on stdout, all human noise suppressed.

.PARAMETER DeviceId
  Specific adb device id (the "serial" from `adb devices`). Required when multiple devices are online,
  unless -ClaimFree is passed.

.PARAMETER ClaimFree
  Take a device lease (S1926) instead of refusing when several devices are online: walk the online
  devices and keep the first one this session can claim. Opt-in on purpose - without it the probe
  answers exactly as it did before the lease existed. With a -DeviceId it claims that device and
  reports state `device-leased` if a sibling holds it.

.PARAMETER Package
  Target package name to verify is installed (e.g. com.sza.fastmediasorter, com.sza.fastmediasorter.debug).

.PARAMETER ExpectedVersion
  Expected versionName the installed Package must report. Comparison is exact string match.

.PARAMETER CheckMcp
  Best-effort check that the Maestro MCP server is launchable: the Maestro CLI resolves (PATH,
  MAESTRO_HOME\bin, %USERPROFILE%\.maestro\bin - the order scripts/devtest/maestro-run.ps1 uses)
  and prints the `maestro mcp` usage line. Does not start the server.

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.PARAMETER StrictExit
  Legacy fail-fast mode: a not-ready state exits with its numeric code (1..6) instead of 0.
  Only for a caller that cannot read the payload and must branch on $LASTEXITCODE.

.PARAMETER Module
  Which module the caller intends to test: app_v2 (phone) or wear (watch). Narrows the candidate
  devices to that form factor before any selection happens, so a phone and a paired watch both
  online resolve to the right one instead of refusing with multiple-devices. No default on
  purpose (S2600): absent, the probe answers exactly as it always has, and every existing caller
  keeps its behaviour unchanged.

.PARAMETER ReuseFinding
  Opt-in finding reuse (S2409): when an alive finding for the requested topic and canonical request
  string exists, take its EXPENSIVE checks (package, version, mcp) instead of repeating them.
  The device list and the form factor are always measured fresh (S2600) - a finding is another
  session's answer about the device THEY wanted, and letting it choose is the thing that put a
  watch under a phone ticket.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/device-ready.ps1
  Quick sanity: ADB up, one device online.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -ExpectedVersion 2.62.0501.151
  Full pre-flight for a /spec-test-device run.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -CheckMcp -Json
  Machine-readable readiness probe including Maestro MCP resolvability.
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [string]$Package,
    [string]$ExpectedVersion,
    [switch]$CheckMcp,
    [switch]$Json,
    [switch]$StrictExit,
    # S1926. Opt-in, and opt-in on purpose (ADR-2): sibling sessions are running right now, and
    # silently changing a shared probe's answer mid-run is the same class of surprise the device
    # lease exists to remove. Without this switch the probe behaves exactly as it always has,
    # `multiple-devices` included.
    [switch]$ClaimFree,
    # S2855. Opt-in for the same reason as -ClaimFree: attach the selected device's last install
    # mark from the device registry, so "free, and it already carries the build I need" is answerable
    # by the probe the caller already runs. Without it the answer object carries no registry field.
    [switch]$WithRegistry,
    # S2409. Opt-in reuse mode.
    [switch]$ReuseFinding,
    # S2600. Explicit only - see .PARAMETER Module for why there is no default.
    [ValidateSet('app_v2', 'wear')]
    [string]$Module
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

. "$PSScriptRoot/lib/device-form-factor.ps1"
. "$PSScriptRoot/lib/device-store-paths.ps1"

# ---------- helpers ----------

$script:result = [ordered]@{
    ready           = $false
    state           = 'unknown'
    statusCode      = 0
    exitCode        = 0   # retained for callers that already read this field; mirrors statusCode
    adbPath         = $null
    devices         = @()
    selectedDevice  = $null
    formFactor      = $null
    module          = if ($Module) { $Module } else { $null }
    package         = $Package
    installed       = $null
    versionName     = $null
    expectedVersion = $ExpectedVersion
    versionMatch    = $null
    # $true when -CheckMcp found the Maestro CLI with its `mcp` subcommand; $null when not asked.
    mcpResolvable   = $null
    reused          = $false
    reusedFrom      = $null
    reason          = $null
}

function Get-CanonicalReadyRequest {
    param(
        [string]$DeviceId,
        [string]$Package,
        [string]$ExpectedVersion,
        [bool]$CheckMcp
    )
    $dev = if ($DeviceId) { $DeviceId } else { '<any>' }
    $pkg = if ($Package) { $Package } else { '<none>' }
    $ver = if ($ExpectedVersion) { $ExpectedVersion } else { '<none>' }
    # 'maestro' and not 'true': a finding written while the check meant mobile-mcp must not be reused.
    $mcp = if ($CheckMcp) { 'mcp:maestro' } else { 'mcp:false' }
    return "device-ready.ps1 -DeviceId $dev -Package $pkg -ExpectedVersion $ver -CheckMcp $mcp"
}

if ($MyInvocation.InvocationName -eq '.') { return }

function Get-ReuseCandidate {
    <#
        Looks a live finding up and returns it, or $null. LOOKUP ONLY - it deliberately does not
        answer, because S2600's incident was this lookup answering: it named emulator-5554 with an
        empty `devices` list, from a 23-minute-old finding belonging to another session, for a
        ticket whose subject was the phone. Rule 34 lets another agent's finding spare us cheap
        idempotent WORK, never carry a verdict, and choosing the device IS the verdict here. So the
        caller below enumerates devices and reads the form factor itself, and consults this only to
        skip the expensive checks (pm list packages, dumpsys package, maestro mcp).
    #>
    if (-not $ReuseFinding) { return $null }
    try {
        $storeScript = Join-Path $PSScriptRoot '..\utils\agent-chat-store.ps1'
        if (-not (Test-Path -LiteralPath $storeScript)) { return $null }
        . $storeScript
        $topic = if ($DeviceId) { "device:$DeviceId" } else { "device:*" }
        $req = Get-CanonicalReadyRequest -DeviceId $DeviceId -Package $Package -ExpectedVersion $ExpectedVersion -CheckMcp $CheckMcp
        $match = Get-AgentChatCoveringFinding -Topic $topic -Request $req
        if ($null -eq $match) { return $null }

        $foundSerial = [string](Get-AgentChatProp $match 'device' '')
        if (-not $foundSerial -and $DeviceId) { $foundSerial = $DeviceId }
        if (-not $foundSerial) { return $null }

        $agentObj = Get-AgentChatProp $match 'agent'
        $atUtc = [DateTime](Get-AgentChatProp $match 'atUtc' ([DateTime]::UtcNow))
        return [pscustomobject]@{
            serial = $foundSerial
            author = [string](Get-AgentChatProp $agentObj 'name' ([string](Get-AgentChatProp $agentObj 'id' '?')))
            age    = Format-AgentChatAge ([double]([DateTime]::UtcNow - $atUtc).TotalMinutes)
        }
    }
    catch {
        # Reuse is an optimisation. A malformed or unreadable chat store must cost the full probe
        # below, never the answer.
        return $null
    }
}

function Write-Line {
    param([string]$Text, [string]$Color = 'White')
    if (-not $Json) { Write-Host $Text -ForegroundColor $Color }
}

function Stop-NotReady {
    # Reports a determined not-ready state. The query succeeded - it is the device that is
    # not ready - so this exits 0 unless the caller asked for the legacy fail-fast codes.
    param([int]$Code, [string]$State, [string]$Reason)
    $script:result.state      = $State
    $script:result.statusCode = $Code
    $script:result.exitCode   = $Code
    $script:result.reason     = $Reason
    if ($Json) {
        $script:result | ConvertTo-Json -Compress
    } else {
        Write-Host "NOT READY ($State) - $Reason" -ForegroundColor Yellow
    }
    if ($StrictExit) { exit $Code }
    exit 0
}

function Add-RegistryMark {
    # S2855, opt-in under -WithRegistry only: attach the device's last install mark from the
    # device registry. Strictly read-only - a missing or unreadable record answers 'absent' and
    # the store directory is never created here, because the probe must not leave artifacts
    # behind any more than the monitor writer does.
    param([Parameter(Mandatory)][string]$Serial)
    # The store's address and the serial encoding both come from the declaration (S3036) - this
    # site used to spell out the path AND the encoding, which made it the third copy of each.
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $recordPath = Get-DeviceStoreRecordPath -RepoRoot $repoRoot -Store Registry -Serial $Serial
    $script:result.registry = 'absent'
    if (-not (Test-Path -LiteralPath $recordPath)) { return }
    try {
        $record = Get-Content -LiteralPath $recordPath -Raw -ErrorAction Stop | ConvertFrom-Json
        $mark = $record.lastInstall
        if ($null -ne $mark) {
            $script:result.registry = [pscustomobject]@{
                package     = [string]$mark.package
                versionName = [string]$mark.versionName
                flavor      = [string]$mark.flavor
                buildType   = [string]$mark.buildType
                installedAt = $mark.installedAt
                recordedBy  = [string]$mark.recordedBy
            }
        }
    }
    catch { $script:result.registry = 'absent' }
}

function Find-Adb {
    # Priority: env ANDROID_HOME / ANDROID_SDK_ROOT, then PATH, then well-known Windows path.
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) {
            $candidate = Join-Path $root 'platform-tools\adb.exe'
            if (Test-Path -Path $candidate -PathType Leaf) { return $candidate }
        }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path -Path $known -PathType Leaf) { return $known }

    return $null
}

# ---------- step 1: ADB ----------

$adb = Find-Adb
if (-not $adb) {
    Stop-NotReady 1 'no-adb' "adb.exe not found (checked ANDROID_HOME, ANDROID_SDK_ROOT, PATH, %LOCALAPPDATA%\Android\Sdk\platform-tools)"
}
$script:result.adbPath = $adb
Write-Line "OK adb: $adb" 'Green'

# Generous on purpose: a cold `adb start-server` on a loaded machine takes seconds, and this
# bound exists to stop an indefinite hang, not to police a slow start.
$script:AdbServerStartTimeoutMs = 20000

function Start-AdbServerDetached([string]$AdbPath) {
    # S1633: `adb devices` auto-starts the `adb fork-server` daemon when none is running, and
    # that daemon INHERITS this process's stdout pipe and then never exits. The capture below
    # waits for EOF on a pipe whose write end the daemon holds open for its whole life, so the
    # adb client exits in milliseconds while the caller hangs - observed 2613 s with not one
    # byte of output, which read as "the agent went silent" rather than as an error.
    # Starting the server through Start-Process hands the daemon file handles instead of our
    # pipe, so every later capture sees EOF and returns. Already-running server: a no-op.
    $outFile = [System.IO.Path]::GetTempFileName()
    $errFile = [System.IO.Path]::GetTempFileName()
    try {
        $proc = Start-Process -FilePath $AdbPath -ArgumentList 'start-server' -NoNewWindow -PassThru `
            -RedirectStandardOutput $outFile -RedirectStandardError $errFile
        if (-not $proc.WaitForExit($script:AdbServerStartTimeoutMs)) {
            try { $proc.Kill() } catch { <# already gone between the timeout and the kill #> }
        }
    }
    catch {
        # Deliberately non-fatal: this call only removes a hazard. If it cannot run, the probe
        # below still succeeds whenever a server is already up, which is the common case, and
        # the caller's own timeout covers the rest.
        Write-Line "adb start-server could not be pre-started: $($_.Exception.Message)" 'DarkYellow'
    }
    finally {
        Remove-Item -LiteralPath $outFile, $errFile -Force -ErrorAction SilentlyContinue
    }
}

# ---------- step 2: devices ----------

Start-AdbServerDetached -AdbPath $adb
$raw = & $adb devices 2>$null
if ($LASTEXITCODE -ne 0) { Stop-NotReady 1 'no-adb' "adb devices returned exit $LASTEXITCODE" }

# parse skipping the "List of devices attached" header
$lines = $raw -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^\s*List of devices' }
$devices = foreach ($line in $lines) {
    $parts = ($line -split "\s+", 2) | Where-Object { $_ }
    if ($parts.Count -ge 2 -and $parts[1] -eq 'device') {
        [pscustomobject]@{ id = $parts[0]; state = $parts[1] }
    }
}
$devices = @($devices)
$script:result.devices = $devices | ForEach-Object { $_.id }

if ($devices.Count -eq 0) {
    Stop-NotReady 2 'no-device' "no online device (boot an emulator or connect a phone, then re-run)"
}

function Get-DevicePreferenceOrder {
    <#
        S1926 section 5.3: the ONE place the choosing order lives. A later preference - favour an
        emulator over the owner's physical phone, or a device that already carries the package -
        belongs here, so it cannot drift apart across the callers that pick a device.
        Today the order is "as adb listed them".

        S2600 narrowed what reaches this function without moving the rule: the -Module form-factor
        filter below runs first and decides ELIGIBILITY, which is a different question from order -
        a device of the wrong form factor is not a worse candidate, it is not a candidate.
    #>
    param([Parameter(Mandatory)]$Candidates)
    return @($Candidates)
}

function Request-DeviceLease {
    <#
        Returns $true when this session now holds the lease on $Serial. Exit 3 is the normal "a
        sibling got there first" answer, not a fault. A missing lease script is not fatal either:
        the probe still worked before the lease existed, and refusing to answer because an optional
        coordination file is absent would be worse than the conflict it prevents.
    #>
    param([Parameter(Mandatory)][string]$Serial)
    $leaseScript = Join-Path $PSScriptRoot 'device-lease.ps1'
    if (-not (Test-Path -LiteralPath $leaseScript)) { return $true }
    & pwsh -NoProfile -File $leaseScript -Verb Claim -Id $Serial -Reason 'device-ready' *> $null
    return ($LASTEXITCODE -eq 0)
}

# ---------- step 2b: form factor (S2600) ----------

# Read for every online device, not only the chosen one: the reason to read it at all is to CHOOSE,
# and the refusal below has to be able to say what each rejected device actually was.
$formFactors = @{}
foreach ($device in $devices) {
    $formFactors[$device.id] = Get-DeviceFormFactor -Adb $adb -Serial $device.id
}

$candidates = $devices
if ($Module) {
    $wanted = if ($Module -eq 'wear') { 'watch' } else { 'phone' }
    $candidates = @($devices | Where-Object { Test-FormFactorMatch -FormFactor $formFactors[$_.id] -Module $Module })
    if ($candidates.Count -eq 0) {
        $seen = (($devices | ForEach-Object { "$($_.id)=$($formFactors[$_.id])" }) -join ', ')
        Stop-NotReady 8 'form-factor-mismatch' "-Module $Module needs a $wanted and no online device is one (online: $seen); boot a $wanted emulator or attach a $wanted, then re-run"
    }
    Write-Line "OK form factor: $($candidates.Count) of $($devices.Count) device(s) match -Module $Module ($wanted)" 'Green'
}

# ---------- step 2c: reuse decision (S2409, amended by S2600) ----------

# Reached only after the device list and every form factor were measured THIS run, so a reused
# answer can no longer name a device the probe did not see, nor one the caller's -Module excludes.
$reuse = Get-ReuseCandidate
if ($null -ne $reuse) {
    $reuseDevice = $candidates | Where-Object { $_.id -eq $reuse.serial } | Select-Object -First 1
    if (-not $reuseDevice) {
        Write-Line "ignoring finding from $($reuse.author) ($($reuse.age) ago): $($reuse.serial) is not an eligible device now - probing fresh" 'DarkYellow'
    }
    elseif ($ClaimFree -and -not (Request-DeviceLease -Serial $reuseDevice.id)) {
        # A sibling took it since the finding was written. The full probe below reaches the proper
        # all-devices-leased / device-leased answer, which this shortcut cannot express.
        Write-Line "ignoring finding from $($reuse.author) ($($reuse.age) ago): $($reuse.serial) is leased by another session - probing fresh" 'DarkYellow'
    }
    else {
        if ($WithRegistry) { Add-RegistryMark -Serial $reuseDevice.id }
        $script:result.ready          = $true
        $script:result.state          = 'ready'
        $script:result.selectedDevice = $reuseDevice.id
        $script:result.formFactor     = $formFactors[$reuseDevice.id]
        $script:result.installed      = [bool]$Package
        $script:result.versionName    = if ($ExpectedVersion) { $ExpectedVersion } else { $null }
        $script:result.versionMatch   = [bool]$ExpectedVersion
        $script:result.mcpResolvable  = if ($CheckMcp) { $true } else { $null }
        $script:result.reused         = $true
        $script:result.reusedFrom     = "$($reuse.author) ($($reuse.age) ago)"

        if ($Json) {
            $script:result | ConvertTo-Json -Compress
        } else {
            Write-Host "READY (reused from $($reuse.author), $($reuse.age) ago) - device=$($reuseDevice.id) ff=$($script:result.formFactor)$(if($Package){" pkg=$Package"})$(if($ExpectedVersion){" v=$ExpectedVersion"})" -ForegroundColor Cyan
        }
        exit 0
    }
}

$selected = $null
# Every branch below chooses from $candidates, never from $devices: without -Module the two are the
# same list, and with it the filter has already run, so no branch can hand back a device of the
# form factor the caller ruled out (S2600).
if ($DeviceId) {
    $selected = $candidates | Where-Object { $_.id -eq $DeviceId } | Select-Object -First 1
    if (-not $selected) {
        # An online device excluded by -Module is a different answer from an absent one, and saying
        # 'not online' about a device sitting in `adb devices` is the lie this ticket exists for.
        $named = $devices | Where-Object { $_.id -eq $DeviceId } | Select-Object -First 1
        if ($named) {
            Stop-NotReady 8 'form-factor-mismatch' "device '$DeviceId' is online but is a $($formFactors[$DeviceId]), and -Module $Module needs a $(if($Module -eq 'wear'){'watch'}else{'phone'})"
        }
        Stop-NotReady 2 'no-device' "device '$DeviceId' is not online (online: $($devices.id -join ', '))"
    }
    if ($ClaimFree -and -not (Request-DeviceLease -Serial $selected.id)) {
        Stop-NotReady 7 'device-leased' "device '$($selected.id)' is leased by another session; run device-lease.ps1 -Verb Status to see who"
    }
} elseif ($ClaimFree) {
    # The claim itself arbitrates - there is deliberately no "list the free ones, then take one",
    # because the gap between those two calls is exactly how two sessions take one device. Walk the
    # candidates and keep the first one that lets us claim it.
    $contested = @()
    foreach ($candidate in (Get-DevicePreferenceOrder -Candidates $candidates)) {
        if (Request-DeviceLease -Serial $candidate.id) { $selected = $candidate; break }
        $contested += $candidate.id
    }
    if (-not $selected) {
        Stop-NotReady 7 'all-devices-leased' "every online device is leased by another session ($($contested -join ', ')); this is not 'no device' - retry later or run device-lease.ps1 -Verb Status"
    }
} elseif ($candidates.Count -gt 1) {
    Stop-NotReady 3 'multiple-devices' "multiple online devices ($($candidates.id -join ', ')); pass -DeviceId, -Module to narrow by form factor, or -ClaimFree to take a free one"
} else {
    $selected = $candidates[0]
}
$script:result.selectedDevice = $selected.id
$script:result.formFactor     = $formFactors[$selected.id]
Write-Line "OK device: $($selected.id) ($($script:result.formFactor))" 'Green'

# ---------- step 3 + 4: package + version ----------

if ($Package) {
    # `pm list packages` returns lines like "package:com.foo.bar"
    $pmRaw = & $adb -s $selected.id shell pm list packages $Package 2>$null
    $installed = $false
    foreach ($pmLine in ($pmRaw -split "`r?`n")) {
        if ($pmLine.Trim() -eq "package:$Package") { $installed = $true; break }
    }
    $script:result.installed = $installed
    if (-not $installed) {
        Stop-NotReady 4 'package-not-installed' "package '$Package' not installed on $($selected.id)"
    }
    Write-Line "OK package: $Package" 'Green'

    if ($ExpectedVersion) {
        # `dumpsys package <pkg>` includes a "versionName=..." line.
        $dump = & $adb -s $selected.id shell dumpsys package $Package 2>$null
        $vLine = ($dump -split "`r?`n" | Where-Object { $_ -match 'versionName=' } | Select-Object -First 1)
        $current = $null
        if ($vLine) {
            $m = [regex]::Match($vLine, 'versionName=([^\s]+)')
            if ($m.Success) { $current = $m.Groups[1].Value }
        }
        $script:result.versionName  = $current
        $script:result.versionMatch = ($current -eq $ExpectedVersion)
        if ($current -ne $ExpectedVersion) {
            Stop-NotReady 5 'version-mismatch' "versionName mismatch: installed='$current' expected='$ExpectedVersion'"
        }
        Write-Line "OK version: $current" 'Green'
    }
}

# ---------- step 5: Maestro MCP resolvability ----------

function Find-Maestro {
    # Same order as scripts/devtest/maestro-run.ps1: PATH, MAESTRO_HOME\bin, the default install.
    foreach ($name in 'maestro', 'maestro.bat', 'maestro.cmd') {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    $roots = @()
    if ($env:MAESTRO_HOME) { $roots += $env:MAESTRO_HOME }
    if ($env:USERPROFILE) { $roots += (Join-Path $env:USERPROFILE '.maestro') }
    foreach ($root in $roots) {
        foreach ($leaf in 'bin\maestro.bat', 'bin\maestro.cmd', 'bin\maestro') {
            $candidate = Join-Path $root $leaf
            if (Test-Path -Path $candidate -PathType Leaf) { return $candidate }
        }
    }
    return $null
}

if ($CheckMcp) {
    $maestroPath = Find-Maestro
    if (-not $maestroPath) {
        Stop-NotReady 6 'mcp-unavailable' "Maestro CLI not found (PATH, MAESTRO_HOME, %USERPROFILE%\.maestro\bin) - see scripts/devtest/maestro/README.md"
    }
    # `maestro mcp` has no --help flag: it rejects the option with exit 2 but still prints its usage,
    # so the usage line is the evidence and the exit code is not.
    $usage = & cmd /c "`"$maestroPath`" mcp --help" 2>&1 | Out-String
    if ($usage -notmatch 'Usage:\s+maestro mcp') {
        Stop-NotReady 6 'mcp-unavailable' "Maestro at $maestroPath has no 'mcp' subcommand - upgrade the Maestro CLI"
    }
    $script:result.mcpResolvable = $true
    Write-Line "OK Maestro MCP: $maestroPath" 'Green'
}

# ---------- verdict ----------

if ($WithRegistry) { Add-RegistryMark -Serial $selected.id }
$script:result.ready = $true
$script:result.state = 'ready'
if ($Json) {
    $script:result | ConvertTo-Json -Compress
} else {
    Write-Host "READY - device=$($selected.id) ff=$($script:result.formFactor)$(if($Package){" pkg=$Package"})$(if($ExpectedVersion){" v=$ExpectedVersion"})" -ForegroundColor Cyan
}

# S2372: the device state is the measurement sessions repeat most (up to 90 s a probe), so READY is
# recorded as a finding that dies the moment the serial leaves `adb devices`. Child process, best
# effort: this probe has no reason to load the lock library, and a chat failure changes nothing.
try {
    $chatCli = Join-Path $PSScriptRoot '..\utils\agent-chat.ps1'
    if (Test-Path -LiteralPath $chatCli) {
        $chatExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
        $chatNote = "READY$(if($Package){" pkg=$Package"})$(if($ExpectedVersion){" v=$ExpectedVersion"})"
        $reqStr = Get-CanonicalReadyRequest -DeviceId $DeviceId -Package $Package -ExpectedVersion $ExpectedVersion -CheckMcp $CheckMcp
        & $chatExe -NoProfile -File $chatCli -Verb Post -Finding -Kind device -Topic "device:$($selected.id)" -Device $selected.id -TtlMinutes 60 -Note $chatNote -EvidenceCommand $reqStr -EvidenceExit 0 *> $null
    }
} catch { }
exit 0
