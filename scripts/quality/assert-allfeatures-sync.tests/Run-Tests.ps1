#requires -Version 7.0
<#
.SYNOPSIS
    S2937: contract suite for the ungated single-flag rule (check 7) of assert-allfeatures-sync.ps1.
# Subject: scripts/quality/lib/allfeatures-ungated-reach.ps1, scripts/quality/lib/flavor-matrix.ps1

.DESCRIPTION
    Two layers, neither reading the live ledger or the live flavor matrix:
      - the pure functions of lib/allfeatures-ungated-reach.ps1 against an in-memory matrix;
      - the real script, copied into a throwaway tree that holds a four-flavor matrix, a one-line
        ledger and stub collaborators, so the exit code and the printed finding are the ones a
        hooks.postClose step would see.
    The decisive cases are the ones the rule exists for: an ungated record on a row only one flag
    owns is refused, the same record with a gate is not, a set that several flags share is check 8's
    and is refused against its own baseline (S4633), and a baselined id passes while a new one does not.

    Exit codes (CLAUDE.md Rule 7):
      0  every case passed
      1  at least one case failed
      2  cannot verify - the subject script or its library is missing
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$subjectScript = Join-Path $repoRoot 'scripts/quality/assert-allfeatures-sync.ps1'
$libDir = Join-Path $repoRoot 'scripts/quality/lib'
foreach ($needed in @($subjectScript, (Join-Path $libDir 'allfeatures-ungated-reach.ps1'), (Join-Path $libDir 'flavor-matrix.ps1'), (Join-Path $libDir 'baseline-set-writer.ps1'))) {
    if (-not (Test-Path -LiteralPath $needed)) { Write-Host "cannot verify: $needed is missing."; exit 2 }
}
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

. (Join-Path $libDir 'flavor-matrix.ps1')
. (Join-Path $libDir 'allfeatures-ungated-reach.ps1')

$script:passed = 0
$script:failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

function Assert-Case {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        $script:passed++
        Write-Host "  PASS  $Name"
    }
    else {
        $script:failed++
        Write-Host "  FAIL  $Name $Detail" -ForegroundColor Red
    }
}

# ---- layer 1: the pure functions -------------------------------------------------------------

$flavors = @('A', 'B', 'C', 'D')
$flagRows = [ordered]@{
    ONE_ROW    = @('A', 'B')
    TWIN_X     = @('A', 'C')
    TWIN_Y     = @('C', 'A')
    EVERYWHERE = @('A', 'B', 'C', 'D')
    NOWHERE    = @()
}
$fullSig = Get-FlavorSetSignature -Flavors $flavors

function New-Record {
    param([string]$Id, [string[]]$Flavors, [string]$Gate = $null, [switch]$NoFlavors)
    $o = [ordered]@{ id = $Id; area = 'Browse' }
    if (-not $NoFlavors) { $o.flavors = $Flavors }
    if ($null -ne $Gate) { $o.gate = $Gate }
    return [pscustomobject]$o
}

function Find-Offenders {
    param([object[]]$Records)
    # The comma keeps an empty result an empty array: a bare return unrolls it to $null, and .Count on
    # $null is a terminating error under StrictMode.
    return , @(Get-UngatedSingleFlagRecord -Records $Records -FlagRows $flagRows -FullSignature $fullSig)
}

$map = Get-SingleFlagRowMap -FlagRows $flagRows -FullSignature $fullSig
Assert-Case 'row map keeps the row only one flag owns' ($map.ContainsKey('A,B') -and $map['A,B'] -eq 'ONE_ROW')
Assert-Case 'row map drops a row two flags share' (-not $map.ContainsKey('A,C'))
Assert-Case 'row map drops the full dimension and the empty row' (-not $map.ContainsKey($fullSig) -and -not $map.ContainsKey('') -and $map.Count -eq 1)

