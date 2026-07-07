<#
.SYNOPSIS
    S0558 - HOW_TO settings-path drift gate.

.DESCRIPTION
    Validates the "Settings -> .. -> <label>" navigation recipes embedded in the
    HOW_TO guides (EN/RU/UK) against the live settings manifest, so a renamed
    setting, a stale group header, or a wrong tab in the manuals is caught the
    same way SETTINGS_REFERENCE drift already is (S0440).

    A recipe is any inline arrow chain (segments joined by U+2192) whose first
    cleaned segment is the localized word for "Settings". For each recipe:
      1. the tab (segment 1) must be a known tab -> resolves the destination,
      2. every following segment must resolve to one of
           - a Media sub-section label (only under the Media tab),
           - a manifest entry title under that destination,
           - an allow-listed non-manifest screen label under that destination,
         the last segment may carry trailing prose (matched as a prefix),
      3. a matched leaf must belong to the named tab (catches a wrong section).
    Cross-locale parity: every locale must have the same number of recipes and,
    positionally, the same locale-independent signature (tab destination plus
    resolved segment tokens), so a recipe added / removed / reordered / repointed
    in one language only is caught.

    Vocab (tab + sub-section + allow-listed screen display names per locale)
    lives in docs/settings/howto-path-vocab.json and is self-checked for
    completeness against the manifest destination enum.

    Pure text analysis - no JVM/gradle. Exit 0 only when everything resolves and
    the locales are in parity. Run with -Gate from post-change.ps1; the switch is
    cosmetic so the call site reads intentionally.
