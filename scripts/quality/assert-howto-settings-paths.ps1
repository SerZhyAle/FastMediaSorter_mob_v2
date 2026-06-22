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
$files      = @{ en = 'docs/HOW_TO.md'; ru = 'docs/HOW_TO_RU.md'; uk = 'docs/HOW_TO_UK.md' }

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

# --- scan each locale ---------------------------------------------------------
$results = @{}
foreach ($loc in $locales) {
    $path = Join-Path $RepoRoot $files[$loc]
    if (-not (Test-Path $path)) { [void]$failures.Add("missing HOW_TO file: $($files[$loc])"); continue }
    $lines  = [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)
    $anchor = $anchorWord[$loc]
    $recs   = New-Object System.Collections.ArrayList

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

        $tabSeg = Clean-Seg $segs[0] $loc
        $dest   = $tabByName[$loc][$tabSeg]
        if (-not $dest) {
            [void]$failures.Add("$($files[$loc]):$lineNo - unknown settings tab '$tabSeg' | $($segs -join ' > ')")
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
                [void]$failures.Add("$($files[$loc]):$lineNo - segment '$sc' has no matching setting/header/sub-section under the '$tabSeg' tab | $($segs -join ' > ')")
                $ok = $false; break
            }
            [void]$tokens.Add($tok)
        }
        if (-not $ok) { continue }

        [void]$recs.Add(@{ line = $lineNo; sig = "$dest|" + ($tokens -join '>'); path = ($segs -join ' > ') })
    }
    $results[$loc] = $recs
}

# --- cross-locale parity ------------------------------------------------------
if ($failures.Count -eq 0 -and $results.ContainsKey('en') -and $results.ContainsKey('ru') -and $results.ContainsKey('uk')) {
    $cEn = $results['en'].Count; $cRu = $results['ru'].Count; $cUk = $results['uk'].Count
    if (-not ($cEn -eq $cRu -and $cEn -eq $cUk)) {
        [void]$failures.Add("locale parity: recipe counts differ - en=$cEn ru=$cRu uk=$cUk")
    }
    else {
        for ($i = 0; $i -lt $cEn; $i++) {
            $se = $results['en'][$i]; $sr = $results['ru'][$i]; $su = $results['uk'][$i]
            if (-not ($se.sig -ceq $sr.sig -and $se.sig -ceq $su.sig)) {
                [void]$failures.Add("locale parity mismatch at recipe #$($i + 1): en[L$($se.line)]='$($se.sig)' ; ru[L$($sr.line)]='$($sr.sig)' ; uk[L$($su.line)]='$($su.sig)'")
            }
        }
    }
}

# --- verdict ------------------------------------------------------------------
if ($failures.Count -gt 0) {
    Write-Host "howto-settings-paths: FAIL ($($failures.Count) issue(s))" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}

$n = if ($results.ContainsKey('en')) { $results['en'].Count } else { 0 }
Write-Host "howto-settings-paths: OK - $n recipes per locale, all paths resolve against the manifest, locales in parity." -ForegroundColor Green
exit 0
