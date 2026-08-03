<#
guard-catalog-before-kt-search.ps1 - Claude Code PreToolUse hook (matcher: Grep, Glob).

Refuses an UNNARROWED Kotlin search issued before the class catalogue was consulted
in this session. Three conditions must all hold for a refusal (S1344 strategic
sections 6.3-6.5):
  1. the tool is Grep or Glob;
  2. the call targets Kotlin - its glob/pattern names .kt, its Grep -type is kotlin,
     or its path sits inside a Kotlin source set;
  3. the call is unnarrowed - it carries no path and no glob restricting it to a
     concrete directory or package.
A call that already names a subtree passes untouched, and a call that does not target
.kt is out of scope by construction - so there is no exclusion list to keep complete.

The pass condition is temp/catalog-touch.marker, written by dev/CATALOG/scripts/query.ps1
on every successful query. A PreToolUse hook keeps no state between invocations, so the
marker file is the only place "has this session consulted the catalogue" can live - the
same mechanism temp/BUILD.LOCK and temp/CODE.LOCK already use under CLAUDE.md Rule 23.

Rationale: "catalogue before grep" is prose, and prose scores 8.3% here while gated rules
score ~99% (dev/AGENT_PROCESS_AUDIT_2026-07-31.md). Strategic section 7 rates "the hook
blocks a legitimate search" as high-probability with a specific consequence - the owner
switches it off - so every refusal carries the exact command to run instead. A gate that
only says no trains the reader to route around it.

Contract:
  exit 2 = block the tool call (stderr is shown to Claude)
  exit 0 = allow
Fail-open on any parse or IO error: a malformed payload must never make Grep unusable.

Exit codes (CLAUDE.md Rule 7):
  0  allow - not an unnarrowed Kotlin search, catalogue already consulted, or the
     payload could not be judged.
  2  block - unnarrowed Kotlin search with no catalogue lookup in this session.

Registered per-project in .claude/settings.json rather than globally: it names
dev/CATALOG/ and is meaningless outside this repository.
#>

function Allow { exit 0 }
function Deny([string]$msg) { [Console]::Error.WriteLine($msg); exit 2 }

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$MarkerFile = Join-Path $RepoRoot 'temp\catalog-touch.marker'
$SectorFile = Join-Path $RepoRoot 'dev\CATALOG\sectors.json'
# The case runner points this at a scratch file so a proof run cannot be counted as pilot evidence.
$PilotLog = if ($env:FMS_GUARD_PILOT_LOG) { $env:FMS_GUARD_PILOT_LOG } else { Join-Path $RepoRoot 'dev\sector-gate-pilot.jsonl' }

# A glob segment with no wildcard is a literal directory or file name, which is exactly
# what "narrowed to a concrete subtree" means. '**/*.kt' has no such segment;
# 'app_v2/**/*.kt' and '**/ui/player/*.kt' both do.
function Test-NarrowingGlob([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return $false }
    foreach ($segment in ($value -split '[\\/]')) {
        if ([string]::IsNullOrWhiteSpace($segment)) { continue }
        if ($segment -eq '.' -or $segment -eq '..') { continue }
        if ($segment -notmatch '[\*\?\[\]\{\}]') { return $true }
    }
    return $false
}

