#requires -Version 7.0
# Subject: scripts/spec_catalog/update.ps1, scripts/spec_catalog/close.ps1
#
# The behaviour under test lives in the canon harness, which is outside this repository and so
# cannot be named here - these are the forwarders every call site in this repo actually invokes,
# and re-pointing or regenerating one is exactly when this suite wants to run.
<#
.SYNOPSIS
    Contract suite for the spec-file **Status:** header mirror (S2512).

.DESCRIPTION
    Three properties of the catalog mutators, all measured against a throwaway project root:

      1. A journal/header divergence is repairable through the CLI. Re-running a transition at the
         SAME status rewrites a drifted header instead of returning "nothing to do". Before S2512
         the sync was keyed to the journal having MOVED, so the documented repair call printed
         `Sxxxx Tactical -> Tactical`, exited 0 and left the header reading Approved - which left
         hand-editing the spec file as the only cure, and the rules forbid that.
      2. A transition whose tactical folder already exists still completes both writes. This was
         the original hypothesis of S2512 and it was REFUTED - the mutator creates no folder on any
         path. The case is pinned anyway so the refutation cannot silently stop being true.
      3. A failure to re-render the release queue does not abort the transition. The release files
         are a projection of the journal and rebuildable with `release-queue.ps1 -Reconcile`,
         whereas the journal write has already landed; letting the throw escape left the journal
         ahead of the header with no confirmation line, which is the shape recorded on 2026-09-04.

    The mutators ship with the canon plugin, so this suite resolves the harness exactly as the
    forwarders do and SKIPS with a named reason when the resolved copy predates the fix - a project
    session cannot deploy the canon, and a red suite over an undeployed edit would fail every
    sibling session for something none of them can act on.

    The harness file is invoked DIRECTLY, never through scripts/spec_catalog/update.ps1: the
    forwarder overwrites SZA_PROJECT_ROOT with the repository root, so a sandboxed run through it
    would mutate the real PLAN/ journal and take a real lock (S2520).

Exit codes: 0 = every contract held (or the suite skipped with a stated reason),
            1 = a contract failed,
            2 = could not verify (harness unresolvable, fixture could not be built).
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path

$failures = New-Object System.Collections.Generic.List[string]
$passes = 0

# ── Resolve the harness the way the forwarders do ────────────────────────────────────────────
function Resolve-HarnessRoot {
    $candidates = @()
    if ($env:SZA_HARNESS_ROOT) { $candidates += $env:SZA_HARNESS_ROOT }
    $cache = Join-Path $env:USERPROFILE '.claude\plugins\cache\sza-unified-rules\sza'
    if (Test-Path -LiteralPath $cache) {
        # Ordered as versions, not as strings - the plugin version is date-derived, so a string
        # sort would put 2026.1001.1 below 2026.903.1 and pick a stale harness after an update.
        $versions = @(Get-ChildItem -LiteralPath $cache -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                $parsed = $null
                [void][version]::TryParse($_.Name, [ref]$parsed)
                [pscustomobject]@{ Path = $_.FullName; Version = $parsed }
            } | Sort-Object @{ Expression = { $null -ne $_.Version }; Descending = $true },
                            @{ Expression = { $_.Version }; Descending = $true },
                            @{ Expression = { $_.Path }; Descending = $true })
        $candidates += @($versions | ForEach-Object { Join-Path $_.Path 'tools\harness' })
    }
    $checkout = $env:SZA_CANON_ROOT
    if (-not $checkout) {
        $resolver = Join-Path $repoRoot 'scripts/utils/project-paths.ps1'
        if (Test-Path -LiteralPath $resolver) {
            $checkout = & { param($p) try { . $p; Get-CanonRoot } catch { $null } } $resolver
        }
    }
    if ($checkout) { $candidates += (Join-Path $checkout 'tools\harness') }

    foreach ($dir in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $dir 'spec_catalog\update.ps1')) { return $dir }
    }
    return $null
}

$harness = Resolve-HarnessRoot
if (-not $harness) {
    Write-Host 'spec-header-sync: could not resolve the SZA harness - nothing to verify.' -ForegroundColor Red
    exit 2
}
$updateScript = Join-Path $harness 'spec_catalog\update.ps1'
Write-Host "harness: $harness" -ForegroundColor DarkGray

# The fix must be present in BOTH files it spans, so a half-deployed cache cannot read as ready.
$updateBody = Get-Content -LiteralPath $updateScript -Raw
$libBody = Get-Content -LiteralPath (Join-Path $harness 'spec_catalog\_lib.ps1') -Raw
$fixPresent = ($updateBody -match 'S2512') -and ($libBody -match '\[ref\]\s*\$Wrote') -and ($libBody -match 'S2512')

