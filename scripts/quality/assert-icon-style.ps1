#requires -Version 7.0
<#
.SYNOPSIS
    S3433: every product glyph holds the measured icon style of ICON-RENDER 0.10 section 10 item A,
    and every icon size sits on the tiers of item E.

.DESCRIPTION
    The portfolio icon contract (catalog folder iconography/, cited as ICON-RENDER) makes the common
    style measurable: a 24 grid, one paint, stroke width 2, one unit of margin, the ink box or the ink
    mass centred within one unit, an estimated line weight of at least 1.3. Measured 2026-09-23, the
    phone drifted at the edges - 25 glyphs without a margin, 13 stroked at five widths, 11 multi-colour
    drawings, a hand-lettered OCR at weight 1.01 - and nothing stopped the next one. This gate does.

    It converts every `ic_*.xml` <vector> under app_v2/src/main, app_v2/src/launcherEnabled,
    app_v2/src/screenCapture and wear/src/main drawables with the converter the contract exporter
    uses (scripts/docs/lib/icon-contract-svg.ps1), measures the ink with
    scripts/docs/lib/measure_glyph_style.py and judges it with scripts/docs/lib/icon-style-rules.ps1,
    so the gate and the contract's style report can never disagree about a picture.

    S3430 adds rule `tint` (ICON-RENDER rule 2), phone glyphs only: a <vector> that paints a literal
    colour must declare android:tint, so a call site that sets no tint still gets the theme's colour
    (scripts/quality/lib/icon-tint-rule.ps1).

    The only way past a rule is a line in scripts/quality/icon-style-exceptions.txt -
    `name | rule | reason`, `name` being a drawable name (both modules) or `wear:name` (watch only),
    `rule` one of grid, paint, stroke, margin, centre, weight, tint or `*`. A full run also fails a STALE
    exception: a name that no longer exists, or whose excused rules it now passes - a list that only
    grows ends up hiding the defects it was written to explain.

    Sizes: every dimen of app_v2/src/main/res/values*/dimens.xml whose name carries `icon` and `size`
    (or ends in `_icon`), resolved through `@dimen/` aliases, and every literal `app:iconSize` in
    res/layout*/, must be 16, 20, 24, 32, 40 or 48 dp. Exception keys for sizes are `dimen:<name>`,
    `dimen:<name>@<values-qualifier>` and `layout:<file>:<N>dp`, rule `size`.

    Fixed-input shape (S2824): with -ChangedFiles, only the product glyphs in the set are judged; the
    exceptions file or one of the shared libraries that define the rules (scripts/docs/lib/icon-*.ps1,
    measure_glyph_style.py, scripts/quality/lib/icon-tint-rule.ps1) in the set widens the run to every glyph; a dimens.xml or a layout file in the
    set runs the size check; a set with none of them is advisory.

    Exit codes:
      0 - every judged glyph holds the style or is excused; no stale exception (full run).
      1 - a glyph breaks a rule nobody excused, or an exception is stale.
      2 - could not verify: no repo venv, the measure failed, or a drawable could not be converted.
      3 - advisory: -ChangedFiles carries no product glyph, dimens, layout, exceptions file or rule
          library; nothing judged.
#>
param(
    [string] $ChangedFiles = '',
    # accepted for the post-change calling convention; the gate always exits non-zero on a finding
    [switch] $Gate,
    [switch] $Quiet,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
. (Join-Path $RepoRoot 'scripts/docs/lib/icon-contract-svg.ps1')
. (Join-Path $RepoRoot 'scripts/docs/lib/icon-style-rules.ps1')
. (Join-Path $RepoRoot 'scripts/quality/lib/icon-tint-rule.ps1')
Initialize-IconColourMaps $RepoRoot

$exceptionsRel = 'scripts/quality/icon-style-exceptions.txt'
$dirs = [ordered]@{
    'app_v2/src/main/res/drawable'            = ''
    'app_v2/src/launcherEnabled/res/drawable' = ''
    'app_v2/src/screenCapture/res/drawable'   = ''
    'wear/src/main/res/drawable'              = 'wear:'
}

function Get-Verdict([int] $code, [string] $word) {
    Write-Host "assert-icon-style: $word"
    exit $code
}

$all = New-Object System.Collections.Generic.List[object]
foreach ($d in $dirs.Keys) {
    $abs = Join-Path $RepoRoot $d
    if (-not (Test-Path -LiteralPath $abs)) { continue }
    foreach ($f in Get-ChildItem -LiteralPath $abs -Filter 'ic_*.xml' | Sort-Object Name) {
        $all.Add([pscustomobject]@{ Key = $dirs[$d] + $f.BaseName; Name = $f.BaseName; Path = $f.FullName; Rel = ($d + '/' + $f.Name) })
    }
}

$full = [string]::IsNullOrWhiteSpace($ChangedFiles)
$targets = $all
if (-not $full) {
    $set = @($ChangedFiles -split '[,;]' | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })
    if ($set | Where-Object { $_.EndsWith($exceptionsRel) -or $_ -match 'scripts/(docs/lib/(icon-[a-z-]+\.ps1|measure_glyph_style\.py)|quality/lib/icon-tint-rule\.ps1)$' }) { $full = $true }
    else {
        $targets = @($all | Where-Object { $rel = $_.Rel; $set | Where-Object { $_.EndsWith($rel) } })
        $sizeRun = [bool]($set | Where-Object { $_ -match 'app_v2/src/main/res/(values[^/]*/dimens|layout[^/]*/[^/]+)\.xml$' })
        if (-not $targets.Count -and -not $sizeRun) {
            if (-not $Quiet) { Write-Host '  no product glyph, exceptions file or rule library in the set - nothing judged' }
            Get-Verdict 3 'ADVISORY'
        }
    }
}

