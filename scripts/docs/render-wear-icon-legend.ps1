#requires -Version 7.0
<#
.SYNOPSIS
    S3442 - render the watch icon legend: every glyph the watch draws, beside its ICON-SET name.

.DESCRIPTION
    The phone's legend (render-icon-legend.ps1) is read from the phone's icon inventory. The watch has
    no such inventory, so this legend is read from the watch source itself:
      - every ic_* drawable wear/src references (R.drawable.ic_x in Kotlin, @drawable/ic_x in XML);
      - minus the product-private patterns of docs/icons/icon-contract-map.json (ICON-SET rule 7);
      - each mapped to its vocabulary meaning the way scripts/quality/assert-icon-contract.ps1 maps
        it: the record's ref / states / levels / variants, then the declaration's 'glyphs';
      - named with the meaning's canonical EN / RU / UK name from the vocabulary (ICON-SET rule 3),
        never with a label of its own.
    A drawable that maps to no meaning is not listed here - that is an icon-contract finding, judged
    and baselined by assert-icon-contract.ps1. Controls drawn with Compose Material image vectors
    carry no drawable and are outside this legend (S3482).

    Outputs, all generated and never hand-edited:
      docs/wear/ICON_LEGEND.md, ICON_LEGEND-ru.md, ICON_LEGEND-uk.md
      docs/icons/svg/wear/<drawable>.svg - converted from wear/src/main/res/drawable through
        lib/vectordrawable-svg.ps1, the converter both phone exporters use; stale files are pruned.
    Deterministic: fixed order, LF newlines, UTF-8 without BOM; a rerun on unchanged inputs is
    byte-identical.

    The vocabulary lives in the shared contracts catalog: -CatalogRoot, then FMS_CONTRACTS_ROOT in the
    process, then the same variable at user scope.

    Exit codes:
      0 - the legend pages and their SVGs were written.
      1 - a referenced, mapped drawable has no source file or cannot be converted; nothing was written.
      2 - the vocabulary or the declaration is missing or unreadable; nothing was written.
      4 - Code.Scripts is held by another session: nothing was written, the place in the queue is
          held, wait for the turn and rerun.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $CatalogRoot
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')
. (Join-Path $PSScriptRoot 'lib/vectordrawable-svg.ps1')

if (-not $CatalogRoot) { $CatalogRoot = $env:FMS_CONTRACTS_ROOT }
if (-not $CatalogRoot) { $CatalogRoot = [Environment]::GetEnvironmentVariable('FMS_CONTRACTS_ROOT', 'User') }
$vocabPath = if ($CatalogRoot) { Join-Path $CatalogRoot 'iconography/vocabulary.jsonl' } else { '' }
$declPath = Join-Path $RepoRoot 'docs/icons/icon-contract-map.json'
if (-not $vocabPath -or -not (Test-Path -LiteralPath $vocabPath)) {
    Write-Host "render-wear-icon-legend: no vocabulary (pass -CatalogRoot or set FMS_CONTRACTS_ROOT)"
    exit 2
}
if (-not (Test-Path -LiteralPath $declPath)) { Write-Host "render-wear-icon-legend: no $declPath"; exit 2 }
try {
    $vocab = @(Get-Content -LiteralPath $vocabPath -Encoding utf8 | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
    $decl = Get-Content -LiteralPath $declPath -Raw -Encoding utf8 | ConvertFrom-Json
}
catch {
    Write-Host "render-wear-icon-legend: unreadable input - $($_.Exception.Message)"
    exit 2
}

# ---- drawable -> meanings, for the watch ----------------------------------------------------
$meanings = @{}
$glyphIndex = @{}
function Add-WearGlyph([string] $Raw, [string] $Id) {
    if (-not $Raw) { return }
    $name = $Raw
    if ($Raw -match '^([A-Za-z_0-9]+):(.+)$') {
        # A source-set prefix other than wear: names a phone-only file.
        if ($Matches[1] -ne 'wear') { return }
        $name = $Matches[2]
    }
    if (-not $glyphIndex.ContainsKey($name)) { $glyphIndex[$name] = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal) }
    [void]$glyphIndex[$name].Add($Id)
}
foreach ($r in $vocab) {
    $meanings[$r.id] = $r
    if ($r.PSObject.Properties['ref']) {
        foreach ($p in $r.ref.PSObject.Properties) { if ($p.Name -in 'android', 'wear') { Add-WearGlyph ([string]$p.Value) $r.id } }
    }
    foreach ($k in 'states', 'variants') {
        if ($r.PSObject.Properties[$k]) { foreach ($p in $r.$k.PSObject.Properties) { Add-WearGlyph ([string]$p.Value) $r.id } }
    }
    if ($r.PSObject.Properties['levels']) { foreach ($l in $r.levels) { Add-WearGlyph ([string]$l) $r.id } }
}
if ($decl.PSObject.Properties['glyphs']) {
    foreach ($p in $decl.glyphs.PSObject.Properties) {
        if ($p.Name -match '^app_v2:') { continue }
        Add-WearGlyph ($p.Name -replace '^wear:', '') ([string]$p.Value)
    }
}
$private = @()
if ($decl.PSObject.Properties['private']) { $private = @($decl.private | ForEach-Object { [string]$_.pattern }) }