# The repository root is not a narrowing path - it is the whole haystack spelled out.
function Test-NarrowingPath([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return $false }
    $trimmed = $value.Trim().TrimEnd('\', '/')
    if ($trimmed -eq '' -or $trimmed -eq '.') { return $false }
    try {
        $full = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $trimmed))
        if ($full.TrimEnd('\', '/') -eq $RepoRoot.TrimEnd('\', '/')) { return $false }
    }
    catch { return $true }
    return $true
}

function Test-KotlinGlob([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return $false }
    # '*.kt' / '**/*.kt' first, then the brace-list spelling '*.{kt,java}'.
    if ($value -match '(?i)\.kts?(\W|$)') { return $true }
    if ($value -match '(?i)[{,]\s*kts?\s*[},]') { return $true }
    return $false
}

function Test-KotlinPath([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return $false }
    if ($value -match '(?i)[\\/]src[\\/][^\\/]+[\\/](java|kotlin)([\\/]|$)') { return $true }
    if ($value -match '(?i)(^|[\\/])(app_v2|wear)[\\/]src([\\/]|$)') { return $true }
    return $false
}

# Only refusals are recorded: an allowed call is uninteresting by definition and logging every one
# of them would grow the file without bound. A logging failure must never turn an allow into a
# refusal, or the measurement would be changing the thing it measures - so the caller ignores it.
function Write-PilotRow([hashtable]$fields) {
    try {
        $dir = Split-Path -Parent $PilotLog
        if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        $row = [PSCustomObject]@{
            kind           = 'refusal'
            at             = (Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK')
            session        = $fields.session
            tool           = $fields.tool
            pattern        = $fields.pattern
            glob           = $fields.glob
            path           = $fields.path
            type           = $fields.type
            markerPresent  = $false
            classification = ''
        }
        Add-Content -LiteralPath $PilotLog -Value ($row | ConvertTo-Json -Depth 5 -Compress) -Encoding UTF8
    }
    catch {
        # Deliberately silent: stderr from this hook is the refusal message the agent reads.
    }
}

function Get-SectorNames {
    try {
        if (-not (Test-Path -LiteralPath $SectorFile)) { return @() }
        return @((Get-Content -LiteralPath $SectorFile -Raw -Encoding UTF8 | ConvertFrom-Json).sectors.name)
    }
    catch { return @() }
}

# The refusal has to answer "so what should I have run" with a command, not a policy.
# The search pattern is the only evidence of intent available here, so the longest
# identifier inside it becomes either a sector name or a -ClassMatches wildcard.
function Get-SuggestedCommand([string]$pattern, [string[]]$sectors) {
    $token = ''
    if (-not [string]::IsNullOrWhiteSpace($pattern)) {
        $words = [regex]::Matches($pattern, '[A-Za-z_][A-Za-z0-9_]{2,}')
        foreach ($match in $words) {
            if ($match.Value.Length -gt $token.Length) { $token = $match.Value }
        }
    }
    if ($token -ne '') {
        foreach ($sector in $sectors) {
            if ($sector -and $token.ToLower().Contains([string]$sector)) {
                return "pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Sector $sector"
            }
        }
        return "pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches ""*$token*"""
    }
    return 'pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Sectors'
}

try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { Allow }
    $payload = $raw | ConvertFrom-Json
    $toolName = [string]$payload.tool_name
    $toolInput = $payload.tool_input
    # Recorded so step 03.3 can count DISTINCT tasks rather than raw refusals - ten refusals from
    # one stubborn session are not the ten tasks strategic section 11.3 asks for.
    $sessionId = [string]$payload.session_id
}
catch {
    Allow
}

if ($toolName -ne 'Grep' -and $toolName -ne 'Glob') { Allow }
if ($null -eq $toolInput) { Allow }

$props = @($toolInput.PSObject.Properties.Name)
$pattern = if ($props -contains 'pattern') { [string]$toolInput.pattern } else { '' }
# Malformed input is not this hook's business - the tool itself will reject it.
if ([string]::IsNullOrWhiteSpace($pattern)) { Allow }

$pathArg = if ($props -contains 'path') { [string]$toolInput.path } else { '' }
$globArg = if ($props -contains 'glob') { [string]$toolInput.glob } else { '' }
$typeArg = if ($props -contains 'type') { [string]$toolInput.type } else { '' }

# For Glob the pattern IS the file filter; for Grep the pattern is a regex over content
# and the file filter lives in glob/type.
$fileFilter = if ($toolName -eq 'Glob') { $pattern } else { $globArg }

$kotlinTarget = (Test-KotlinGlob $fileFilter) -or (Test-KotlinPath $pathArg)
if ($typeArg -match '(?i)^(kotlin|kt)$') { $kotlinTarget = $true }
if (-not $kotlinTarget) { Allow }

if (Test-NarrowingPath $pathArg) { Allow }
if (Test-NarrowingGlob $fileFilter) { Allow }
if ($toolName -eq 'Grep' -and (Test-NarrowingGlob $globArg)) { Allow }

# Checked last so the cheap structural tests above decide most calls without touching disk.
try {
    if (Test-Path -LiteralPath $MarkerFile) { Allow }
}
catch {
    Allow
}

Write-PilotRow @{
    session = $sessionId
    tool    = $toolName
    pattern = $pattern
    glob    = $globArg
    path    = $pathArg
    type    = $typeArg
}

$sectors = Get-SectorNames
$suggested = Get-SuggestedCommand -pattern $pattern -sectors $sectors
$sectorLine = if ($sectors.Count -gt 0) {
    "Known sectors: $($sectors -join ', ')."
}
else {
    'List the sectors with query.ps1 -Sectors.'
}

Deny(
    "Blocked by guard-catalog-before-kt-search (S1344): this is a repo-wide Kotlin search and " +
    "the class catalogue has not been consulted in this session. 2398 classes are already " +
    "indexed by name, path, layer, role and injected dependencies - a scan re-derives what one " +
    "query answers. Run this first:`n  $suggested`n$sectorLine`nThen re-issue the search, or " +
    "skip it: the catalogue record usually names the file outright. A search that already names " +
    "a subtree is never blocked - add a 'path' or a 'glob' naming a directory and this hook " +
    "steps aside. Policy: CLAUDE.md 'Catalog-first' / dev/CATALOG/scripts/query.ps1 -Help."
)