if ($full) { $sizeRun = $true }
$exceptions = Read-StyleExceptions (Join-Path $RepoRoot $exceptionsRel)
$jobs = New-Object System.Collections.Generic.List[object]
$structure = @{}
foreach ($t in $targets) {
    $st = Get-DrawableStructure $t.Path
    if (-not $st) { continue }
    try { $svg = (Convert-Drawable $t.Path $false).Svg }
    catch {
        Write-Host "  $($t.Rel): cannot convert - $($_.Exception.Message)"
        Get-Verdict 2 'COULD NOT VERIFY'
    }
    $structure[$t.Key] = $st
    $jobs.Add(@{ name = $t.Key; svg = $svg; viewportW = $st.ViewportW; viewportH = $st.ViewportH })
}
try { $metrics = if ($jobs.Count) { Invoke-GlyphMeasure $RepoRoot $jobs } else { @{} } }
catch {
    Write-Host "  $($_.Exception.Message)"
    Get-Verdict 2 'COULD NOT VERIFY'
}

$IconTiers = @(16, 20, 24, 32, 40, 48)
function Test-IconSizeTiers([string] $root, [hashtable] $exceptions, [bool] $reportStale) {
    $out = New-Object System.Collections.Generic.List[string]
    $used = @{}
    $resDir = Join-Path $root 'app_v2/src/main/res'
    $byQual = [ordered]@{}
    foreach ($d in Get-ChildItem -LiteralPath $resDir -Directory -Filter 'values*' | Sort-Object Name) {
        $f = Join-Path $d.FullName 'dimens.xml'
        if (-not (Test-Path -LiteralPath $f)) { continue }
        $map = @{}
        foreach ($m in [regex]::Matches([System.IO.File]::ReadAllText($f), '<dimen name="([^"]+)">([^<]+)</dimen>')) { $map[$m.Groups[1].Value] = $m.Groups[2].Value.Trim() }
        $byQual[$d.Name] = $map
    }
    function Resolve-Dimen([string] $qual, [string] $raw) {
        for ($i = 0; $i -lt 6 -and $raw -match '^@dimen/(.+)$'; $i++) {
            $n = $Matches[1]
            $raw = if ($byQual[$qual].ContainsKey($n)) { $byQual[$qual][$n] } elseif ($byQual['values'].ContainsKey($n)) { $byQual['values'][$n] } else { $null }
            if ($null -eq $raw) { return $null }
        }
        return $raw
    }
    function Get-SizeExcuse([string[]] $keys) {
        foreach ($k in $keys) { if ($exceptions.ContainsKey($k) -and ($exceptions[$k].ContainsKey('size') -or $exceptions[$k].ContainsKey('*'))) { return $k } }
        return $null
    }
    foreach ($qual in $byQual.Keys) {
        foreach ($name in ($byQual[$qual].Keys | Sort-Object)) {
            if ($name -notmatch 'icon' -or -not ($name -match 'size' -or $name -match '_icon$') -or $name -match 'margin|padding|spacing|gap|min_width|text|layout_(width|height)') { continue }
            $v = Resolve-Dimen $qual $byQual[$qual][$name]
            $ok = $v -match '^(\d+)dp$' -and ($IconTiers -contains [int]$Matches[1])
            if ($ok) { continue }
            $by = Get-SizeExcuse @("dimen:$name@$qual", "dimen:$name")
            if ($by) { $used[$by] = $true; continue }
            $out.Add("$qual/dimens.xml: size - $name = $v (tiers: $($IconTiers -join ', ') dp)")
        }
    }
    foreach ($lf in Get-ChildItem -LiteralPath $resDir -Directory -Filter 'layout*' | ForEach-Object { Get-ChildItem -LiteralPath $_.FullName -Filter '*.xml' } | Sort-Object FullName) {
        foreach ($m in [regex]::Matches([System.IO.File]::ReadAllText($lf.FullName), 'app:iconSize="(\d+)dp"')) {
            if ($IconTiers -contains [int]$m.Groups[1].Value) { continue }
            $key = "layout:$($lf.Name):$($m.Groups[1].Value)dp"
            $by = Get-SizeExcuse @($key)
            if ($by) { $used[$by] = $true; continue }
            $out.Add("$($lf.Directory.Name)/$($lf.Name): size - literal app:iconSize $($m.Groups[1].Value)dp; use @dimen/icon_tier_NN")
        }
    }
    if ($reportStale) {
        foreach ($k in ($exceptions.Keys | Where-Object { $_ -match '^(dimen|layout):' } | Sort-Object)) {
            if (-not $used.ContainsKey($k)) { $out.Add("$exceptionsRel`: '$k' is stale - that size is on a tier now, or gone") }
        }
    }
    return $out
}
function Get-Excuse([string] $key, [string] $name, [string] $rule) {
    foreach ($k in $key, $name) {
        if ($exceptions.ContainsKey($k)) {
            $e = $exceptions[$k]
            if ($e.ContainsKey($rule)) { return $k }
            if ($e.ContainsKey('*')) { return $k }
        }
    }
    return $null
}

