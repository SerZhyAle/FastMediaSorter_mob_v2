#requires -Version 7.0
<#
.SYNOPSIS
    Rejects a call site that invokes scripts/devtest/device-ready.ps1 without naming a -Module.

.DESCRIPTION
    S2611. The probe selects an attached device by form factor when it is told which module the
    caller means, and answers "whatever is attached" when it is not. S2600 built the selecting half
    and deliberately left the parameter with no default, so that adding it changed no live caller's
    answer mid-run - which meant the protection existed and nothing switched it on. Twelve call
    sites carried none for a day, and the incident that opened S2600 was one of them: a watch
    emulator answered a phone-only ticket and the verdict went into a spec.

    So the parameter is not defaulted, it is REQUIRED at the call site, and this is what requires it.
    A caller that forgets gets a refusal naming the file, rather than a silently phone-shaped answer
    that reads afterwards as a deliberate choice.

    What counts as a call site: a file the machine or the model EXECUTES - a .ps1, the project
    profile, a command driver or reference companion under .claude/, a skill script. A file that
    merely describes the probe is not judged, on assert-qualified-gradle-tasks.ps1's principle that
    prose is not a call: docs/ and dev/ name the probe to explain it, and failing them would push
    authors to stop naming it in explanations. PLAN/ and the agent-memory tree are excluded for the
    same reason. Inside the executed roots the distinction is not visible to a scanner, so a file
    that names the probe without calling it takes a row in
    scripts/quality/device-ready-module-baseline.txt carrying the REASON - a bare path list would
    let a real call site hide as one more line.

    Judged per FILE, not per invocation. A file that mentions the probe must carry a -Module token
    somewhere. This is deliberately the weaker of the two shapes available: an invocation can span
    lines with the module set several statements from the call - check-standard-fast.ps1 resolves it
    into a hashtable that is splatted later - and a per-line rule would either miss that or refuse
    it. The bound is real and stated here: a file with two call sites where only one names a module
    passes. That case does not exist today, and a gate that refuses correct code is the one people
    route around.

.PARAMETER Gate
    Exit 1 on findings. Without it the findings are reported and the exit stays 0, matching every
    sibling in assert-fast-gates.ps1, which supplies -Gate itself.

.PARAMETER ChangedFiles
    Judge only these repo-relative paths. Used by post-change.ps1 -ScopeToFile so another session's
    in-flight work cannot fail this ticket's closure.

.PARAMETER Quiet
    Print only the verdict line, not the per-file scan detail.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-device-ready-module.ps1 -Gate

.NOTES
    Exit codes:
      0  PASS, or findings reported without -Gate.
      1  FAIL - at least one call site names no module, and -Gate was supplied.
      2  Cannot verify - the repository root does not carry the probe this gate is about.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [string[]]$ChangedFiles,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$probeRelative = 'scripts/devtest/device-ready.ps1'
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $probeRelative))) {
    Write-Error "device-ready-module: cannot verify - $probeRelative does not exist in this checkout." -ErrorAction Continue
    exit 2
}

# Roots the machine or the model executes. Everything else describes.
$scanRoots = @('scripts', '.claude')
$scanRootFiles = @('.sza-profile.json')
$judgedExtensions = @('.ps1', '.psm1', '.json', '.md')

# The agent-memory tree is excluded as a ROOT, not baselined: it is recall, never executed, and
# every note quoting a probe invocation would otherwise need a row of its own forever.
$excludedRoots = @('.claude/agent-memory/')

# The subject, this gate and the resolver are structural - they name the probe by definition.
# Everything else that mentions it without calling it earns a row in the baseline, with its reason.
$structural = @(
    'scripts/devtest/device-ready.ps1',
    'scripts/devtest/device-ready.tests/Run-Tests.ps1',
    'scripts/devtest/resolve-ticket-module.ps1',
    'scripts/devtest/resolve-ticket-module.tests/Run-Tests.ps1',
    'scripts/quality/assert-device-ready-module.ps1'
)
$baselinePath = Join-Path $PSScriptRoot 'device-ready-module-baseline.txt'
$baselined = @()
if (Test-Path -LiteralPath $baselinePath) {
    $baselined = @(Get-Content -LiteralPath $baselinePath |
            ForEach-Object { ($_ -split '#', 2)[0].Trim() } |
            Where-Object { $_ })
}
$exempt = @($structural + $baselined)

function Test-ModuleNamed {
    # The three spellings a module reaches the probe in: a bare switch, a quoted array element, and
    # the splatted-hashtable key check-standard-fast.ps1 builds.
    param([string]$Text)
    return ($Text -match '-Module\b') -or ($Text -match "\[\s*'Module'\s*\]")
}

