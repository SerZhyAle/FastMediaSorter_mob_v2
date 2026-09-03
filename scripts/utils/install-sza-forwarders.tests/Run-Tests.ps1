#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for the forwarder template in scripts/utils/install-sza-forwarders.ps1 (S2441).

.DESCRIPTION
    Half of the generated forwarders are DOT-SOURCED as libraries, so everything the template
    assigns at its top level lands in the caller's scope. Two failures follow, and this suite holds
    both shut:

      1. Substitution. PowerShell names are case-insensitive, so the template's own `$target` was
         the caller's `-Target` parameter. post-change.ps1 dot-sources the agent-lock-domains
         forwarder before it journals, and every dev/CHANGELOG.md row written on 2026-09-03 carried
         a harness path where the ticket id belonged.
      2. Type constraint. A caller declaring `[string]$Candidates` constrains the template's own
         accumulator, so `$candidates = @()` collapses to '' and each `+=` concatenates rather than
         appends - one unusable path, and a forwarder that cannot locate the harness at all.

    Each case generates a forwarder from the LIVE template into a temp sandbox, points it at a stub
    harness, dot-sources it from a probe declaring the colliding parameters, and reads back what
    survived. Both cases fail against the pre-fix template, which is the point of writing them.

Exit codes: 0 = every case passed; 1 = a case failed; 2 = the generator or its template could not
            be read, so nothing was verified.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$generator = Join-Path $repoRoot 'scripts\utils\install-sza-forwarders.ps1'
if (-not (Test-Path -LiteralPath $generator)) {
    Write-Host "install-sza-forwarders.tests: generator not found at $generator" -ForegroundColor Red
    exit 2
}

# The template is read out of the generator rather than restated here: a local copy would keep
# passing while the shipped template stayed broken, which is the failure this suite exists to catch.
$generatorText = Get-Content -LiteralPath $generator -Raw
$templateMatch = [regex]::Match($generatorText, "(?s)\`$template\s*=\s*@'\r?\n(.*?)\r?\n'@")
if (-not $templateMatch.Success) {
    Write-Host "install-sza-forwarders.tests: no `$template here-string found in $generator" -ForegroundColor Red
    exit 2
}
$template = $templateMatch.Groups[1].Value

$failures = New-Object System.Collections.Generic.List[string]

function Assert-Equal {
    param([string]$What, [string]$Expected, [string]$Actual)
    if ($Expected -eq $Actual) {
        Write-Host "  PASS  $What" -ForegroundColor Green
    } else {
        Write-Host "  FAIL  $What - expected: $Expected | actual: $Actual" -ForegroundColor Red
        $failures.Add($What)
    }
}