$failures = New-Object System.Collections.Generic.List[string]
$used = @{}
foreach ($t in $targets) {
    if (-not $structure.ContainsKey($t.Key)) { continue }
    foreach ($v in (Test-GlyphStyle $structure[$t.Key] $metrics[$t.Key])) {
        $by = Get-Excuse $t.Key $t.Name $v.Rule
        if ($by) { $used["$by|$($v.Rule)"] = $true; $used["$by|*"] = $true; continue }
        $failures.Add("$($t.Rel): $($v.Rule) - $($v.Value)")
    }
}
# Rule `tint` is the phone's alone: the watch draws every glyph through Compose, which tints it itself.
foreach ($t in $targets) {
    if ($t.Key.StartsWith('wear:')) { continue }
    $baked = Test-GlyphTint $t.Path
    if (-not $baked) { continue }
    $by = Get-Excuse $t.Key $t.Name 'tint'
    if ($by) { $used["$by|tint"] = $true; $used["$by|*"] = $true; continue }
    $failures.Add("$($t.Rel): tint - $baked")
}

if ($full) {
    $keys = @{}
    foreach ($t in $all) { $keys[$t.Key] = $true; $keys[$t.Name] = $true }
    foreach ($name in ($exceptions.Keys | Where-Object { $_ -notmatch '^(dimen|layout):' } | Sort-Object)) {
        if (-not $keys.ContainsKey($name)) { $failures.Add("$exceptionsRel`: '$name' names no glyph - remove the line"); continue }
        foreach ($rule in ($exceptions[$name].Keys | Sort-Object)) {
            if (-not $used.ContainsKey("$name|$rule")) { $failures.Add("$exceptionsRel`: '$name | $rule' is stale - the glyph now passes that rule") }
        }
    }
}

if ($sizeRun) {
    $sizeFindings = Test-IconSizeTiers $RepoRoot $exceptions $full
    foreach ($f in $sizeFindings) { $failures.Add($f) }
}

if (-not $Quiet) { Write-Host ("  judged: $($structure.Count) glyph(s); exceptions: $($exceptions.Count) name(s)" + $(if ($full) { '; full run' } else { '' })) }
if ($failures.Count) {
    foreach ($f in $failures) { Write-Host "  - $f" }
    Write-Host '  fix: bring the glyph inside ICON-RENDER 0.10 section 10 item A or the size onto a tier (@dimen/icon_tier_NN),'
    Write-Host '       or add "name | rule | reason" to scripts/quality/icon-style-exceptions.txt; delete a stale line'
    Get-Verdict 1 'FAIL'
}
Get-Verdict 0 'PASS'