#>
param(
    [switch] $Gate,
    # S0945 Phase D: narrative guides (README/QUICK_START/FAQ/TROUBLESHOOTING) are now
    # scanned unconditionally alongside HOW_TO. This switch is retained as a no-op so
    # existing invocations (`-IncludeNarrative`) keep working; it no longer gates anything.
    [switch] $IncludeNarrative,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$manifestPath = Join-Path $RepoRoot 'docs/settings/settings-manifest.json'
$vocabPath    = Join-Path $RepoRoot 'docs/settings/howto-path-vocab.json'

if (-not (Test-Path $manifestPath)) { Write-Host "howto-settings-paths: manifest not found: $manifestPath" -ForegroundColor Red; exit 1 }
if (-not (Test-Path $vocabPath))    { Write-Host "howto-settings-paths: vocab not found: $vocabPath" -ForegroundColor Red; exit 1 }

$arrow   = [char]0x2192
$locales = @('en', 'ru', 'uk')
$titleProp  = @{ en = 'titleEn'; ru = 'titleRu'; uk = 'titleUk' }
$anchorWord = @{ en = 'Settings'; ru = 'Настройки'; uk = 'Налаштування' }

$manifest = (Get-Content $manifestPath -Raw | ConvertFrom-Json).entries
$vocab    = Get-Content $vocabPath -Raw | ConvertFrom-Json

# --- tab display name -> destination, per locale -----------------------------
$tabByName = @{}
foreach ($loc in $locales) { $tabByName[$loc] = @{} }
foreach ($t in $vocab.tabs) {
    foreach ($loc in $locales) { $tabByName[$loc][[string]$t.$loc] = [string]$t.destination }
}

# --- candidate labels per (destination, locale) ------------------------------
# Each candidate: @{ name = <display label>; token = <locale-independent id> }
$destinations = $manifest.destination | Sort-Object -Unique
$cand = @{}
foreach ($dest in $destinations) {
    foreach ($loc in $locales) {
        $list = New-Object System.Collections.ArrayList
        if ($dest -eq 'MEDIA') {
            foreach ($s in $vocab.mediaSubsections) {
                [void]$list.Add(@{ name = [string]$s.$loc; token = 'sub:' + $s.sectionId })
            }
        }
        $entriesForDest = $manifest | Where-Object { $_.destination -eq $dest }
        $grouped = $entriesForDest | Group-Object -Property $titleProp[$loc]
        foreach ($g in $grouped) {
            if ([string]::IsNullOrWhiteSpace($g.Name)) { continue }
            $keys = ($g.Group.key | Sort-Object) -join '+'
            [void]$list.Add(@{ name = [string]$g.Name; token = 'key:' + $keys })
        }
        foreach ($scr in $vocab.allowedScreens) {
            if ([string]$scr.destination -eq $dest) {
                [void]$list.Add(@{ name = [string]$scr.$loc; token = 'screen:' + $scr.id })
            }
        }
        $cand["$dest|$loc"] = $list
    }
}

$failures = New-Object System.Collections.ArrayList

# --- vocab completeness: every manifest tab has a name in every locale --------
foreach ($dest in $destinations) {
    foreach ($loc in $locales) {
        $hasName = $false
        foreach ($n in $tabByName[$loc].Keys) { if ($tabByName[$loc][$n] -eq $dest) { $hasName = $true; break } }
        if (-not $hasName) { [void]$failures.Add("vocab: no '$loc' tab display name for manifest destination '$dest' (add it to docs/settings/howto-path-vocab.json)") }
    }
}

# --- helpers -----------------------------------------------------------------
function Clean-Seg([string] $s, [string] $loc) {
    $x = $s.Trim()
    if ($loc -eq 'en') { $x = $x -replace '\s+tab$', '' }
    else { $x = $x -replace '^(вкладка|вкладку)\s+', '' }
    # drop a trailing "= value" assignment ("Always show .. = OFF")
    $x = $x -replace '\s*=\s*.*$', ''
    # strip surrounding quotes (straight + typographic, both languages)
    $q = @('"', "'", '«', '»', '“', '”', '„', '‟', '‹', '›')
    $changed = $true
    while ($changed) {
        $changed = $false
        $x = $x.Trim()
        if ($x.Length -ge 1 -and ($q -contains $x.Substring(0, 1)))             { $x = $x.Substring(1); $changed = $true }
        if ($x.Length -ge 1 -and ($q -contains $x.Substring($x.Length - 1, 1))) { $x = $x.Substring(0, $x.Length - 1); $changed = $true }
    }
    return $x.Trim()
}

function Test-Boundary([string] $seg, [int] $len) {
    if ($len -ge $seg.Length) { return $true }
    $c = $seg.Substring($len, 1)
    return ($c -notmatch '[\p{L}\p{N}]')
}

# S0945: is this "Settings -> .." chain a path into the Android *system* Settings
# app (Apps / Permissions / the app's own product entry) rather than the in-app
# screen? Such chains are out of the manifest gate's scope and skipped silently.
function Test-SystemPath([object[]] $segs, [string] $loc) {
    $sys = $vocab.systemSettingsNodes
    if (-not $sys -or $segs.Count -lt 1) { return $false }
    $prod = [string]$sys.productName
    foreach ($s in $segs) {
        if ($prod -and ((Clean-Seg $s $loc) -eq $prod)) { return $true }
    }
    $first = Clean-Seg $segs[0] $loc
    foreach ($n in $sys.$loc) { if ($first -eq [string]$n) { return $true } }
    return $false
}

# S0945: narrative prose "Settings -> General to do X .." names a real tab followed
# by free prose (no further arrow). Resolve it as a bare-tab reference (prose
# dropped) so a legitimate loose mention is not reported as a broken recipe. Word
# boundary required, so 'Input'/'Interface'/'Audio' (no real tab prefix) still fail.
function Resolve-TabPrefix([string] $seg, [string] $loc) {
    foreach ($name in $tabByName[$loc].Keys) {
        $n = [string]$name
        if ($n.Length -gt 0 -and $seg.Length -gt $n.Length -and
            $seg.StartsWith($n, [System.StringComparison]::Ordinal) -and
            (Test-Boundary $seg $n.Length)) {
            return $tabByName[$loc][$n]
        }
    }
    return $null
}

# Resolve a non-tab segment to a token, or $null. Last segment may carry prose
# (prefix match against the authoritative label set, longest label wins).
function Resolve-Seg([string] $seg, [string] $dest, [string] $loc, [bool] $isLast) {
    $list = $cand["$dest|$loc"]
    if (-not $list) { return $null }
    foreach ($c in $list) { if ($c.name -ceq $seg) { return $c.token } }
    if ($isLast) {
        $best = $null; $bestLen = -1
        foreach ($c in $list) {
            $n = [string]$c.name
            if ($n.Length -gt 0 -and $seg.StartsWith($n, [System.StringComparison]::Ordinal) -and (Test-Boundary $seg $n.Length)) {
                if ($n.Length -gt $bestLen) { $bestLen = $n.Length; $best = $c }
            }
        }
        if ($best) { return $best.token }
    }
    return $null
}

# --- per-file scan (extracted for multi-file-group reuse, S0945) --------------
function Scan-File([string] $relPath, [string] $loc) {
    $recs      = New-Object System.Collections.ArrayList
    $localFail = New-Object System.Collections.ArrayList
    $path = Join-Path $RepoRoot $relPath
    if (-not (Test-Path $path)) {
        [void]$localFail.Add("missing guide file: $relPath")
        return @{ recs = $recs; failures = $localFail }
    }
    $lines  = [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)
    $anchor = $anchorWord[$loc]

    for ($ln = 0; $ln -lt $lines.Count; $ln++) {
        $line = $lines[$ln]
        if ($line.IndexOf($arrow) -lt 0) { continue }
        if ($line.IndexOf($anchor, [System.StringComparison]::Ordinal) -lt 0) { continue }

        $clean  = $line -replace '\*\*', ''
        $pieces = $clean.Split($arrow)
        $trim   = @($pieces | ForEach-Object { $_.Trim() })

        $aIdx = -1
        for ($i = 0; $i -lt $trim.Count; $i++) {
            $p = $trim[$i]
            if ($p -ceq $anchor -or $p.EndsWith(" $anchor", [System.StringComparison]::Ordinal)) { $aIdx = $i; break }
        }
        if ($aIdx -lt 0 -or $aIdx -ge ($trim.Count - 1)) { continue }

        $segs   = @($trim[($aIdx + 1)..($trim.Count - 1)])
        $lineNo = $ln + 1

        # S0945: Android system-Settings path (Apps / Permissions / product name) -> out of app scope.
        if (Test-SystemPath $segs $loc) { continue }

        $tabSeg = Clean-Seg $segs[0] $loc
        $dest   = $tabByName[$loc][$tabSeg]
        if (-not $dest) { $dest = Resolve-TabPrefix $tabSeg $loc }  # S0945: bare-tab prose reference
        if (-not $dest) {
            [void]$localFail.Add("${relPath}:$lineNo - unknown settings tab '$tabSeg' | $($segs -join ' > ')")
            continue
        }

        $rest = @()
        if ($segs.Count -gt 1) { $rest = @($segs[1..($segs.Count - 1)]) }
        $tokens = New-Object System.Collections.ArrayList
        $ok = $true
        for ($k = 0; $k -lt $rest.Count; $k++) {
            $isLast = ($k -eq ($rest.Count - 1))
            $sc  = Clean-Seg $rest[$k] $loc
            $tok = Resolve-Seg $sc $dest $loc $isLast
            if (-not $tok) {
                [void]$localFail.Add("${relPath}:$lineNo - segment '$sc' has no matching setting/header/sub-section under the '$tabSeg' tab | $($segs -join ' > ')")
                $ok = $false; break
            }
            [void]$tokens.Add($tok)
        }
        if (-not $ok) { continue }

        [void]$recs.Add(@{ line = $lineNo; sig = "$dest|" + ($tokens -join '>'); path = ($segs -join ' > ') })
    }
    return @{ recs = $recs; failures = $localFail }
}

# --- file groups (S0945) ------------------------------------------------------
# HOW_TO keeps positional cross-locale parity (step-by-step recipes are authored
# 1:1 across locales). Narrative guides are resolve-only: their prose reorders
# recipes across languages, so positional parity would false-positive - they are
# still held to full manifest-truth resolution of every "Settings -> .." chain.
# Narrative groups are ALWAYS scanned (S0945 Phase D promotion); the discriminator
# above (system-path skip + bare-tab prose prefix) keeps prose false-positives out.
# -IncludeNarrative is retained as a no-op so existing invocations do not break.
$fileGroups = [System.Collections.ArrayList]@(
    @{ name = 'HOW_TO';          parity = $true;  files = @{ en = 'docs/HOW_TO.md';           ru = 'docs/HOW_TO_RU.md';           uk = 'docs/HOW_TO_UK.md' } }
    @{ name = 'README';          parity = $false; files = @{ en = 'docs/README.md';           ru = 'docs/README_RU.md';           uk = 'docs/README_UK.md' } }
    @{ name = 'QUICK_START';     parity = $false; files = @{ en = 'docs/QUICK_START.md';      ru = 'docs/QUICK_START_RU.md';      uk = 'docs/QUICK_START_UK.md' } }
    @{ name = 'FAQ';             parity = $false; files = @{ en = 'docs/FAQ.md';              ru = 'docs/FAQ_RU.md';              uk = 'docs/FAQ_UK.md' } }
    @{ name = 'TROUBLESHOOTING'; parity = $false; files = @{ en = 'docs/TROUBLESHOOTING.md';  ru = 'docs/TROUBLESHOOTING_RU.md';  uk = 'docs/TROUBLESHOOTING_UK.md' } }
)

# --- scan every group ---------------------------------------------------------
$totalRecipes = 0
foreach ($grp in $fileGroups) {
    $groupResults = @{}
    $grpHadFail   = $false
    foreach ($loc in $locales) {
        $scan = Scan-File $grp.files[$loc] $loc
        if ($scan.failures.Count -gt 0) { $grpHadFail = $true }
        foreach ($f in $scan.failures) { [void]$failures.Add($f) }
        $groupResults[$loc] = $scan.recs
    }

    if ($grp.parity -and -not $grpHadFail -and
        $groupResults.ContainsKey('en') -and $groupResults.ContainsKey('ru') -and $groupResults.ContainsKey('uk')) {
        $cEn = $groupResults['en'].Count; $cRu = $groupResults['ru'].Count; $cUk = $groupResults['uk'].Count
        if (-not ($cEn -eq $cRu -and $cEn -eq $cUk)) {
            [void]$failures.Add("[$($grp.name)] locale parity: recipe counts differ - en=$cEn ru=$cRu uk=$cUk")
        }
        else {
            for ($i = 0; $i -lt $cEn; $i++) {
                $se = $groupResults['en'][$i]; $sr = $groupResults['ru'][$i]; $su = $groupResults['uk'][$i]
                if (-not ($se.sig -ceq $sr.sig -and $se.sig -ceq $su.sig)) {
                    [void]$failures.Add("[$($grp.name)] locale parity mismatch at recipe #$($i + 1): en[L$($se.line)]='$($se.sig)' ; ru[L$($sr.line)]='$($sr.sig)' ; uk[L$($su.line)]='$($su.sig)'")
                }
            }
        }
    }

    if ($groupResults.ContainsKey('en')) { $totalRecipes += $groupResults['en'].Count }
}

# --- verdict ------------------------------------------------------------------
if ($failures.Count -gt 0) {
    Write-Host "howto-settings-paths: FAIL ($($failures.Count) issue(s))" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}

Write-Host "howto-settings-paths: OK - $totalRecipes recipes across $($fileGroups.Count) guide group(s), all paths resolve against the manifest; HOW_TO locales in parity." -ForegroundColor Green
exit 0