function Invoke-Probe {
    <#
        Generates a forwarder from the live template beside a stub harness, runs $ProbeBody as a
        child script with the given arguments, and returns the object the probe emitted as JSON -
        or $null when the probe never got that far, having printed why.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$ProbeBody,
        [Parameter(Mandatory = $true)][string[]]$ProbeArgs,
        # S2452: exercise candidate 3 instead of candidate 1 - no SZA_HARNESS_ROOT, no reachable
        # plugin cache, and the harness locatable only through Get-CanonRoot in a stub resolver
        # planted where the template looks for the real one.
        [switch]$ViaCanonFallback
    )

    $sandbox = Join-Path $repoRoot ('temp\scratch\sza-fwd-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $harnessRoot = Join-Path $sandbox 'harness'
    New-Item -ItemType Directory -Force -Path $harnessRoot | Out-Null
    try {
        Set-Content -LiteralPath (Join-Path $harnessRoot 'probe-lib.ps1') -Encoding UTF8 -Value @'
function Get-SzaFwdTestMarker { 'harness-reached' }
'@
        if ($ViaCanonFallback) {
            # The template reads {UP}\scripts\utils\project-paths.ps1, and {UP} is '.' here. The
            # canon root is the PARENT of tools\harness, so the stub points one level above the
            # stub harness and the forwarder has to append tools\harness itself.
            $canonRoot = Join-Path $sandbox 'canon'
            New-Item -ItemType Directory -Force -Path (Join-Path $canonRoot 'tools\harness') | Out-Null
            Copy-Item -LiteralPath (Join-Path $harnessRoot 'probe-lib.ps1') `
                -Destination (Join-Path $canonRoot 'tools\harness\probe-lib.ps1') -Force
            New-Item -ItemType Directory -Force -Path (Join-Path $sandbox 'scripts\utils') | Out-Null
            Set-Content -LiteralPath (Join-Path $sandbox 'scripts\utils\project-paths.ps1') -Encoding UTF8 -Value @"
function Get-CanonRoot { '$canonRoot' }
"@
        }
        $forwarder = $template.
            Replace('{HARNESS}', 'probe-lib.ps1').
            Replace('{LEAF}', 'probe-fwd.ps1').
            Replace('{UP}', '.')
        [System.IO.File]::WriteAllText((Join-Path $sandbox 'probe-fwd.ps1'), $forwarder, [System.Text.UTF8Encoding]::new($false))
        Set-Content -LiteralPath (Join-Path $sandbox 'probe.ps1') -Encoding UTF8 -Value $ProbeBody

        $previousHarnessRoot = $env:SZA_HARNESS_ROOT
        $previousProjectRoot = $env:SZA_PROJECT_ROOT
        $previousCanonRoot = $env:SZA_CANON_ROOT
        $previousUserProfile = $env:USERPROFILE
        if ($ViaCanonFallback) {
            # Both earlier candidates have to MISS for candidate 3 to be what the test measures.
            # USERPROFILE moves to the empty sandbox, so the plugin cache path does not exist.
            $env:SZA_HARNESS_ROOT = $null
            $env:SZA_CANON_ROOT = $null
            $env:USERPROFILE = $sandbox
        } else {
            $env:SZA_HARNESS_ROOT = $harnessRoot
        }
        try {
            $raw = & (Get-Process -Id $PID).Path -NoProfile -File (Join-Path $sandbox 'probe.ps1') @ProbeArgs 2>&1
        } finally {
            # The forwarder writes SZA_PROJECT_ROOT into the environment it inherits, and this
            # process runs inside the repository it would then point at the sandbox.
            $env:SZA_HARNESS_ROOT = $previousHarnessRoot
            $env:SZA_PROJECT_ROOT = $previousProjectRoot
            $env:SZA_CANON_ROOT = $previousCanonRoot
            $env:USERPROFILE = $previousUserProfile
        }

        $json = @($raw | ForEach-Object { "$_" } | Where-Object { $_.TrimStart().StartsWith('{') }) | Select-Object -Last 1
        if (-not $json) {
            Write-Host '  the probe emitted no result. Its output was:' -ForegroundColor Yellow
            $raw | ForEach-Object { Write-Host "      $_" -ForegroundColor Gray }
            return $null
        }
        return ($json | ConvertFrom-Json)
    } finally {
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# --- Case 1: a dot-sourced forwarder must not overwrite the caller's own variables ---------------
# `-Target` is typed exactly as post-change.ps1 declares it, which is where this was measured.
Write-Host 'case 1: a dot-sourced forwarder leaves the caller''s state alone'
$case1 = Invoke-Probe -ProbeBody @'
param([string]$Target, $Candidates, $Code, $Cache, $Checkout)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'probe-fwd.ps1')
[pscustomobject]@{
    Target     = $Target
    Candidates = $Candidates
    Code       = $Code
    Cache      = $Cache
    Checkout   = $Checkout
    # Cast: $ErrorActionPreference is an ActionPreference enum, and ConvertTo-Json renders an enum
    # as its ordinal - 'Stop' would arrive here as 1 and never match the expected name.
    Eap        = [string]$ErrorActionPreference
    Marker     = (Get-SzaFwdTestMarker)
} | ConvertTo-Json -Compress
'@ -ProbeArgs @(
    '-Target', 'S2441', '-Candidates', 'caller-candidates', '-Code', 'caller-code',
    '-Cache', 'caller-cache', '-Checkout', 'caller-checkout')

if (-not $case1) {
    $failures.Add('case 1 produced no result')
} else {
    Assert-Equal 'the harness was actually reached'            'harness-reached'   $case1.Marker
    Assert-Equal '-Target survives the dot-source'             'S2441'             $case1.Target
    Assert-Equal '-Candidates survives the dot-source'         'caller-candidates' $case1.Candidates
    Assert-Equal '-Code survives the dot-source'               'caller-code'       $case1.Code
    Assert-Equal '-Cache survives the dot-source'              'caller-cache'      $case1.Cache
    Assert-Equal '-Checkout survives the dot-source'           'caller-checkout'   $case1.Checkout
    Assert-Equal "the caller's ErrorActionPreference survives" 'Stop'              $case1.Eap
}

# --- Case 2: a TYPED caller parameter must not break the forwarder's own resolution --------------
# The harder mode: a [string] constraint on a name the template accumulates into turns every += into
# a concatenation, so the forwarder finds nothing and exits 2 before the caller's script begins.
Write-Host 'case 2: a typed caller parameter does not break harness resolution'
$case2 = Invoke-Probe -ProbeBody @'
param([string]$Candidates, [string]$Target)
. (Join-Path $PSScriptRoot 'probe-fwd.ps1')
[pscustomobject]@{
    Candidates = $Candidates
    Marker     = (Get-SzaFwdTestMarker)
} | ConvertTo-Json -Compress
'@ -ProbeArgs @('-Candidates', 'caller-candidates', '-Target', 'S2441')

if (-not $case2) {
    $failures.Add('case 2 produced no result - the forwarder could not resolve the harness')
} else {
    Assert-Equal 'the harness was reached despite a typed caller parameter' 'harness-reached'   $case2.Marker
    Assert-Equal '-Candidates survives a typed declaration'                 'caller-candidates' $case2.Candidates
}

# --- Case 3: the canon-checkout fallback still resolves once the literal is gone ----------------
# S2452 removed 'P:\WEB\sza-unified-rules' from the template and made candidate 3 ask
# project-paths.ps1 for it. Nothing else exercises that branch: cases 1 and 2 both resolve at
# candidate 1 and never reach it, so without this case the fix could silently have left every
# forwarder with two working candidates and a dead third.
Write-Host 'case 3: candidate 3 resolves through Get-CanonRoot with no literal in the template'
$case3 = Invoke-Probe -ViaCanonFallback -ProbeBody @'
param([string]$Target)
. (Join-Path $PSScriptRoot 'probe-fwd.ps1')
[pscustomobject]@{
    Target = $Target
    Marker = (Get-SzaFwdTestMarker)
} | ConvertTo-Json -Compress
'@ -ProbeArgs @('-Target', 'S2452')

if (-not $case3) {
    $failures.Add('case 3 produced no result - the canon-checkout fallback resolved nothing')
} else {
    Assert-Equal 'the harness was reached through the canon fallback' 'harness-reached' $case3.Marker
    Assert-Equal 'the caller survives the fallback dot-source'        'S2452'           $case3.Target
}

if ($failures.Count -gt 0) {
    Write-Host "install-sza-forwarders.tests: FAIL ($($failures.Count) assertion(s))" -ForegroundColor Red
    exit 1
}
Write-Host 'install-sza-forwarders.tests: PASS' -ForegroundColor Green
exit 0