$hit = Find-Offenders @(New-Record 'r.hit' @('A', 'B'))
Assert-Case 'ungated record on a unique row is an offender, with the flag named' ($hit.Count -eq 1 -and $hit[0].Id -eq 'r.hit' -and $hit[0].Flag -eq 'ONE_ROW') "got $($hit.Count)"
Assert-Case 'flavor order does not matter' ((Find-Offenders @(New-Record 'r.order' @('B', 'A'))).Count -eq 1)
Assert-Case 'a gated record with the same set is left to validate.ps1' ((Find-Offenders @(New-Record 'r.gated' @('A', 'B') 'ONE_ROW')).Count -eq 0)
Assert-Case 'a conjunction gate counts as gated' ((Find-Offenders @(New-Record 'r.conj' @('A', 'B') 'ONE_ROW+TWIN_X')).Count -eq 0)
Assert-Case 'an empty gate string is still ungated' ((Find-Offenders @(New-Record 'r.blank' @('A', 'B') '')).Count -eq 1)
Assert-Case 'a whitespace-only gate is still ungated' ((Find-Offenders @(New-Record 'r.space' @('A', 'B') '   ')).Count -eq 1)
Assert-Case 'a set that two flags share is not demanded a gate' ((Find-Offenders @(New-Record 'r.twin' @('A', 'C'))).Count -eq 0)
Assert-Case 'the full dimension is S1934 own claim and stays legal' ((Find-Offenders @(New-Record 'r.full' $flavors)).Count -eq 0)
Assert-Case 'a set that matches no row is outside this rule' ((Find-Offenders @(New-Record 'r.none' @('D'))).Count -eq 0)
Assert-Case 'a record with no flavors field is skipped' ((Find-Offenders @(New-Record 'r.noflav' @() -NoFlavors)).Count -eq 0)
Assert-Case 'an empty ledger yields no offenders' ((Find-Offenders @()).Count -eq 0)

$mixed = Find-Offenders @(
    (New-Record 'm.1' @('A', 'B')), (New-Record 'm.2' @('A', 'B') 'ONE_ROW'), (New-Record 'm.3' @('A', 'B')), (New-Record 'm.4' @('D')))
Assert-Case 'offenders come back in ledger order, one object each' ($mixed.Count -eq 2 -and $mixed[0].Id -eq 'm.1' -and $mixed[1].Id -eq 'm.3')

function Find-SharedOffenders {
    param([object[]]$Records)
    return , @(Get-UngatedSharedRowRecord -Records $Records -FlagRows $flagRows -FullSignature $fullSig)
}

$sharedMap = Get-SharedFlagRowMap -FlagRows $flagRows -FullSignature $fullSig
Assert-Case 'S4633: shared map keeps the row two flags own, flags sorted' ($sharedMap.Count -eq 1 -and $sharedMap.ContainsKey('A,C') -and ($sharedMap['A,C'] -join '|') -eq 'TWIN_X|TWIN_Y')
$twinHit = Find-SharedOffenders @(New-Record 'r.twin' @('C', 'A'))
Assert-Case 'S4633: ungated record on a shared row is an offender, with every flag named' ($twinHit.Count -eq 1 -and $twinHit[0].Id -eq 'r.twin' -and ($twinHit[0].Flags -join '|') -eq 'TWIN_X|TWIN_Y') "got $($twinHit.Count)"
Assert-Case 'S4633: a shared-row record gated with either twin is left to validate.ps1' ((Find-SharedOffenders @((New-Record 'r.tx' @('A', 'C') 'TWIN_X'), (New-Record 'r.ty' @('A', 'C') 'TWIN_Y'))).Count -eq 0)
Assert-Case 'S4633: a unique-row record is check 7, not check 8' ((Find-SharedOffenders @(New-Record 'r.hit' @('A', 'B'))).Count -eq 0)
Assert-Case 'S4633: the full dimension and an unmatched set stay outside check 8' ((Find-SharedOffenders @((New-Record 'r.full' $flavors), (New-Record 'r.none' @('D')))).Count -eq 0)

$baselineProbe = Join-Path ([System.IO.Path]::GetTempPath()) ("s2937-baseline-" + [guid]::NewGuid().ToString('N') + '.txt')
$fixtures.Add($baselineProbe)
[System.IO.File]::WriteAllText($baselineProbe, "# header`n`nid.one`n  id.two  `n# trailing`n")
$read = @(Read-UngatedReachBaseline -Path $baselineProbe)
Assert-Case 'baseline reader skips comments and blanks and trims' ($read.Count -eq 2 -and $read[0] -eq 'id.one' -and $read[1] -eq 'id.two') "got [$($read -join '|')]"

