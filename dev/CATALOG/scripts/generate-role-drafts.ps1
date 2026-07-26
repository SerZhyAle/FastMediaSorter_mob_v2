# Generates DRAFT `role` strings for catalogue classes whose role is empty, into a review TSV.
# Source priority: class-level KDoc (/** ... */) from the .kt file, else a synthesised draft from
# the class name (camelCase split) + top function names. Nothing is written back to the catalogue
# here - that is apply-role-drafts.ps1's job. Companion tool to backfill the semantic search layer.
#
# Usage:
#   pwsh -NoProfile -File dev/CATALOG/scripts/generate-role-drafts.ps1 -Module app_v2
#   pwsh -NoProfile -File dev/CATALOG/scripts/generate-role-drafts.ps1 -Module app_v2 -Limit 30   # sample
#   pwsh -NoProfile -File dev/CATALOG/scripts/generate-role-drafts.ps1 -Module app_v2 -IncludeAll # not only entry points
#
# Exit codes:
#   0 - success.

param(
    [string]$Module = 'app_v2',
    [string]$OutFile = 'temp/scratch/role-drafts.tsv',
    [int]$Limit = 0,
    [switch]$IncludeAll,
    [string]$EntryRegex = '(ViewModel|Manager|UseCase|Activity|Fragment|Coordinator|Controller|Dispatcher|Handler|Repository|RepositoryImpl|Worker|Service|Facade|Launcher|Gate|Store|Interactor|Presenter|Initializer)$'
)

$ErrorActionPreference = 'Stop'
$Root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$InFile = Join-Path $Root "dev\CATALOG\$Module.jsonl"
if (-not (Test-Path $InFile)) { throw "Catalogue not found: $InFile" }

# All source-set java roots (mirrors scan.ps1) - a class may live in main or any flavor set.
$srcRoots = @()
$srcParent = Join-Path $Root "$Module\src"
if (Test-Path $srcParent) {
    foreach ($d in (Get-ChildItem $srcParent -Directory)) {
        $j = Join-Path $d.FullName 'java'
        if (Test-Path $j) { $srcRoots += $j }
    }
}

function Get-ClassKDoc([string]$text, [string]$class) {
    $esc = [regex]::Escape($class)
    $pattern = '/\*\*(?<body>[\s\S]*?)\*/\s*' +
               '(?:@[A-Za-z][\w.]*(?:\([^)]*\))?\s*)*' +
               '(?:(?:public|internal|private|protected|abstract|open|sealed|data|inner|final|annotation|enum)\s+)*' +
               '(?:class|object|interface)\s+' + $esc + '\b'
    $m = [regex]::Match($text, $pattern)
    if (-not $m.Success) { return '' }
    $body = $m.Groups['body'].Value
    $keep = New-Object System.Collections.ArrayList
    foreach ($ln in ($body -split "`n")) {
        $t = $ln.Trim()
        if ($t.StartsWith('*')) { $t = $t.Substring(1).Trim() }
        if ($t -match '^@(param|return|property|see|throws|constructor|sample|suppress)\b') { continue }
        if ($t) { [void]$keep.Add($t) }
    }
    $roleText = ($keep -join ' ').Trim()
    if ($roleText -match '^(.*?[.])(\s|$)') { $roleText = $matches[1] }
    $roleText = ($roleText -replace '\s+', ' ').Trim()
    if ($roleText.Length -gt 140) {
        $cut = $roleText.Substring(0, 140)
        $sp = $cut.LastIndexOf(' ')
        if ($sp -gt 60) { $cut = $cut.Substring(0, $sp) }
        $roleText = $cut.TrimEnd()
    }
    return $roleText
}

function Get-SynthRole($rec) {
    $camel = ($rec.class -creplace '(?<=[a-z0-9])(?=[A-Z])', ' ')
    $fns = @($rec.functions |
        Where-Object { $_.name -notmatch '^(component\d+|copy|equals|hashCode|toString|<init>)$' } |
        Select-Object -First 3 | ForEach-Object { $_.name })
    if ($fns.Count -gt 0) { return "$camel - fns: $($fns -join ', ')" }
    return $camel
}

$records = @()
foreach ($line in (Get-Content -Path $InFile -Encoding UTF8)) {
    if ($line) { $records += ($line | ConvertFrom-Json) }
}

$rows = New-Object System.Collections.ArrayList
$seen = @{}
$kdocCount = 0; $synthCount = 0; $noFileCount = 0
foreach ($rec in $records) {
    if (-not [string]::IsNullOrWhiteSpace($rec.role)) { continue }
    if (-not $IncludeAll -and ($rec.class -notmatch $EntryRegex)) { continue }
    # One draft per path::class - the catalogue can hold several records for one file (nested
    # objects, companion, interface+impl); a single row backfills the whole group at apply time.
    $key = "$($rec.path)::$($rec.class)"
    if ($seen.ContainsKey($key)) { continue }
    $seen[$key] = $true

    $file = $null
    foreach ($sr in $srcRoots) {
        $cand = Join-Path $sr ($rec.path -replace '/', '\')
        if (Test-Path $cand) { $file = $cand; break }
    }

    $draft = ''; $source = ''
    if ($file) {
        $text = Get-Content -Path $file -Raw -Encoding UTF8
        $draft = Get-ClassKDoc $text $rec.class
        if ($draft) { $source = 'kdoc'; $kdocCount++ }
    } else {
        $noFileCount++
    }
    if (-not $draft) { $draft = Get-SynthRole $rec; $source = 'synth'; $synthCount++ }

    $draft = ($draft -replace "[`t`r`n]", ' ').Trim()
    [void]$rows.Add([pscustomobject]@{ path = $rec.path; class = $rec.class; layer = $rec.layer; source = $source; draftRole = $draft })
    if ($Limit -gt 0 -and $rows.Count -ge $Limit) { break }
}

$outPath = Join-Path $Root $OutFile
$outDir = Split-Path $outPath -Parent
if (-not (Test-Path $outDir)) { New-Item -Path $outDir -ItemType Directory -Force | Out-Null }

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("path`tclass`tlayer`tsource`tdraftRole")
foreach ($r in $rows) {
    [void]$sb.AppendLine("$($r.path)`t$($r.class)`t$($r.layer)`t$($r.source)`t$($r.draftRole)")
}
Set-Content -Path $outPath -Value $sb.ToString().TrimEnd() -Encoding UTF8

Write-Host "Drafts written: $($rows.Count) -> $OutFile" -ForegroundColor Cyan
Write-Host "  from KDoc:  $kdocCount" -ForegroundColor Green
Write-Host "  synthesised:$synthCount  (need most review)" -ForegroundColor Yellow
Write-Host "  file not found for: $noFileCount" -ForegroundColor DarkGray

exit 0
