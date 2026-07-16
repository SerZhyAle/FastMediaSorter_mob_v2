# Run-Tests.ps1 (S1063) - regression suite for scripts/spec_catalog/close-and-log.ps1.
#
# Guards the invariants that keep a malformed call from half-closing a ticket, or from closing it
# with a false record:
#   * a bad call shape is rejected BEFORE the first mutation (bind time or pre-flight),
#   * a good call still applies every step,
#   * (S1072) a capability record carries what the caller stated - never a derived guess.
#
# Regression origin: passing a multi-element PowerShell array to -DevLogs through `pwsh -File`
# left the extra elements as positional args. They silently bound to -FeatId and only surfaced
# at the all-features step - after the status flip and first dev-log had already been written,
# leaving the ticket closed but the inventory unsynced (observed closing S1062, 2026-07-15).
# The same call shape failed LOUDLY back on S0082 (2026-06-03): the failure mode degraded from
# hard error to silent mis-bind when the -Feat* params were added (2026-06-17), because each
# new optional [string] param opens another positional slot. Hence PositionalBinding = $false
# rather than a point-check on -FeatId: it cannot rot as the param block grows.
#
# What this runner touches (it is NOT hermetic - unlike scripts/guard.tests):
#   * dev/CHANGELOG.md and docs/ALL_FEATURES.jsonl are backed up and restored in a finally
#     block; the happy-path cases genuinely write to them.
#   * The subject ticket's `updated` timestamp moves, because the happy-path cases run a real
#     status write. Every case passes -Status <the ticket's CURRENT status> -StatusOnly, so no
#     lifecycle transition ever happens and the suite is idempotent across repeated runs.
#   * -SkipCatalogSync everywhere: no scan/render, nothing under dev/CATALOG is touched.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1
#         pwsh -NoProfile -File .../Run-Tests.ps1 -SubjectId S0223   # any live catalog id
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param(
    # Any id present in the catalog. Its status is read and echoed straight back, so the choice
    # is inert - it defaults to the ticket that introduced these guarantees.
    [string]$SubjectId = 'S1063'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$facade = Join-Path $repoRoot 'scripts/spec_catalog/close-and-log.ps1'
$selectPs1 = Join-Path $repoRoot 'scripts/spec_catalog/select.ps1'
$changelog = Join-Path $repoRoot 'dev/CHANGELOG.md'
$features = Join-Path $repoRoot 'docs/ALL_FEATURES.jsonl'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Get-SpecField([string]$field) {
    $json = & $pwshExe -NoProfile -File $selectPs1 -Id $SubjectId -Format json | ConvertFrom-Json
    return $json.$field
}

function Get-LineCount([string]$path) {
    if (Test-Path $path) { return @(Get-Content -LiteralPath $path).Count }
    return 0
}

# Echo the ticket's own status back at it: a write that cannot transition anything.
$subjectStatus = Get-SpecField 'status'
if (-not $subjectStatus) { Write-Host "Cannot resolve status of $SubjectId" -ForegroundColor Red; exit 1 }
Write-Host "subject: $SubjectId (status '$subjectStatus', echoed back via -StatusOnly)" -ForegroundColor DarkGray

$j1 = '{"file":"PLAN/S1063_bugfix-close-and-log-funcop-id-mapping.md","target":"s1063-tests","desc":"sandbox entry one"}'
$j2 = '{"file":"scripts/spec_catalog/close-and-log.ps1","target":"s1063-tests","desc":"sandbox entry two"}'

$bkChangelog = Join-Path $env:TEMP "close-and-log.tests.changelog.$PID.bak"
$bkFeatures = Join-Path $env:TEMP "close-and-log.tests.features.$PID.bak"
Copy-Item $changelog $bkChangelog -Force
Copy-Item $features $bkFeatures -Force

try {
    # --- A: the regression itself. Multi-element array via -File must die at bind time. ---
    Write-Host "A: multi-element -DevLogs through -File is rejected at bind time" -ForegroundColor Yellow
    $updBefore = Get-SpecField 'updated'
    $clBefore = Get-LineCount $changelog
    $outA = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs $j1 $j2 -FuncOp ADD -FuncDesc "sandbox capability" 2>&1 | Out-String
    $exitA = $LASTEXITCODE
    Assert-That "A1 non-zero exit" ($exitA -ne 0) "exit=$exitA"
    Assert-That "A2 error names the stray positional arg" ($outA -match 'positional parameter cannot be found') "out=$($outA.Trim())"
    Assert-That "A3 status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved from $updBefore"
    Assert-That "A4 no dev-log written" ((Get-LineCount $changelog) -eq $clBefore) "changelog grew"

    # --- B: the documented transport form applies every step. ---
    Write-Host "B: single JSON-array string -DevLogs + -FuncOp applies fully" -ForegroundColor Yellow
    $clBefore = Get-LineCount $changelog
    $outB = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs "[$j1,$j2]" -FuncOp ADD -FuncDesc "sandbox capability two" `
        -FeatArea "Spec Tooling" -FeatName "Sandbox capability two" -FeatFlavors "standard" 2>&1 | Out-String
    $exitB = $LASTEXITCODE
    Assert-That "B1 exit 0" ($exitB -eq 0) "exit=$exitB out=$($outB.Trim())"
    Assert-That "B2 both dev-logs written" ((Get-LineCount $changelog) -eq ($clBefore + 2)) "delta=$((Get-LineCount $changelog) - $clBefore)"
    $recB = @(Get-Content -LiteralPath $features | Where-Object { $_ -match "`"spec`":`"$SubjectId`"" })
    Assert-That "B3 capability recorded" ($recB.Count -ge 1) "records=$($recB.Count)"

    # --- C: single-element -DevLogs (the shape that always survived -File). ---
    Write-Host "C: single -DevLogs entry still works" -ForegroundColor Yellow
    $clBefore = Get-LineCount $changelog
    $outC = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs $j1 2>&1 | Out-String
    $exitC = $LASTEXITCODE
    Assert-That "C1 exit 0" ($exitC -eq 0) "exit=$exitC out=$($outC.Trim())"
    Assert-That "C2 one dev-log written" ((Get-LineCount $changelog) -eq ($clBefore + 1)) "delta=$((Get-LineCount $changelog) - $clBefore)"

    # --- D: an explicit well-formed -FeatId still overrides the derived id. ---
    Write-Host "D: valid kebab -FeatId is honoured" -ForegroundColor Yellow
    $outD = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -FuncOp FIX -FuncDesc "sandbox explicit id" -FeatId "spec-tooling.sandbox_probe" `
        -FeatArea "Spec Tooling" -FeatName "Sandbox probe" -FeatFlavors "standard" 2>&1 | Out-String
    $exitD = $LASTEXITCODE
    Assert-That "D1 exit 0" ($exitD -eq 0) "exit=$exitD out=$($outD.Trim())"
    $recD = @(Get-Content -LiteralPath $features | Where-Object { $_ -match '"id":"spec-tooling\.sandbox_probe"' })
    Assert-That "D2 explicit id used verbatim" ($recD.Count -eq 1) "records=$($recD.Count)"

    # --- E: a malformed -FeatId dies in pre-flight, not three mutations later. ---
    # The message assertion matters since S1072: several pre-flight rules now answer with exit 2, so
    # a bare code check could pass while the -FeatId rule itself is broken.
    Write-Host "E: malformed -FeatId rejected before any mutation" -ForegroundColor Yellow
    $updBefore = Get-SpecField 'updated'
    $clBefore = Get-LineCount $changelog
    $outE = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs $j1 -FuncOp ADD -FuncDesc "sandbox bad id" -FeatId $j2 2>&1 | Out-String
    $exitE = $LASTEXITCODE
    Assert-That "E1 exit 2 (bad arguments)" ($exitE -eq 2) "exit=$exitE out=$($outE.Trim())"
    Assert-That "E2 rejected for the -FeatId shape, not another rule" ($outE -match 'Invalid -FeatId') "out=$($outE.Trim())"
    Assert-That "E3 status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved from $updBefore"
    Assert-That "E4 no dev-log written" ((Get-LineCount $changelog) -eq $clBefore) "changelog grew"

    # --- F: -FuncOp and -FuncDesc must arrive together or not at all. ---
    Write-Host "F: -FuncOp without -FuncDesc rejected" -ForegroundColor Yellow
    $updBefore = Get-SpecField 'updated'
    $outF = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -FuncOp ADD 2>&1 | Out-String
    $exitF = $LASTEXITCODE
    Assert-That "F1 exit 2 (bad arguments)" ($exitF -eq 2) "exit=$exitF out=$($outF.Trim())"
    Assert-That "F2 rejected for the xor rule, not the S1072 Feat* rule" ($outF -match 'must be supplied together') "out=$($outF.Trim())"
    Assert-That "F3 status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved from $updBefore"

    # --- G: a DevLogs entry missing a field dies in pre-flight too. ---
    Write-Host "G: malformed -DevLogs entry rejected before any mutation" -ForegroundColor Yellow
    $updBefore = Get-SpecField 'updated'
    $outG = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs '{"file":"x.kt","target":"t"}' 2>&1 | Out-String
    $exitG = $LASTEXITCODE
    Assert-That "G1 exit 2 (bad arguments)" ($exitG -eq 2) "exit=$exitG out=$($outG.Trim())"
    Assert-That "G2 status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved from $updBefore"

    # --- H (S1072): recording a capability without stating its facts is rejected, one field at a
    # time. Each of the three used to be silently invented ('General' / an 80-char cut of -FuncDesc /
    # 'standard'), producing a valid-looking but false record.
    $featCases = @(
        @{ Label = '-FeatArea'; Args = @('-FeatName', 'Sandbox H', '-FeatFlavors', 'standard') },
        @{ Label = '-FeatName'; Args = @('-FeatArea', 'Spec Tooling', '-FeatFlavors', 'standard') },
        @{ Label = '-FeatFlavors'; Args = @('-FeatArea', 'Spec Tooling', '-FeatName', 'Sandbox H') }
    )
    foreach ($case in $featCases) {
        Write-Host "H: -FuncOp without $($case.Label) rejected before any mutation" -ForegroundColor Yellow
        $updBefore = Get-SpecField 'updated'
        $clBefore = Get-LineCount $changelog
        $outH = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
            -FuncOp ADD -FuncDesc "sandbox missing $($case.Label)" @($case.Args) 2>&1 | Out-String
        $exitH = $LASTEXITCODE
        Assert-That "H1 $($case.Label): exit 2" ($exitH -eq 2) "exit=$exitH out=$($outH.Trim())"
        Assert-That "H2 $($case.Label): message names the missing field" ($outH -match [regex]::Escape($case.Label)) "out=$($outH.Trim())"
        Assert-That "H3 $($case.Label): status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved"
        Assert-That "H4 $($case.Label): no dev-log written" ((Get-LineCount $changelog) -eq $clBefore) "changelog grew"
    }

    # --- I (S1072): a full call records exactly what it was told - no derivation anywhere. ---
    Write-Host "I: stated area/name/flavors land verbatim in the record" -ForegroundColor Yellow
    $longDesc = 'This description is deliberately far longer than eighty characters so that the old ' +
    'Substring(0,80) derivation would visibly slice it mid-word and land in the name field.'
    $outI = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -FuncOp ADD -FuncDesc $longDesc `
        -FeatArea "Spec Tooling" -FeatName "Stated name wins" -FeatFlavors "standard,legacy,vr" 2>&1 | Out-String
    $exitI = $LASTEXITCODE
    Assert-That "I1 exit 0" ($exitI -eq 0) "exit=$exitI out=$($outI.Trim())"
    $recI = @(Get-Content -LiteralPath $features | Where-Object { $_ -match '"id":"spec-tooling\.stated-name-wins"' })
    Assert-That "I2 id derived from area + stated name" ($recI.Count -eq 1) "records=$($recI.Count)"
    if ($recI.Count -eq 1) {
        $objI = $recI[0] | ConvertFrom-Json
        Assert-That "I3 name is verbatim, not a cut of the description" ($objI.name -eq 'Stated name wins') "name=$($objI.name)"
        Assert-That "I4 area is verbatim, not 'General'" ($objI.area -eq 'Spec Tooling') "area=$($objI.area)"
        Assert-That "I5 flavors are verbatim, not ['standard']" (($objI.flavors -join ',') -eq 'standard,legacy,vr') "flavors=$($objI.flavors -join ',')"
    }

    # --- J (S1072): the escape hatch still works - a change that ships no capability needs no facts. ---
    Write-Host "J: -SkipFuncLog still closes without any -Feat* field" -ForegroundColor Yellow
    $clBefore = Get-LineCount $changelog
    $featBefore = Get-LineCount $features
    $outJ = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -SkipFuncLog -DevLogs $j1 -FuncOp ADD -FuncDesc "sandbox skipped" 2>&1 | Out-String
    $exitJ = $LASTEXITCODE
    Assert-That "J1 exit 0" ($exitJ -eq 0) "exit=$exitJ out=$($outJ.Trim())"
    Assert-That "J2 dev-log still written" ((Get-LineCount $changelog) -eq ($clBefore + 1)) "delta=$((Get-LineCount $changelog) - $clBefore)"
    Assert-That "J3 no capability recorded" ((Get-LineCount $features) -eq $featBefore) "inventory grew"
}
finally {
    Copy-Item $bkChangelog $changelog -Force
    Copy-Item $bkFeatures $features -Force
    Remove-Item $bkChangelog, $bkFeatures -Force -ErrorAction SilentlyContinue
    Write-Host "sandbox restored (dev/CHANGELOG.md, docs/ALL_FEATURES.jsonl)" -ForegroundColor DarkGray
}

Write-Host ""
if ($script:fail -eq 0) {
    Write-Host "close-and-log tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "close-and-log tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