if (-not $fixPresent) {
    Write-Host ''
    Write-Host 'SKIP - the resolved harness predates S2512.' -ForegroundColor Yellow
    Write-Host '  The fix lives in the canon checkout (tools/harness/spec_catalog/) and only a canon' -ForegroundColor Yellow
    Write-Host '  session can bump CANON_VERSION and run deploy.ps1. Point SZA_HARNESS_ROOT at the' -ForegroundColor Yellow
    Write-Host '  checkout to exercise it before the deploy lands.' -ForegroundColor Yellow
    Write-Host ''
    Write-Host 'spec-header-sync: 0 passed, 4 contracts skipped (harness predates S2512)' -ForegroundColor Yellow
    exit 0
}

# ── Fixture ──────────────────────────────────────────────────────────────────────────────────
function New-Sandbox {
    param(
        [Parameter(Mandatory)][string] $Id,
        [Parameter(Mandatory)][string] $JournalStatus,
        [Parameter(Mandatory)][string] $HeaderStatus,
        [switch] $WithTacticalFolder
    )
    $root = Join-Path ([IO.Path]::GetTempPath()) ('s2512-' + [guid]::NewGuid().ToString('N').Substring(0, 10))
    New-Item -ItemType Directory -Path (Join-Path $root 'PLAN') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'temp') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Destination (Join-Path $root '.sza-profile.json')

    $slug = "${Id}_contract-fixture"
    $specPath = Join-Path $root "PLAN/$slug.md"
    Set-Content -LiteralPath $specPath -Encoding utf8NoBOM -Value @(
        "# Strategic spec: $Id - fixture", '',
        "**Ticket:** $Id",
        "**Status:** $HeaderStatus",
        '**Priority:** 50', '', '## 1. Problem', '', 'Fixture.'
    )
    if ($WithTacticalFolder) {
        $dir = Join-Path $root "PLAN/$slug"
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $dir 'INDEX.md') -Encoding utf8NoBOM -Value '# Tactical plan'
    }
    $record = [ordered]@{
        id = $Id; name = 'contract fixture'; status = $JournalStatus; priority = 50
        file = "PLAN/$slug.md"; created = '2026-09-05 10:00'; updated = '2026-09-05 10:00'; tier = 2
    }
    Set-Content -LiteralPath (Join-Path $root 'PLAN/spec-catalog.jsonl') -Encoding utf8NoBOM `
        -Value ($record | ConvertTo-Json -Compress -Depth 5)
    Set-Content -LiteralPath (Join-Path $root 'PLAN/spec-catalog-archive.jsonl') -Encoding utf8NoBOM -Value ''
    Set-Content -LiteralPath (Join-Path $root 'PLAN/RELEASE_QUEUE.md') -Encoding utf8NoBOM -Value @(
        '# Release Queue', '', 'current-release: 37', '', '```',
        'rel  ticket                                                         changed     status',
        "37   $slug                                    2026-09-05  $JournalStatus", '```'
    )
    Set-Content -LiteralPath (Join-Path $root 'PLAN/RELEASE_READY.md') -Encoding utf8NoBOM -Value @(
        '# Release Ready', '', '```',
        'rel  ticket                                                         changed     status', '```'
    )
    return [pscustomobject]@{ Root = $root; SpecPath = $specPath; Slug = $slug }
}

function Invoke-Mutator {
    param([Parameter(Mandatory)][string] $Root, [Parameter(Mandatory)][string] $Id,
          [Parameter(Mandatory)][string] $Status)
    # A child process per scenario: Get-SzaProjectRoot caches the root for the life of the
    # process, so two sandboxes cannot share one.
    $env:SZA_PROJECT_ROOT = $Root
    $out = & pwsh -NoProfile -File $updateScript -Id $Id -Status $Status 2>&1 | Out-String
    $code = $LASTEXITCODE
    $env:SZA_PROJECT_ROOT = $null
    return [pscustomobject]@{ Output = $out; ExitCode = $code }
}

function Get-HeaderStatus {
    param([Parameter(Mandatory)][string] $SpecPath)
    $m = [regex]::Match((Get-Content -LiteralPath $SpecPath -Raw), '(?m)^\*\*Status:\*\*[ \t]*([^\r\n]*)')
    if ($m.Success) { return $m.Groups[1].Value.Trim() }
    return '<no header>'
}

function Add-Result {
    param([Parameter(Mandatory)][string] $Name, [Parameter(Mandatory)][bool] $Ok,
          [string] $Detail)
    if ($Ok) {
        $script:passes++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $failures.Add("$Name - $Detail")
        Write-Host "  FAIL  $Name - $Detail" -ForegroundColor Red
    }
}

