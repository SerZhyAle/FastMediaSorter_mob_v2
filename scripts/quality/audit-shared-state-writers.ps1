#requires -Version 7.0
<#
.SYNOPSIS
    Harvest writers of shared state across the app modules and rank multi-writer
    candidates - stage 1 of the S0703 shared-state mutation audit.

.DESCRIPTION
    Mechanical, regex-level pre-filter. It SURFACES candidates, it does not adjudicate.
    Two surfaces are scanned:
      - UI: writers of observable view properties (visibility / isVisible / isEnabled /
        alpha / isClickable / isFocusable / text), both direct (binding.<id>.<prop> =),
        apply-block (binding.<id>.apply { .. }), and generic/indirect (a write through a
        local/loop/it receiver) - the generic case is the one that bit S0703's seed bug
        and is invisible to id-only matching.
      - data: writers of shared state carriers (reactive StateFlow/LiveData, Room write
        ops, DataStore/preferences edits, mutable caches).
    The authoritative second stage is the agent prompt
    scripts/quality/shared-state-audit-prompt.md, which reasons about indirect writers
    and concurrency. This script never edits source.

.PARAMETER Surface
    Which surface to harvest: ui | data | all (default all).

.PARAMETER Top
    How many top-ranked findings to print (default 20).

.PARAMETER MinWriters
    Minimum distinct writer files for a finding to be reported (default 2).

.PARAMETER Json
    Optional path for the full machine-readable result. Defaults under temp/.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/audit-shared-state-writers.ps1 -Surface all -Top 20
