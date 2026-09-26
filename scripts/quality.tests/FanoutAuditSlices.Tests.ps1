#requires -Version 7.0
<#
.SYNOPSIS
    S3556: contract suite for scripts/quality/fanout-audit-slices.ps1.

.DESCRIPTION
    The generator inserts catalog records, so it is proven against a fixture catalog and never the
    live journal: the fixture holds the repository's profile, the child template and the catalog
    forwarders, so the real harness allocates ids and writes the journal inside the fixture. What
    is demonstrated: -WhatIf writes nothing, a run creates one Tactical record, one spec and one
    research artifact per slice, the spec passes the owner-inputs gate and plan-tick.ps1 finds its
    inline phase, a second run skips everything, a foreign parent is refused, and -Only caps the run.

    The fixture lives under temp/scratch and is removed in `finally`.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test passed.
      1  at least one test failed.
      2  cannot verify - the subject script or a fixture source is missing.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:pass = 0
$script:fail = 0

function Test-Case([string]$Name, [scriptblock]$Body) {
    try {
        & $Body
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    catch {
        $script:fail++
        Write-Host "  FAIL  $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Assert-Equal($Expected, $Actual, [string]$What) {
    if ($Expected -ne $Actual) { throw "$What - expected: $Expected | actual: $Actual" }
}

function Assert-True([bool]$Condition, [string]$What) {
    if (-not $Condition) { throw "$What - expected: true | actual: false" }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$subject = Join-Path $repoRoot 'scripts/quality/fanout-audit-slices.ps1'
$sources = @(
    '.sza-profile.json',
    '.claude/templates/audit-slice-spec.md',
    'scripts/spec_catalog/_lib.ps1',
    'scripts/spec_catalog/insert.ps1',
    'scripts/spec_catalog/select.ps1',
    'scripts/spec_catalog/check-owner-inputs.ps1',
    'scripts/spec_catalog/plan-tick.ps1'
)
foreach ($rel in @('scripts/quality/fanout-audit-slices.ps1') + $sources) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $rel))) { Write-Host "cannot verify: $rel is missing."; exit 2 }
}
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixture = Join-Path $repoRoot "temp/scratch/fanout-audit-slices-tests-$PID"
$journal = Join-Path $fixture 'PLAN/spec-catalog.jsonl'
$manifestPath = Join-Path $fixture 'manifest.json'

function New-Slice([int]$Index, [string]$Name, [string]$Module, [string]$SourceSet, [string[]]$Packages, [string[]]$Siblings, [object[]]$Files) {
    $loc = 0; foreach ($f in $Files) { $loc += [int]$f.loc }
    return [ordered]@{
        index = $Index; name = $Name; title = "$Module/$SourceSet $($Packages -join ', ')"
        module = $Module; sourceSet = $SourceSet; packages = @($Packages)
        fileCount = $Files.Count; loc = $loc
        signals = [ordered]@{ listeners = 1; coroutines = 0; sharedState = 0; nonNull = 2; largeFile = 0 }
        risk = 1.5; lint = 0; detekt = 0; detektAmbiguous = $false; siblings = @($Siblings); files = @($Files)
    }
}

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    foreach ($rel in $sources) {
        $dest = Join-Path $fixture $rel
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
        Copy-Item -LiteralPath (Join-Path $repoRoot $rel) -Destination $dest
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'PLAN'), (Join-Path $fixture 'dev'), (Join-Path $fixture 'temp') | Out-Null
    Set-Content -LiteralPath $journal -Value '' -NoNewline
    Set-Content -LiteralPath (Join-Path $fixture 'PLAN/spec-catalog-archive.jsonl') -Value '' -NoNewline
    $manifest = [ordered]@{
        schema = 'audit-slices/1'; parent = 'S0001'; generatedAt = '2026-09-25T00:00:00Z'
        params = [ordered]@{ maxFiles = 40; maxLoc = 8000; includeTests = $false; includeDebug = $false; fileList = '' }
        tree = [ordered]@{ files = 4; loc = 40 }
        slices = @(
            (New-Slice 1 'audit-slice-01-app-alpha' 'app_v2' 'main' @('com/sza/fastmediasorter/alpha') @() @(
                [ordered]@{ path = 'app_v2/src/main/java/com/sza/fastmediasorter/alpha/A.kt'; loc = 10; signals = 3; lint = 0; detekt = 0 }
                [ordered]@{ path = 'app_v2/src/main/java/com/sza/fastmediasorter/alpha/B.kt'; loc = 10; signals = 0; lint = 0; detekt = 0 })),
            (New-Slice 2 'audit-slice-02-wear-beta' 'wear' 'main' @('com/sza/fastmediasorter/wear/beta') @() @(
                [ordered]@{ path = 'wear/src/main/java/com/sza/fastmediasorter/wear/beta/W.kt'; loc = 10; signals = 0; lint = 0; detekt = 0 })),
            (New-Slice 3 'audit-slice-03-app-gamma-a' 'app_v2' 'main' @('com/sza/fastmediasorter/gamma') @('audit-slice-04-app-gamma-b') @(
                [ordered]@{ path = 'app_v2/src/main/java/com/sza/fastmediasorter/gamma/G.kt'; loc = 10; signals = 0; lint = 0; detekt = 0 }))
        )
    }
    [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 8), [System.Text.UTF8Encoding]::new($false))
}