Write-Host ''

# ── 1. A diverged header is repairable through the CLI ───────────────────────────────────────
$sb = New-Sandbox -Id 'S9101' -JournalStatus 'Tactical' -HeaderStatus 'Approved'
try {
    $r = Invoke-Mutator -Root $sb.Root -Id 'S9101' -Status 'Tactical'
    $after = Get-HeaderStatus -SpecPath $sb.SpecPath
    Add-Result -Name 'same-status call repairs a diverged header' `
        -Ok ($after -eq 'Tactical' -and $r.ExitCode -eq 0) `
        -Detail "header='$after' exit=$($r.ExitCode)"
    Add-Result -Name 'the repair announces itself' `
        -Ok ($r.Output -match 'header synced') -Detail 'no "header synced" line printed'
} finally { Remove-Item -Recurse -Force -LiteralPath $sb.Root -ErrorAction SilentlyContinue }

# ── 2. An already-correct header is not re-announced ─────────────────────────────────────────
$sb = New-Sandbox -Id 'S9102' -JournalStatus 'Tactical' -HeaderStatus 'Tactical'
try {
    $r = Invoke-Mutator -Root $sb.Root -Id 'S9102' -Status 'Tactical'
    # The unconditional sync must stay silent when it changed nothing, or every no-op call would
    # claim a repair it did not perform.
    Add-Result -Name 'a no-op sync claims no repair' `
        -Ok ($r.Output -notmatch 'header synced') -Detail 'printed "header synced" without writing'
} finally { Remove-Item -Recurse -Force -LiteralPath $sb.Root -ErrorAction SilentlyContinue }

# ── 3. An existing tactical folder does not break the transition (S2512's refuted hypothesis) ─
$sb = New-Sandbox -Id 'S9103' -JournalStatus 'Approved' -HeaderStatus 'Approved' -WithTacticalFolder
try {
    $r = Invoke-Mutator -Root $sb.Root -Id 'S9103' -Status 'Tactical'
    $after = Get-HeaderStatus -SpecPath $sb.SpecPath
    $journal = (Get-Content -LiteralPath (Join-Path $sb.Root 'PLAN/spec-catalog.jsonl') -Raw | ConvertFrom-Json).status
    Add-Result -Name 'transition completes with the tactical folder already present' `
        -Ok ($after -eq 'Tactical' -and $journal -eq 'Tactical' -and $r.ExitCode -eq 0) `
        -Detail "header='$after' journal='$journal' exit=$($r.ExitCode)"
} finally { Remove-Item -Recurse -Force -LiteralPath $sb.Root -ErrorAction SilentlyContinue }

# ── 4. A queue-render failure does not split the journal from the header ─────────────────────
$sb = New-Sandbox -Id 'S9104' -JournalStatus 'Approved' -HeaderStatus 'Approved'
try {
    # Write-ReleaseFile stages through "<path>.tmp"; a DIRECTORY at that name makes the staging
    # write throw without touching the journal, which lands first.
    New-Item -ItemType Directory -Path (Join-Path $sb.Root 'PLAN/RELEASE_QUEUE.md.tmp') -Force | Out-Null
    $r = Invoke-Mutator -Root $sb.Root -Id 'S9104' -Status 'Tactical'
    $after = Get-HeaderStatus -SpecPath $sb.SpecPath
    $journal = (Get-Content -LiteralPath (Join-Path $sb.Root 'PLAN/spec-catalog.jsonl') -Raw | ConvertFrom-Json).status
    Add-Result -Name 'a queue-render failure leaves journal and header agreeing' `
        -Ok ($after -eq 'Tactical' -and $journal -eq 'Tactical') `
        -Detail "header='$after' journal='$journal'"
    Add-Result -Name 'a queue-render failure is not reported as a failed transition' `
        -Ok ($r.ExitCode -eq 0) -Detail "exit=$($r.ExitCode)"
    Add-Result -Name 'the warning names the repair command' `
        -Ok ($r.Output -match '-Reconcile') -Detail 'warning does not name release-queue.ps1 -Reconcile'
} finally { Remove-Item -Recurse -Force -LiteralPath $sb.Root -ErrorAction SilentlyContinue }

# ── Verdict ──────────────────────────────────────────────────────────────────────────────────
Write-Host ''
if ($failures.Count -gt 0) {
    Write-Host ("spec-header-sync: {0} passed, {1} FAILED, 0 skipped" -f $passes, $failures.Count) -ForegroundColor Red
    foreach ($f in $failures) { Write-Host "  - $f" -ForegroundColor Red }
    exit 1
}
Write-Host ("spec-header-sync: {0} passed, 0 failed, 0 skipped" -f $passes) -ForegroundColor Green
exit 0