# ---- layer 2: the real script in a throwaway tree --------------------------------------------

function Write-Utf8 {
    param([string]$Root, [string]$Relative, [string]$Text)
    $path = Join-Path $Root $Relative
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force
    [System.IO.File]::WriteAllText($path, $Text, [System.Text.UTF8Encoding]::new($false))
}

$matrixText = @(
    '| Flag | A | B | C | D |'
    '|:-----|:----:|:----:|:----:|:----:|'
    '| `ONE_ROW` | [+] | [+] | [-] | [-] |'
    '| `TWIN_X` | [+] | [-] | [+] | [-] |'
    '| `TWIN_Y` | [+] | [-] | [+] | [-] |'
    '| `EVERYWHERE` | [+] | [+] | [+] | [+] |'
) -join "`n"

function New-Tree {
    param([string[]]$Ledger, [string[]]$Baseline = @('# header'), [string[]]$SharedBaseline = @('# header'), [switch]$WithoutBaseline, [switch]$WithoutSharedBaseline)
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("s2937-sync-" + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    foreach ($rel in 'scripts/quality/assert-allfeatures-sync.ps1', 'scripts/quality/lib/allfeatures-ungated-reach.ps1', 'scripts/quality/lib/flavor-matrix.ps1', 'scripts/quality/lib/baseline-set-writer.ps1') {
        $dest = Join-Path $root $rel
        $null = New-Item -ItemType Directory -Path (Split-Path -Parent $dest) -Force
        Copy-Item -LiteralPath (Join-Path $repoRoot $rel) -Destination $dest
    }
    # Collaborators the rule does not exercise are stubs: the lock library is only called by
    # -UpdateBaseline, and validate.ps1 is the canon schema judge, not this repository's.
    Write-Utf8 $root 'scripts/utils/code-lock-scope.ps1' '# stub'
    Write-Utf8 $root 'scripts/all_features/validate.ps1' 'exit 0'
    Write-Utf8 $root 'docs/FLAVOR_MATRIX.md' $matrixText
    Write-Utf8 $root 'docs/ALL_FEATURES.schema.json' '{"properties":{"area":{"enum":["Browse"]}}}'
    Write-Utf8 $root 'docs/ALL_FEATURES.jsonl' (($Ledger -join "`n") + "`n")
    Write-Utf8 $root 'scripts/quality/allfeatures-sync-baseline.txt' '0'
    Write-Utf8 $root 'scripts/quality/allfeatures-gate-baseline.txt' '# none'
    Write-Utf8 $root 'scripts/quality/allfeatures-subject-reach.json' '{"subjects":[]}'
    Write-Utf8 $root 'scripts/quality/allfeatures-wear-boundary-map.json' '{"wearFlavors":["noLegal"],"noLegalOnly":[]}'
    Write-Utf8 $root 'wear/config/store-boundary-policy.json' '{"noLegalOnly":[{"category":"c"}]}'
    if (-not $WithoutBaseline) {
        Write-Utf8 $root 'scripts/quality/allfeatures-ungated-single-flag-baseline.txt' (($Baseline -join "`n") + "`n")
    }
    if (-not $WithoutSharedBaseline) {
        Write-Utf8 $root 'scripts/quality/allfeatures-ungated-shared-row-baseline.txt' (($SharedBaseline -join "`n") + "`n")
    }
    return $root
}

function Invoke-Script {
    param([string]$Name, [string]$Root, [int]$ExpectedExit, [string]$ExpectedText = '', [string]$ForbiddenText = '')
    $script = Join-Path $Root 'scripts/quality/assert-allfeatures-sync.ps1'
    $out = & $pwshExe -NoProfile -File $script -Gate 2>&1 | Out-String
    $code = $LASTEXITCODE
    $textOk = (-not $ExpectedText) -or $out.Contains($ExpectedText)
    $forbiddenOk = (-not $ForbiddenText) -or (-not $out.Contains($ForbiddenText))
    Assert-Case $Name (($code -eq $ExpectedExit) -and $textOk -and $forbiddenOk) "(expected exit $ExpectedExit, got $code; text found: $textOk; forbidden absent: $forbiddenOk)`n$($out.Trim())"
}

$ungatedOnRow = '{"id":"x.ungated","area":"Browse","name":"n","description":"d","flavors":["A","B"],"spec":"S1","status":"active"}'
$gatedOnRow = '{"id":"x.ungated","area":"Browse","name":"n","description":"d","flavors":["A","B"],"gate":"ONE_ROW","spec":"S1","status":"active"}'
$twinRecord = '{"id":"x.twin","area":"Browse","name":"n","description":"d","flavors":["A","C"],"spec":"S1","status":"active"}'
$twinGated = '{"id":"x.twin","area":"Browse","name":"n","description":"d","flavors":["A","C"],"gate":"TWIN_Y","spec":"S1","status":"active"}'
$fullRecord ='{"id":"x.full","area":"Browse","name":"n","description":"d","flavors":["A","B","C","D"],"spec":"S1","status":"active"}'

try {
    Invoke-Script 'script: clean ledger passes' (New-Tree -Ledger @($fullRecord)) 0 'assert-allfeatures-sync: PASS'
    Invoke-Script 'script: a full-dimension record needs no gate' (New-Tree -Ledger @($fullRecord)) 0 'assert-allfeatures-sync: PASS'
    Invoke-Script 'S4633 script: a new ungated record on a shared row fails and names every flag' (New-Tree -Ledger @($twinRecord)) 1 'x.twin [A,C] - the row of TWIN_X, TWIN_Y'
    Invoke-Script 'S4633 script: the same record gated with one twin passes' (New-Tree -Ledger @($twinGated)) 0 'assert-allfeatures-sync: PASS'
    Invoke-Script 'S4633 script: a shared-row id in its own baseline is excused' (New-Tree -Ledger @($twinRecord) -SharedBaseline @('# header', 'x.twin')) 0 'assert-allfeatures-sync: PASS'
    Invoke-Script 'S4633 script: the single-flag baseline does not excuse a shared-row record' (New-Tree -Ledger @($twinRecord) -Baseline @('# header', 'x.twin')) 1 'ungated shared-row reach'
    Invoke-Script 'S4633 script: a stale shared-row baseline row is advisory' (New-Tree -Ledger @($twinGated) -SharedBaseline @('# header', 'x.twin')) 0 'shared-row baseline holds 1'
    Invoke-Script 'S4633 script: a missing shared-row baseline is could-not-verify' (New-Tree -Ledger @($fullRecord) -WithoutSharedBaseline) 2 'shared-row-baseline.txt not found'
    Invoke-Script 'script: a new ungated record on a unique row fails and names the flag' (New-Tree -Ledger @($ungatedOnRow)) 1 'x.ungated [A,B] - the row of ONE_ROW'
    Invoke-Script 'script: the failure names the closure parameter that fixes it' (New-Tree -Ledger @($ungatedOnRow)) 1 '-FeatGate'
    Invoke-Script 'script: the same record with its gate passes' (New-Tree -Ledger @($gatedOnRow)) 0 'assert-allfeatures-sync: PASS'
    Invoke-Script 'script: a baselined id is excused' (New-Tree -Ledger @($ungatedOnRow) -Baseline @('# header', 'x.ungated')) 0 'assert-allfeatures-sync: PASS'
    Invoke-Script 'script: a baselined id does not excuse a different record' (New-Tree -Ledger @($ungatedOnRow) -Baseline @('# header', 'x.other')) 1 'x.ungated'
    Invoke-Script 'script: a stale baseline row is advisory, not a failure' (New-Tree -Ledger @($gatedOnRow) -Baseline @('# header', 'x.ungated')) 0 'no longer offend'
    Invoke-Script 'script: a missing baseline file is could-not-verify, never a pass' (New-Tree -Ledger @($fullRecord) -WithoutBaseline) 2 'COULD NOT VERIFY'
}
finally {
    foreach ($f in $fixtures) { if (Test-Path -LiteralPath $f) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue } }
}

if ($script:failed -gt 0) {
    Write-Host ("assert-allfeatures-sync.tests: FAIL (run={0} skipped=0, failed={1})" -f ($script:passed + $script:failed), $script:failed)
    exit 1
}
Write-Host ("assert-allfeatures-sync.tests: PASS (run={0} skipped=0)" -f $script:passed)
exit 0
