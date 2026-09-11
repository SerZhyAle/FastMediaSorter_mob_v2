#requires -Version 7.0
<#
.SYNOPSIS
    S2930: every Activity in app_v2 resolves resources through the app locale wrapper.

.DESCRIPTION
    An Activity that neither extends BaseActivity nor overrides attachBaseContext with
    LocaleHelper.applyLocale never passes through the app's locale wrapper, so it resolves its
    strings from whatever configuration the framework handed it - not from the language the user
    chose. Measured 2026-09-11: 24 such Activities in src/main and 12 more across the flavor source
    sets, one of which (WearCompanionActivity) was observed showing Russian on an en-US device while
    the screen one tap away showed English.

    The subject is a single source file, so this is a per-ticket gate (CLAUDE.md Rule 33): it runs
    from post-change.ps1 and from `.\a.ps1 fg`, never only at the release boundary.

    Exclusions are NAMED, not counted. A count ratchet would admit a fresh violation the moment an
    old one was fixed; here every exemption carries a written reason a human has read. Baseline:
    scripts/quality/activity-locale-wrapper-baseline.txt, one `<repo-relative path> | <reason>` per
    line. A row with no reason is a misconfiguration of this gate, not a pass.

    Test source sets (test, androidTest, and anything starting with `test`) are out of scope: they
    render nothing to a user.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every in-scope Activity carries the wrapper, or a non-gate report run.
      1  -Gate and at least one Activity is unwrapped and unexcused.
      2  cannot verify - the source root is missing, or a baseline row carries no reason.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-activity-locale-wrapper.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-activity-locale-wrapper.ps1 -Gate -ChangedFiles "a.kt,b.kt"
    pwsh -NoProfile -File scripts/quality/assert-activity-locale-wrapper.ps1 -List
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$List,
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$srcRoot = Join-Path $repoRoot 'app_v2/src'
$baselinePath = Join-Path $PSScriptRoot 'activity-locale-wrapper-baseline.txt'

if (-not (Test-Path -LiteralPath $srcRoot)) {
    Write-Error "assert-activity-locale-wrapper: cannot verify - source root not found: $srcRoot" -ErrorAction Continue
    exit 2
}

# A framework Activity supertype named directly. A class extending an app-level base instead is not
# matched on purpose: that base is itself in scope and is judged on its own line.
$declarationPattern = '^\s*(?:internal |private |public |abstract |open |sealed )*class\s+\w+\s*(?:\([^)]*\))?\s*:\s*(?:AppCompatActivity|ComponentActivity|FragmentActivity|Activity)\s*\('
$wrapperPattern = 'LocaleHelper\.applyLocale'
$baseClassPattern = ':\s*BaseActivity\b'

# --- baseline -------------------------------------------------------------------------------

$excused = @{}
if (Test-Path -LiteralPath $baselinePath) {
    $rowNumber = 0
    foreach ($row in (Get-Content -LiteralPath $baselinePath)) {
        $rowNumber++
        $text = $row.Trim()
        if (-not $text -or $text.StartsWith('#')) { continue }
        $parts = $text -split '\|', 2
        $path = $parts[0].Trim() -replace '\\', '/'
        $reason = if ($parts.Count -gt 1) { $parts[1].Trim() } else { '' }
        if (-not $reason) {
            Write-Error ("assert-activity-locale-wrapper: cannot verify - baseline row $rowNumber " +
                "names '$path' with no reason. Every exemption states why, or it is not an exemption.") -ErrorAction Continue
            exit 2
        }
        $excused[$path] = $reason
    }
}

# --- scope ----------------------------------------------------------------------------------

function Test-InScope {
    param([string]$RelativePath)
    if ($RelativePath -notmatch '^app_v2/src/([^/]+)/java/') { return $false }
    $sourceSet = $Matches[1]
    return -not ($sourceSet -eq 'androidTest' -or $sourceSet -eq 'test' -or $sourceSet.StartsWith('test'))
}

if ($ChangedFiles) {
    # post-change.ps1 hands the whole set as ONE comma-joined string, which binds to [string[]] as a
    # single element. Without this split the gate filtered every path out and reported "0 changed
    # file(s), all wrapped" - a PASS that had examined nothing, which is worse than a FAIL.
    $named = @()
    foreach ($entry in @($ChangedFiles)) {
        $named += @($entry -split ',' | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })
    }
    $candidates = $named |
        Where-Object { $_.EndsWith('.kt') -and (Test-InScope $_) } |
        ForEach-Object { Join-Path $repoRoot $_ } |
        Where-Object { Test-Path -LiteralPath $_ }
} else {
    $candidates = Get-ChildItem -LiteralPath $srcRoot -Recurse -Filter '*.kt' -File |
        ForEach-Object { $_.FullName } |
        Where-Object { Test-InScope (($_.Substring($repoRoot.Length + 1)) -replace '\\', '/') }
}

# --- scan -----------------------------------------------------------------------------------

$violations = @()
$checked = 0
$activities = 0

foreach ($full in $candidates) {
    $checked++
    $text = [System.IO.File]::ReadAllText($full)
    $relative = ($full.Substring($repoRoot.Length + 1)) -replace '\\', '/'

    $declarationLine = 0
    $lineNumber = 0
    foreach ($line in ($text -split "`r?`n")) {
        $lineNumber++
        if ($line -match $declarationPattern) { $declarationLine = $lineNumber; break }
    }
    if ($declarationLine -eq 0) { continue }

    $activities++
    if ($text -match $wrapperPattern -or $text -match $baseClassPattern) { continue }
    if ($excused.ContainsKey($relative)) {
        if ($List) { Write-Host "  excused ${relative}:$declarationLine - $($excused[$relative])" }
        continue
    }
    $violations += "${relative}:$declarationLine"
}

# --- verdict --------------------------------------------------------------------------------

$scopeLabel = if ($ChangedFiles) { "$checked changed file(s)" } else { 'app_v2 (all non-test source sets)' }

if ($violations.Count -eq 0) {
    Write-Host "assert-activity-locale-wrapper: PASS - $activities Activity declaration(s) in $scopeLabel, all wrapped; $($excused.Count) excused by baseline."
    exit 0
}

$message = @(
    "assert-activity-locale-wrapper: FAIL - $($violations.Count) Activity/Activities resolve resources outside the app locale wrapper:"
    ($violations | ForEach-Object { "  $_" })
    ''
    'Fix: extend BaseActivity, or add'
    '    override fun attachBaseContext(newBase: Context) {'
    '        super.attachBaseContext(LocaleHelper.applyLocale(newBase))'
    '    }'
    'Without it the screen shows the framework configuration language, not the one the user chose.'
    "If the Activity genuinely must not wrap its context, add a row to $baselinePath as"
    '    <repo-relative path> | <why wrapping breaks this one>'
) -join "`n"
Write-Error $message -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
