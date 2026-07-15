#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: public mutable reactive state in src/main *.kt must never grow (S1031).

.DESCRIPTION
    Part of the neuroslop hygiene family (CLAUDE.md Rule 19). The canonical Android/Kotlin idiom
    is a PRIVATE mutable backing (`private val _x = MutableStateFlow(...)`) exposed through a
    read-only view (`val x: StateFlow<..> = _x.asStateFlow()`), so a single owner controls
    emission. Exposing a `MutableStateFlow` / `MutableLiveData` / `MutableSharedFlow` publicly
    (a `val`/`var` declaration without `private`) lets any collaborator emit into shared state.

    Detection is deterministic: a property declaration line (`val`/`var`) that names a
    `Mutable(StateFlow|LiveData|SharedFlow)` type or constructor and carries no `private` modifier.
    Private backings, `.asStateFlow()`/`.asSharedFlow()` read-only views, and `import` lines never
    match. Baseline starts at 0 (S1031 fixed the sole occurrence, WearSyncEvents, first).

    Scope is `app_v2/src/main/**/*.kt` only, mirroring assert-em-dash.

    Baseline lives in scripts/quality/public-mutable-flow-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).
      -ChangedFiles    Judge only the growth these files introduce (working vs HEAD).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-public-mutable-flow.ps1
    pwsh -NoProfile -File scripts/quality/assert-public-mutable-flow.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-public-mutable-flow.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-public-mutable-flow.ps1 -List
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    [Parameter(ParameterSetName = 'Gate')][string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mainRoot = Join-Path $repoRoot 'app_v2/src/main'
$baselineFile = Join-Path $PSScriptRoot 'public-mutable-flow-baseline.txt'

$mutRx = [regex]'Mutable(StateFlow|LiveData|SharedFlow)\b'

# Count public-mutable-reactive-state declarations in a block of Kotlin source text.
# A hit is a val/var declaration line that names a Mutable* type and lacks a `private` modifier.
function Measure-PublicMutableFlow {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    $count = 0
    foreach ($line in ($Text -split "`n")) {
        if ($line -match '\b(val|var)\b' -and $mutRx.IsMatch($line) -and $line -notmatch '\bprivate\b') {
            $count++
        }
    }
    return $count
}

# Delta mode - judge only the growth the changed files introduce (working vs HEAD).
if ($ChangedFiles) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    $scoped = @($ChangedFiles | Where-Object { ($_ -replace '\\', '/') -match 'app_v2/src/main/' })
    $countFn = { param($t) Measure-PublicMutableFlow -Text $t }
    $d = Measure-ChangedFileGrowth -ChangedFiles $scoped -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn
    Write-Host ("public-mutable-flow [delta over changed files]: new occurrences {0}" -f $d.Growth)
    if ($Gate -and $d.Growth -gt 0) {
        foreach ($p in $d.PerFile) { if ($p.New -gt 0) { Write-Host ("  +{0} in {1}" -f $p.New, $p.Path) } }
        Write-Host "FAIL: new public mutable reactive state. Use a private backing + asStateFlow()/asSharedFlow()."
        exit 1
    }
    exit 0
}

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
$files = Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    if ([string]::IsNullOrEmpty($text)) { continue }
    $lineNo = 0
    foreach ($line in ($text -split "`n")) {
        $lineNo++
        if ($line -match '\b(val|var)\b' -and $mutRx.IsMatch($line) -and $line -notmatch '\bprivate\b') {
            $current++
            if ($List) {
                $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $hits.Add(("{0}:{1}  {2}" -f $rel, $lineNo, $line.Trim()))
            }
        }
    }
}

if ($List) {
    foreach ($h in $hits) { Write-Host $h }
    Write-Host ''
}

if ($PSCmdlet.ParameterSetName -eq 'Update') {
    if (-not (Test-Path $baselineFile)) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "public-mutable-flow baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "public-mutable-flow baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "public-mutable-flow baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Use a private backing + asStateFlow()/asSharedFlow()."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "public-mutable-flow: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("public-mutable-flow in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: public mutable reactive state grew above baseline. Use a private backing + asStateFlow()/asSharedFlow()."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