function ConvertTo-RepoRelative {
    param([string]$FullPath)
    return ((($FullPath -replace '\\', '/') -replace [regex]::Escape((($repoRoot -replace '\\', '/') + '/')), ''))
}

$candidates = @()
if ($ChangedFiles) {
    # `pwsh -File` binds a comma list as ONE element, so re-split it - the same trap
    # Resolve-GradleModulesForPaths documents. A narrower set here means a missed finding.
    $wanted = @($ChangedFiles | ForEach-Object { ([string]$_) -split ',' } |
        ForEach-Object { $_.Trim() -replace '\\', '/' } | Where-Object { $_ })
    # S2693 re-audit: the same population as the tree walk. A changed docs/ page mentions the probe
    # in prose and is never executed, yet a set naming it was judged and failed while the bare run
    # never looked at it - two answers for one file. Only what the machine or the model executes
    # is inspected in either mode.
    $wanted = @($wanted | Where-Object {
            $rel = $_
            ($scanRootFiles -contains $rel) -or ($scanRoots | Where-Object { $rel.StartsWith($_ + '/') })
        })
    foreach ($rel in $wanted) {
        $full = Join-Path $repoRoot $rel
        if (Test-Path -LiteralPath $full -PathType Leaf) { $candidates += (Get-Item -LiteralPath $full) }
    }
}
else {
    foreach ($root in $scanRoots) {
        $full = Join-Path $repoRoot $root
        if (-not (Test-Path -LiteralPath $full)) { continue }
        $candidates += @(Get-ChildItem -LiteralPath $full -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -notmatch '[\\/](node_modules|build|\.gradle|\.kotlin)[\\/]' })
    }
    foreach ($rel in $scanRootFiles) {
        $full = Join-Path $repoRoot $rel
        if (Test-Path -LiteralPath $full -PathType Leaf) { $candidates += (Get-Item -LiteralPath $full) }
    }
}

$findings = @()
$inspected = 0
foreach ($file in $candidates) {
    if ($file.Extension -notin $judgedExtensions) { continue }
    $rel = ConvertTo-RepoRelative $file.FullName
    if ($exempt -contains $rel) { continue }
    if ($excludedRoots | Where-Object { $rel.StartsWith($_) }) { continue }

    $lines = @(Get-Content -LiteralPath $file.FullName -ErrorAction SilentlyContinue)
    if ($lines.Count -eq 0) { continue }
    # `deviceReadyArgs` is the profile's spelling: the harness splats that array into the probe, so
    # the array IS the call site even though the file never writes the script's name beside it.
    $mentions = @(0..($lines.Count - 1) | Where-Object {
            $lines[$_].Contains('device-ready.ps1') -or $lines[$_].Contains('deviceReadyArgs')
        })
    if ($mentions.Count -eq 0) { continue }

    $inspected++
    $named = $false
    foreach ($i in $mentions) {
        # Two shapes, and the difference decides how far to look. A line that carries `-File` (or the
        # profile's argument array) carries the WHOLE invocation, so the module must be on it - and
        # judging the file instead would let an unrelated `-Module` pass it, which is not
        # hypothetical: every command driver also names -Module for post-change.ps1 and
        # catalog_sync.ps1, so a whole-file test passes a driver that names none for the probe.
        # A line that only assigns the path is the other shape - standard-release-smoke.ps1 and
        # check-standard-fast.ps1 both build their arguments a few statements later - so there the
        # window is the surrounding statements.
        if ($lines[$i].Contains('-File') -or $lines[$i].Contains('deviceReadyArgs')) {
            if (Test-ModuleNamed $lines[$i]) { $named = $true; break }
            continue
        }
        $from = [Math]::Max(0, $i - 12)
        $to = [Math]::Min($lines.Count - 1, $i + 12)
        if (Test-ModuleNamed ($lines[$from..$to] -join "`n")) { $named = $true; break }
    }
    if ($named) { continue }
    $findings += $rel
}

if (-not $Quiet) {
    Write-Host "device-ready-module: $inspected call site(s) inspected."
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'device-ready-module: OK - every device-ready.ps1 call site names a -Module.' -ForegroundColor Green
    }
    exit 0
}

foreach ($rel in $findings) {
    Write-Host "device-ready-module: FAIL - $rel invokes $probeRelative without naming a -Module." -ForegroundColor Red
}
Write-Host 'Fix: add -Module app_v2 (phone) or -Module wear (watch) to the invocation. A constant caller writes the literal; a caller entering from a ticket resolves it with scripts/devtest/resolve-ticket-module.ps1 -Id <Sxxxx>. Without it the probe takes whatever device is attached, which is the S2600 incident.'

if ($Gate) { exit 1 }
exit 0