function Invoke-Fanout([string[]]$Extra = @()) {
    $output = & $pwshExe -NoProfile -NonInteractive -File $subject -Manifest $manifestPath -Parent S0001 -RepoRoot $fixture @Extra 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

function Read-Journal {
    return @(Get-Content -LiteralPath $journal | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
}

function Get-SpecFiles { return @(Get-ChildItem -LiteralPath (Join-Path $fixture 'PLAN') -Filter 'S*_audit-slice-*.md' -File | Sort-Object Name) }

try {
    Test-Case '-WhatIf prints one plan line per slice and writes nothing' {
        Reset-Fixture
        $r = Invoke-Fanout @('-WhatIf')
        Assert-Equal 0 $r.ExitCode 'exit code'
        Assert-Equal 3 ([regex]::Matches($r.Output, '(?m)^plan: audit-slice-')).Count 'plan lines'
        # Wrapped at the call site: a function returning an empty array hands the caller $null.
        Assert-Equal 0 @(Read-Journal).Count 'journal records'
        Assert-Equal 0 @(Get-SpecFiles).Count 'spec files'
    }

    Test-Case 'a run creates one Tactical record, one spec and one research artifact per slice' {
        $r = Invoke-Fanout
        Assert-Equal 0 $r.ExitCode 'exit code'
        Assert-True ($r.Output -match 'fanout: created 3, skipped 0, of 3 slices') "summary line: $($r.Output.Trim())"
        $records = @(Read-Journal)
        Assert-Equal 3 $records.Count 'journal records'
        foreach ($rec in $records) {
            Assert-Equal 'Tactical' ([string]$rec.status) "status of $($rec.id)"
            Assert-True ([string]$rec.name -like 'audit-slice-*') "name of $($rec.id)"
        }
        Assert-Equal 3 @(Get-SpecFiles).Count 'spec files'
        $artifacts = @(Get-ChildItem -LiteralPath (Join-Path $fixture 'PLAN') -Recurse -Filter '01__slice-files.md' -File)
        Assert-Equal 3 $artifacts.Count 'research artifacts'
        $artifactText = [IO.File]::ReadAllText($artifacts[0].FullName)
        Assert-True ($artifactText -match '\| Path \| LOC \| Signals \| Lint \| Detekt \|') 'artifact table header'
    }

    Test-Case 'every written spec carries the gate heading, the parent link, the inline phase and no placeholder' {
        foreach ($f in (Get-SpecFiles)) {
            $text = [IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
            Assert-Equal 1 ([regex]::Matches($text, '(?m)^### 3\.3 Owner inputs \(Approval gate\)$')).Count "3.3 heading in $($f.Name)"
            Assert-True ($text.Contains('**Related tickets:** S0001')) "related tickets in $($f.Name)"
            Assert-True ($text.Contains('# Phase 01 - ')) "phase 01 in $($f.Name)"
            Assert-Equal 0 ([regex]::Matches($text, '\{\{')).Count "placeholders left in $($f.Name)"
            Assert-True ($text.Contains('**Status:** Tactical')) "status header in $($f.Name)"
        }
        $wear = @(Get-SpecFiles | Where-Object { $_.Name -like '*wear-beta*' })
        Assert-Equal 1 $wear.Count 'wear spec'
        $wearText = [IO.File]::ReadAllText($wear[0].FullName, [System.Text.Encoding]::UTF8)
        Assert-True ($wearText.Contains('.\a.ps1 fw')) 'wear spec names the wear compile check'
        Assert-True ($wearText.Contains('.\a.ps1 fwu')) 'wear spec names the wear unit suite'
        $gamma = @(Get-SpecFiles | Where-Object { $_.Name -like '*gamma-a*' })
        $gammaText = [IO.File]::ReadAllText($gamma[0].FullName, [System.Text.Encoding]::UTF8)
        Assert-True ($gammaText.Contains('- `audit-slice-04-app-gamma-b`')) 'sibling listed as a bullet'
    }

    Test-Case 'the owner-inputs gate passes on a generated spec' {
        $first = (Read-Journal)[0]
        $out = & $pwshExe -NoProfile -NonInteractive -File (Join-Path $fixture 'scripts/spec_catalog/check-owner-inputs.ps1') -Id $first.id 2>&1 | Out-String
        Assert-Equal 0 ([int]$LASTEXITCODE) "check-owner-inputs exit: $($out.Trim())"
    }

    Test-Case 'plan-tick.ps1 finds the inline phase and ticks step 01.1' {
        $first = (Read-Journal)[0]
        $out = & $pwshExe -NoProfile -NonInteractive -File (Join-Path $fixture 'scripts/spec_catalog/plan-tick.ps1') -Id $first.id -Phase 01 -Steps 1 -State Done -Log 'contract suite' 2>&1 | Out-String
        Assert-Equal 0 ([int]$LASTEXITCODE) "plan-tick exit: $($out.Trim())"
        $spec = @(Get-SpecFiles | Where-Object { $_.Name -like "$($first.id)_*" })[0]
        $text = [IO.File]::ReadAllText($spec.FullName, [System.Text.Encoding]::UTF8)
        $step = [regex]::Match($text, '(?s)### Step 01\.1.*?\*\*Status:\*\* (`\[.\]`)')
        Assert-Equal '`[x]`' $step.Groups[1].Value 'step 01.1 marker'
    }

    Test-Case 'a second run skips every slice and creates nothing' {
        $r = Invoke-Fanout
        Assert-Equal 0 $r.ExitCode 'exit code'
        Assert-Equal 3 ([regex]::Matches($r.Output, '(?m)^skip: S\d{4} audit-slice-')).Count 'skip lines'
        Assert-True ($r.Output -match 'fanout: created 0, skipped 3, of 3 slices') "summary line: $($r.Output.Trim())"
        Assert-Equal 3 @(Read-Journal).Count 'journal records unchanged'
    }

    Test-Case 'a parent that does not match the manifest is a cannot-verify' {
        $output = & $pwshExe -NoProfile -NonInteractive -File $subject -Manifest $manifestPath -Parent S0002 -RepoRoot $fixture 2>&1 | Out-String
        Assert-Equal 2 ([int]$LASTEXITCODE) "exit code: $($output.Trim())"
        Assert-True ($output -match 'does not match') 'refusal names the mismatch'
    }

    Test-Case '-Only 1 on a fresh fixture creates exactly one record' {
        Reset-Fixture
        $r = Invoke-Fanout @('-Only', '1')
        Assert-Equal 0 $r.ExitCode 'exit code'
        Assert-Equal 1 @(Read-Journal).Count 'journal records'
        Assert-True ($r.Output -match 'of 1 slices \(manifest 3\)') "summary names the cap: $($r.Output.Trim())"
    }

    Test-Case 'a record whose files are missing is repaired under its own id instead of skipped' {
        $only = @(Read-Journal)[0]
        $specPath = Join-Path $fixture "PLAN/$($only.id)_$($only.name).md"
        Remove-Item -LiteralPath $specPath -Force
        Remove-Item -LiteralPath (Join-Path $fixture "PLAN/$($only.id)_$($only.name)") -Recurse -Force
        $r = Invoke-Fanout
        Assert-Equal 0 $r.ExitCode 'exit code'
        Assert-Equal 1 ([regex]::Matches($r.Output, "(?m)^repaired: $($only.id) $($only.name)")).Count 'repaired line'
        Assert-True ($r.Output -match 'fanout: created 2, skipped 0, of 3 slices') "summary line: $($r.Output.Trim())"
        Assert-True (Test-Path -LiteralPath $specPath -PathType Leaf) 'spec restored'
        Assert-True (Test-Path -LiteralPath (Join-Path $fixture "PLAN/$($only.id)_$($only.name)/research/01__slice-files.md") -PathType Leaf) 'artifact restored'
        Assert-Equal 3 @(Read-Journal).Count 'journal records - no duplicate for the repaired name'
        $text = [IO.File]::ReadAllText($specPath, [System.Text.Encoding]::UTF8)
        Assert-True ($text.Contains("**Ticket:** $($only.id)")) 'restored spec carries the existing id'
    }
}
finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("FanoutAuditSlices.Tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