#>
[CmdletBinding()]
param(
    [ValidateSet('ui', 'data', 'all')]
    [string]$Surface = 'all',
    [int]$Top = 20,
    [int]$MinWriters = 2,
    [string]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scanRoots = @(
    (Join-Path $repoRoot 'app_v2/src'),
    (Join-Path $repoRoot 'wear/src')
) | Where-Object { Test-Path $_ }

$uiProps = 'visibility|isVisible|isEnabled|alpha|isClickable|isFocusable|text'
$rxDirectUi = [regex]"binding\.(\w+)\??\.($uiProps)\s*=(?!=)"
$rxApplyUi = [regex]'binding\.(\w+)\??\.(?:apply|also|run)\s*\{'
$rxWithUi = [regex]'with\s*\(\s*binding\.(\w+)\??\s*\)'
# Generic/indirect view-property write through a non-binding receiver (the dangerous class).
$rxGenericUi = [regex]'(?<![.\w])(\w+)\.(visibility|isVisible|isEnabled|alpha)\s*=(?!=)'
$rxBindingType = [regex]'\b(\w+Binding)\b'

# Data surface: reactive state holders, Room write ops, preferences/DataStore, mutable caches.
$rxReactive = [regex]'(?<![.\w])(\w+)\.(value\s*=(?!=)|postValue\s*\(|setValue\s*\(|emit\s*\(|tryEmit\s*\(|update\s*\{)'
$rxRoomAnno = [regex]'@Insert\b|@Update\b|@Delete\b'
$rxRoomQuery = [regex]'@Query\s*\(\s*"\s*(?:UPDATE|DELETE\s+FROM|INSERT\s+INTO)\s+`?(\w+)'
$rxPrefEdit = [regex]'(?<![.\w])(\w+)\.edit\s*\{'
$rxCachePut = [regex]'(?i)(?<![.\w])(\w*(?:cache|pool|registry)\w*)\.put\s*\('
# Nearest enclosing coroutine/thread context, used to flag cross-scope writes.
$rxContext = [regex]'Dispatchers\.(?:IO|Default|Main|Unconfined)|withContext\s*\(|\bviewModelScope\b|\blifecycleScope\b|\bGlobalScope\b|\brunBlocking\b'

# Writer record shape: PSCustomObject { Surface, Carrier, Domain, File, Prop, Kind, Context }
$writers = [System.Collections.Generic.List[object]]::new()

function Get-RelPath {
    param([string]$Full)
    ($Full.Substring($repoRoot.Length).TrimStart('\', '/')) -replace '\\', '/'
}

function Get-DominantBinding {
    param([string]$Text, [string]$Fallback)
    $counts = @{}
    foreach ($m in $rxBindingType.Matches($Text)) {
        $name = $m.Groups[1].Value
        if ($counts.ContainsKey($name)) { $counts[$name]++ } else { $counts[$name] = 1 }
    }
    if ($counts.Count -eq 0) { return $Fallback }
    ($counts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 1).Key
}

$files = foreach ($root in $scanRoots) {
    Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue
}

foreach ($file in $files) {
    $text = [System.IO.File]::ReadAllText($file.FullName)
    $rel = Get-RelPath $file.FullName
    $leaf = $file.BaseName

    if ($Surface -eq 'ui' -or $Surface -eq 'all') {
        $domain = Get-DominantBinding -Text $text -Fallback $leaf
        foreach ($m in $rxDirectUi.Matches($text)) {
            $writers.Add([pscustomobject]@{
                    Surface = 'ui'; Carrier = $m.Groups[1].Value; Domain = $domain
                    File = $rel; Prop = $m.Groups[2].Value; Kind = 'direct'; Context = ''
                })
        }
        foreach ($m in $rxApplyUi.Matches($text)) {
            $writers.Add([pscustomobject]@{
                    Surface = 'ui'; Carrier = $m.Groups[1].Value; Domain = $domain
                    File = $rel; Prop = '(apply)'; Kind = 'apply'; Context = ''
                })
        }
        foreach ($m in $rxWithUi.Matches($text)) {
            $writers.Add([pscustomobject]@{
                    Surface = 'ui'; Carrier = $m.Groups[1].Value; Domain = $domain
                    File = $rel; Prop = '(with)'; Kind = 'apply'; Context = ''
                })
        }
        foreach ($m in $rxGenericUi.Matches($text)) {
            if ($m.Groups[1].Value -eq 'binding') { continue }
            $writers.Add([pscustomobject]@{
                    Surface = 'ui'; Carrier = '(generic)'; Domain = $domain
                    File = $rel; Prop = $m.Groups[2].Value; Kind = 'generic'; Context = ''
                })
        }
    }

    if ($Surface -eq 'data' -or $Surface -eq 'all') {
        $ctxMatches = $rxContext.Matches($text)
        $dataHits = [System.Collections.Generic.List[object]]::new()
        foreach ($m in $rxReactive.Matches($text)) {
            $op = ($m.Groups[2].Value -replace '[\s({=].*$', '')
            $dataHits.Add([pscustomobject]@{ Carrier = $m.Groups[1].Value; Op = $op; Index = $m.Index })
        }
        foreach ($m in $rxRoomQuery.Matches($text)) {
            $dataHits.Add([pscustomobject]@{ Carrier = ('table:' + $m.Groups[1].Value); Op = 'sql-write'; Index = $m.Index })
        }
        foreach ($m in $rxRoomAnno.Matches($text)) {
            $dataHits.Add([pscustomobject]@{ Carrier = ('room:' + $leaf); Op = $m.Value; Index = $m.Index })
        }
        foreach ($m in $rxPrefEdit.Matches($text)) {
            $dataHits.Add([pscustomobject]@{ Carrier = $m.Groups[1].Value; Op = 'edit'; Index = $m.Index })
        }
        foreach ($m in $rxCachePut.Matches($text)) {
            $dataHits.Add([pscustomobject]@{ Carrier = $m.Groups[1].Value; Op = 'put'; Index = $m.Index })
        }
        foreach ($h in $dataHits) {
            $ctx = ''
            foreach ($c in $ctxMatches) {
                if ($c.Index -lt $h.Index) { $ctx = ($c.Value -replace '\s*\($', '' -replace '\s+', '') } else { break }
            }
            $writers.Add([pscustomobject]@{
                    Surface = 'data'; Carrier = $h.Carrier; Domain = $leaf
                    File = $rel; Prop = $h.Op; Kind = 'data'; Context = $ctx
                })
        }
    }
}

# --- Aggregate writers into ranked findings ---------------------------------
# Domains that contain a generic/indirect view-property writer (a loop/local-receiver
# writer coexisting with point writers is the S0703 authority-conflict smell).
$genericDomains = [System.Collections.Generic.HashSet[string]]::new()
foreach ($w in $writers) { if ($w.Kind -eq 'generic') { [void]$genericDomains.Add($w.Domain) } }

$patternWeight = @{ 'generic-loop-writer' = 3; 'cross-scope-write' = 3; 'no-single-owner' = 1 }
$findings = [System.Collections.Generic.List[object]]::new()

# UI findings: group point writers (direct + apply) by ownership domain + view id.
$uiPoint = @($writers | Where-Object { $_.Surface -eq 'ui' -and ($_.Kind -eq 'direct' -or $_.Kind -eq 'apply') })
foreach ($g in ($uiPoint | Group-Object { "$($_.Domain)|$($_.Carrier)" })) {
    $fileSet = @($g.Group | Select-Object -ExpandProperty File -Unique)
    if ($fileSet.Count -lt $MinWriters) { continue }
    $domain = $g.Group[0].Domain
    $isGeneric = $genericDomains.Contains($domain)
    $flags = if ($isGeneric) { @('no-single-owner', 'generic-loop-writer') } else { @('no-single-owner') }
    $weight = ($flags | ForEach-Object { $patternWeight[$_] } | Measure-Object -Maximum).Maximum
    $props = @($g.Group | Select-Object -ExpandProperty Prop -Unique)
    $findings.Add([pscustomobject]@{
            Rank = $fileSet.Count * $weight; Surface = 'ui'; Carrier = $g.Group[0].Carrier
            Domain = $domain; WriterCount = $fileSet.Count; Flags = $flags
            Detail = ($props -join ','); Files = $fileSet
        })
}

# Data findings: group carriers by name; flag writes spanning >=2 coroutine/thread contexts.
$dataAll = @($writers | Where-Object { $_.Surface -eq 'data' })
foreach ($g in ($dataAll | Group-Object Carrier)) {
    $fileSet = @($g.Group | Select-Object -ExpandProperty File -Unique)
    if ($fileSet.Count -lt $MinWriters) { continue }
    $ctxSet = @($g.Group | Where-Object { $_.Context -ne '' } | Select-Object -ExpandProperty Context -Unique)
    $crossScope = $ctxSet.Count -ge 2
    $flags = if ($crossScope) { @('no-single-owner', 'cross-scope-write') } else { @('no-single-owner') }
    $weight = ($flags | ForEach-Object { $patternWeight[$_] } | Measure-Object -Maximum).Maximum
    $findings.Add([pscustomobject]@{
            Rank = $fileSet.Count * $weight; Surface = 'data'; Carrier = $g.Name
            Domain = ''; WriterCount = $fileSet.Count; Flags = $flags
            Detail = ($ctxSet -join ','); Files = $fileSet
        })
}

$ranked = @($findings | Sort-Object -Property @{Expression = 'Rank'; Descending = $true }, @{Expression = 'WriterCount'; Descending = $true })

# --- Report -----------------------------------------------------------------
Write-Host ("Shared-state writer audit (surface={0}, top={1}, min-writers={2})" -f $Surface, $Top, $MinWriters)
Write-Host ("Scanned {0} Kotlin files across {1} root(s); {2} multi-writer finding(s)." -f $files.Count, $scanRoots.Count, $ranked.Count)
Write-Host ("{0,4}  {1,-4}  {2,7}  {3,-28}  {4}" -f 'RANK', 'SURF', 'WRITERS', 'CARRIER', 'FLAGS')
foreach ($f in (@($ranked | Select-Object -First $Top))) {
    Write-Host ("{0,4}  {1,-4}  {2,7}  {3,-28}  {4}" -f $f.Rank, $f.Surface, $f.WriterCount, $f.Carrier, ($f.Flags -join '+'))
    $domainPart = if ($f.Domain) { "domain=$($f.Domain)  " } else { '' }
    Write-Host ("        {0}{1}: {2}" -f $domainPart, $f.Detail, ($f.Files -join ', '))
}

if (-not $Json) { $Json = Join-Path $repoRoot 'temp/shared-state-audit.json' }
$jsonDir = Split-Path -Parent $Json
if ($jsonDir -and -not (Test-Path $jsonDir)) { New-Item -ItemType Directory -Path $jsonDir -Force | Out-Null }
$ranked | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Json -Encoding UTF8
Write-Host ("Full result: {0}" -f ((Get-RelPath (Resolve-Path -LiteralPath $Json).Path)))