# ---- what the watch references ----------------------------------------------------------------
$refRx = [regex]'(?:R\.drawable\.|@drawable/)(ic_[a-z0-9_]+)'
$referenced = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
foreach ($f in Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'wear/src') -Recurse -File -Include '*.kt', '*.xml') {
    foreach ($m in $refRx.Matches([IO.File]::ReadAllText($f.FullName))) { [void]$referenced.Add($m.Groups[1].Value) }
}
$drawableDir = Join-Path $RepoRoot 'wear/src/main/res/drawable'
$rows = [System.Collections.Generic.List[object]]::new()
$problems = [System.Collections.Generic.List[string]]::new()
$svgs = @{}
foreach ($name in $referenced) {
    if (@($private | Where-Object { $name -like $_ }).Count -gt 0) { continue }
    if (-not $glyphIndex.ContainsKey($name)) { continue }
    $src = Join-Path $drawableDir ($name + '.xml')
    if (-not (Test-Path -LiteralPath $src)) { $problems.Add("$name - no source at wear/src/main/res/drawable"); continue }
    $conv = Convert-VectorToSvg $src
    if ($conv.Skip) { $problems.Add("$name - $($conv.Skip)"); continue }
    $svgs[$name] = $conv.Svg
    foreach ($id in $glyphIndex[$name]) { $rows.Add([pscustomobject]@{ id = $id; group = [string]$meanings[$id].group; drawable = $name }) }
}
if ($problems.Count -gt 0) {
    Write-Host 'render-wear-icon-legend: these watch glyphs cannot be shown; nothing was written:'
    foreach ($p in $problems) { Write-Host "  - $p" }
    exit 1
}

# ---- pages --------------------------------------------------------------------------------------
$groupOrder = @('navigation', 'media', 'view', 'action', 'tool', 'content', 'source', 'status', 'app', 'system', 'feature', 'weather', 'device', 'camera', 'brand')
$groupTitle = @{
    navigation = @{ en = 'Navigation'; ru = 'Навигация'; uk = 'Навігація' }
    media = @{ en = 'Playback'; ru = 'Воспроизведение'; uk = 'Відтворення' }
    view = @{ en = 'View'; ru = 'Вид'; uk = 'Вигляд' }
    action = @{ en = 'Actions'; ru = 'Действия'; uk = 'Дії' }
    tool = @{ en = 'Drawing tools'; ru = 'Инструменты рисования'; uk = 'Інструменти малювання' }
    content = @{ en = 'Content'; ru = 'Содержимое'; uk = 'Вміст' }
    source = @{ en = 'Sources'; ru = 'Источники'; uk = 'Джерела' }
    status = @{ en = 'Status'; ru = 'Состояние'; uk = 'Стан' }
    app = @{ en = 'App'; ru = 'Приложение'; uk = 'Застосунок' }
    system = @{ en = 'System'; ru = 'Система'; uk = 'Система' }
    feature = @{ en = 'Programs'; ru = 'Программы'; uk = 'Програми' }
    weather = @{ en = 'Weather'; ru = 'Погода'; uk = 'Погода' }
    device = @{ en = 'Devices'; ru = 'Устройства'; uk = 'Пристрої' }
    camera = @{ en = 'Camera'; ru = 'Камера'; uk = 'Камера' }
    brand = @{ en = 'Services'; ru = 'Сервисы'; uk = 'Сервіси' }
}
$pageTitle = @{ en = 'Watch icon legend'; ru = 'Легенда значков часов'; uk = 'Легенда значків годинника' }
$intro = @{
    en = 'These are the icons FastMedia draws on the Wear OS watch, each beside the one name that icon carries in every FastMedia surface - the phone, the watch, this manual and the site.'
    ru = 'Это значки, которые FastMedia рисует на часах Wear OS, и рядом с каждым - единственное название, которое этот значок носит везде в FastMedia: на телефоне, на часах, в этом руководстве и на сайте.'
    uk = 'Це значки, які FastMedia малює на годиннику Wear OS, і поряд із кожним - єдина назва, яку цей значок має всюди у FastMedia: на телефоні, на годиннику, у цьому посібнику та на сайті.'
}
$colIcon = @{ en = 'Icon'; ru = 'Значок'; uk = 'Значок' }
$colName = @{ en = 'Meaning'; ru = 'Значение'; uk = 'Значення' }
$permalink = @{ en = '/docs/wear/ICON_LEGEND.html'; ru = '/docs/wear/ICON_LEGEND_RU.html'; uk = '/docs/wear/ICON_LEGEND_UK.html' }
$fileName = @{ en = 'ICON_LEGEND.md'; ru = 'ICON_LEGEND-ru.md'; uk = 'ICON_LEGEND-uk.md' }

