# S1982 research probe: how many inventory records equal a single flag row, an intersection of
# two rows, or nothing the matrix produces. Read-only.
$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path
$all = @('standard','lite','photos','legacy','vr','noLegal')

$flags = @{}
$columns = @()
foreach ($line in (Get-Content -LiteralPath "$root/docs/FLAVOR_MATRIX.md" -Encoding UTF8)) {
    if ($line -notmatch '^\s*\|') { continue }
    $cells = @($line.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() })
    if ($cells.Count -lt 2) { continue }
    if ($columns.Count -eq 0) { if ($cells[0] -eq 'Flag') { $columns = @($cells[1..($cells.Count-1)]) }; continue }
    if ($cells[0] -match '^:?-{2,}') { continue }
    $name = $cells[0].Trim('`')
    if ($name -notmatch '^[A-Z][A-Z0-9_]*$') { continue }
    $on = @()
    for ($i = 0; $i -lt $columns.Count -and ($i+1) -lt $cells.Count; $i++) {
        if ($cells[$i+1] -like '*[[]+]*') { $on += $columns[$i] }
    }
    $flags[$name] = $on
}
function Key($s) { return (($s | Sort-Object -Unique) -join ',') }

$rowKeys = @{}
foreach ($k in $flags.Keys) { $rowKeys[(Key $flags[$k])] = $true }
$fullKey = Key $all

Write-Host "flags parsed: $($flags.Count)"
Write-Host "distinct flag rows (as sets): $($rowKeys.Keys.Count)"

# pairwise intersections
$pairKeys = @{}
$names = @($flags.Keys | Sort-Object)
for ($i = 0; $i -lt $names.Count; $i++) {
  for ($j = $i+1; $j -lt $names.Count; $j++) {
    $inter = @($flags[$names[$i]] | Where-Object { $flags[$names[$j]] -contains $_ })
    if ($inter.Count -eq 0) { continue }
    $k = Key $inter
    if (-not $pairKeys.ContainsKey($k)) { $pairKeys[$k] = "$($names[$i])+$($names[$j])" }
  }
}
Write-Host "distinct non-empty pairwise intersections: $($pairKeys.Keys.Count)"
$newByPairs = @($pairKeys.Keys | Where-Object { -not $rowKeys.ContainsKey($_) -and $_ -ne $fullKey })
Write-Host "  of them NOT already a flag row: $($newByPairs.Count)"
foreach ($k in ($newByPairs | Sort-Object)) { Write-Host "    [$k] <- $($pairKeys[$k])" }

# all 63 non-empty subsets of the six flavors, for reference
Write-Host ""
Write-Host "coverage of the 63 non-empty flavor subsets:"
Write-Host "  by single rows       : $(@($rowKeys.Keys).Count)"
Write-Host "  by rows+pairwise int : $(@(($rowKeys.Keys + $pairKeys.Keys) | Sort-Object -Unique).Count)"

# records
$gated = 0; $ungatedFull = 0; $ungatedRow = 0; $unexplained = @()
$lineNo = 0
foreach ($raw in (Get-Content -LiteralPath "$root/docs/ALL_FEATURES.jsonl" -Encoding UTF8)) {
    $lineNo++
    if ($raw.Trim().Length -eq 0) { continue }
    $o = $raw | ConvertFrom-Json
    if ($o.PSObject.Properties.Name -contains 'gate' -and -not [string]::IsNullOrWhiteSpace("$($o.gate)")) { $gated++; continue }
    $k = Key $o.flavors
    if ($k -eq $fullKey) { $ungatedFull++; continue }
    if ($rowKeys.ContainsKey($k)) { $ungatedRow++; continue }
    $unexplained += [pscustomobject]@{ Line = $lineNo; Id = $o.id; Key = $k; Spec = $o.spec }
}
Write-Host ""
Write-Host "records: gated=$gated ungated-full=$ungatedFull ungated-equals-a-row=$ungatedRow unexplained=$($unexplained.Count)"

$byPair = @($unexplained | Where-Object { $pairKeys.ContainsKey($_.Key) })
Write-Host "  unexplained that equal SOME pairwise intersection: $($byPair.Count)"
Write-Host ""
Write-Host "distinct unexplained sets, by frequency:"
$unexplained | Group-Object Key | Sort-Object Count -Descending | ForEach-Object {
    $mark = if ($pairKeys.ContainsKey($_.Name)) { "PAIR:$($pairKeys[$_.Name])" } else { "-" }
    Write-Host ("  {0,4} x [{1}]  {2}" -f $_.Count, $_.Name, $mark)
}
