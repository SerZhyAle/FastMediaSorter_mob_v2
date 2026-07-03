#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: the number of trivial verb-noun comments in src/main must never grow.

.DESCRIPTION
    Part of S0383 neuroslop hygiene. AI-generated code accrues trivial line
    comments that merely restate the next call (e.g. `// Get user` above
    `getUser()`). This detector counts `//` comments whose text begins with a
    trivial verb-noun keyword. It is a COARSE proposal generator, not an
    auto-deleter: removal is manual and per-site (a comment may legitimately
    start with such a verb). The count may only go DOWN; growth fails the gate.

    Heuristic: a line whose first non-space content is `//` followed by one of
    the trivial verbs and a word boundary. Excludes `//noinspection`, `// TODO`,
    `// FIXME`, and `//`-style URLs. KDoc (`*` / `/**`) never starts with `//`.

    Baseline lives in scripts/quality/trivial-comments-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1
    pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1 -List
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Fix')][switch]$Fix,
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    # S0850: when set, judge only the growth these files introduce (working vs HEAD)
    # instead of a full src/main scan. Preserves the "must not grow" guarantee for the change.
    [Parameter(ParameterSetName = 'Gate')][string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mainRoot = Join-Path $repoRoot 'app_v2/src/main'
$baselineFile = Join-Path $PSScriptRoot 'trivial-comments-baseline.txt'

$verbs = 'Get|Set|Initialize|Init|Create|Update|Check|Handle|Setup|Set up|Show|Hide|Load|Save|Return|Add|Remove|Clear|Start|Stop|Reset|Apply|Configure|Build|Bind|Observe|Enable|Disable|Register|Unregister|Notify|Refresh|Toggle|Cancel'
$rx = [regex]"^\s*//\s*($verbs)\b"
# A comment is TRIVIAL only if it is a short verb-noun phrase that adds nothing
# the code does not already say. Anything with an explanatory connective
# (why/when/how) carries information and is KEPT, even if it starts with a verb.
$connectiveRx = [regex]'(?i)\b(to|so|for|because|since|while|when|if|via|using|avoid|prevent|ensure|keep|otherwise|limit|note|already|only|first|before|after|null|stale|crash|leak|race|workaround|hack|fallback|instead|due|unless|until|safe|deprecated)\b'
$maxWords = 4

# A standalone `//` comment is TRIVIAL only if it is a short verb-noun phrase that
# adds nothing the code does not already say. Excluded (KEPT) when it carries any
# explanatory connective, OR a digit / colon (those name a specific value, version,
# id, magic byte, or url-like format: "Set 01 - Music", "Android 10", "signature: BM").
function Test-TrivialLine([string]$line) {
    if ($line -match '//\s*(noinspection|TODO|FIXME)') { return $false }
    if ($line -match '//\s*https?:') { return $false }
    if (-not $rx.IsMatch($line)) { return $false }
    $body = ($line -replace '^\s*//\s*', '').Trim()
    if ($connectiveRx.IsMatch($body)) { return $false }
    if ($body -match '[\d:]') { return $false }
    $wordCount = @($body -split '\s+' | Where-Object { $_ -ne '' }).Count
    if ($wordCount -gt $maxWords) { return $false }
    return $true
}

# S0850: delta mode - same Test-TrivialLine heuristic as the full scan, applied per changed
# file (working vs HEAD), mirroring the flavor-flags/deprecated-pm wiring from S0848 Phase 04.
if ($ChangedFiles) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    $scoped = @($ChangedFiles | Where-Object { ($_ -replace '\\', '/') -match 'app_v2/src/main/' })
    $countFn = {
        param($t)
        $n = 0
        foreach ($ln in ($t -split "`r?`n")) { if (Test-TrivialLine $ln) { $n++ } }
        $n
    }
    $d = Measure-ChangedFileGrowth -ChangedFiles $scoped -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn
    Write-Host ("trivial-comments [delta over changed files]: new occurrences {0}" -f $d.Growth)
    if ($Gate -and $d.Growth -gt 0) {
        foreach ($p in $d.PerFile) { if ($p.New -gt 0) { Write-Host ("  +{0} in {1}" -f $p.New, $p.Path) } }
        Write-Host "FAIL: new trivial comment introduced in the changed file(s). Remove it (comments explain WHY, not WHAT)."
        exit 1
    }
    exit 0
}

$current = 0
$removed = 0
$hits = [System.Collections.Generic.List[string]]::new()
$files = Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
foreach ($file in $files) {
    $raw = Get-Content -LiteralPath $file.FullName -Raw
    if ([string]::IsNullOrEmpty($raw)) { continue }
    $eol = if ($raw -match "`r`n") { "`r`n" } else { "`n" }
    $lines = $raw -split "`r?`n"
    $trivialIdx = [System.Collections.Generic.List[int]]::new()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if (Test-TrivialLine $lines[$i]) {
            $trivialIdx.Add($i)
            $current++
            if ($List) {
                $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $hits.Add(("{0}:{1}  {2}" -f $rel, ($i + 1), $lines[$i].Trim()))
            }
        }
    }
    if ($Fix -and $trivialIdx.Count -gt 0) {
        $skip = [System.Collections.Generic.HashSet[int]]::new()
        foreach ($idx in $trivialIdx) { [void]$skip.Add($idx) }
        $kept = [System.Collections.Generic.List[string]]::new()
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if (-not $skip.Contains($i)) { $kept.Add($lines[$i]) }
        }
        $newText = ($kept -join $eol)
        [System.IO.File]::WriteAllText($file.FullName, $newText, [System.Text.UTF8Encoding]::new($false))
        $removed += $trivialIdx.Count
    }
}

if ($List) {
    foreach ($h in $hits) { Write-Host $h }
    Write-Host ''
}

if ($Fix) {
    Write-Host "trivial-comments -Fix: removed $removed standalone trivial comment line(s)."
    Write-Host "Re-run the detector and a build to confirm. Then -UpdateBaseline to ratchet."
    exit 0
}

if ($PSCmdlet.ParameterSetName -eq 'Update') {
    if (-not (Test-Path $baselineFile)) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "trivial-comments baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "trivial-comments baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "trivial-comments baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Trivial comments grew - remove them instead of widening the cap."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "trivial-comments: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("trivial-comments in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: trivial comment count grew above baseline. Remove the new trivial comments (or justify and refactor)."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