$pages = @{}
foreach ($loc in 'en', 'ru', 'uk') {
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append("---`nlayout: default`ntitle: `"$($pageTitle[$loc])`"`npermalink: $($permalink[$loc])`n---`n")
    [void]$sb.Append("<!-- GENERATED by scripts/docs/render-wear-icon-legend.ps1 from the watch source, docs/icons/icon-contract-map.json and the ICON-SET vocabulary. Do not edit by hand. -->`n`n")
    [void]$sb.Append("# $($pageTitle[$loc])`n`n$($intro[$loc])`n")
    foreach ($g in $groupOrder) {
        $inGroup = @($rows | Where-Object { $_.group -eq $g } | Sort-Object -Property @{ Expression = { $_.id } }, @{ Expression = { $_.drawable } })
        if ($inGroup.Count -eq 0) { continue }
        [void]$sb.Append("`n## $($groupTitle[$g][$loc])`n`n| $($colIcon[$loc]) | $($colName[$loc]) |`n|---|---|`n")
        foreach ($r in $inGroup) {
            $name = [string]$meanings[$r.id].name.$loc
            [void]$sb.Append("| <img src=`"../icons/svg/wear/$($r.drawable).svg`" alt=`"`" width=`"24`" height=`"24`"> | $name |`n")
        }
    }
    $pages[$loc] = $sb.ToString()
}

# ---- write ----------------------------------------------------------------------------------
$svgDir = Join-Path $RepoRoot 'docs/icons/svg/wear'
$outDir = Join-Path $RepoRoot 'docs/wear'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$codeScope = $null
try {
    $codeScope = Enter-CodeLockOrExit -Path @($svgDir, (Join-Path $outDir 'ICON_LEGEND.md')) -Reason 'render-wear-icon-legend.ps1 (docs/wear legend + svg)'
    $null = New-Item -ItemType Directory -Force -Path $svgDir
    foreach ($name in $svgs.Keys) { [IO.File]::WriteAllText((Join-Path $svgDir ($name + '.svg')), $svgs[$name], $utf8) }
    $pruned = 0
    Get-ChildItem -LiteralPath $svgDir -File | Where-Object { -not $svgs.ContainsKey($_.BaseName) -or $_.Extension -ne '.svg' } | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Force
        $pruned++
    }
    foreach ($loc in 'en', 'ru', 'uk') { [IO.File]::WriteAllText((Join-Path $outDir $fileName[$loc]), $pages[$loc], $utf8) }
}
finally { Exit-CodeLockScope -Scope $codeScope }

Write-Host "render-wear-icon-legend: $($rows.Count) row(s), $($svgs.Count) glyph(s), $pruned stale SVG(s) pruned -> docs/wear/ICON_LEGEND*.md"
exit 0
