#requires -Version 7.0
<#
.SYNOPSIS
    S3556: contract suite for scripts/quality/partition-audit-slices.ps1.

.DESCRIPTION
    The partition decides which files each audit ticket reads, so its invariants are demonstrated
    on a fixture tree rather than trusted from the live run: every file lands in exactly one slice,
    the caps hold, a package above the file cap is cut into balanced parts, small sibling packages
    fuse, test and debug sets stay out unless asked, two runs agree byte for byte, the baseline
    share reaches the right slice, risk orders the slices, a missing module root is a cannot-verify
    and -FileList narrows the input to the listed files.

    The fixture is a minimal repository under the system temp directory - profile, two module roots,
    one lint and one detekt baseline - and is removed in `finally`.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test passed.
      1  at least one test failed.
      2  cannot verify - the subject script is missing.
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
$subject = Join-Path $repoRoot 'scripts/quality/partition-audit-slices.ps1'
if (-not (Test-Path -LiteralPath $subject -PathType Leaf)) { Write-Host "cannot verify: $subject is missing."; exit 2 }
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ("partition-audit-slices-fixture-{0}" -f $PID)
$appMain = 'app_v2/src/main/java/com/sza/fastmediasorter'
$wearMain = 'wear/src/main/java/com/sza/fastmediasorter/wear'

function Set-FixtureFile([string]$Rel, [string[]]$Lines) {
    $full = Join-Path $fixture ($Rel -replace '/', [IO.Path]::DirectorySeparatorChar)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $full) | Out-Null
    [IO.File]::WriteAllText($full, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

function New-KtLines([int]$Count, [string]$Tag) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("package fixture.$Tag")
    for ($i = 1; $i -lt $Count; $i++) { $lines.Add("val ${Tag}_$i = $i") }
    return [string[]]$lines
}

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $fixture | Out-Null
    Set-FixtureFile '.sza-profile.json' @('{ "paths": { "tempDir": "temp" } }')
    # alpha: five direct files - above a -MaxFiles 3 cap, so it must be cut into balanced parts.
    foreach ($n in 1..5) { Set-FixtureFile "$appMain/alpha/A$n.kt" (New-KtLines 10 "a$n") }
    # baselined: one file that the lint and detekt baselines both name twice.
    Set-FixtureFile "$appMain/baselined/Base.kt" (New-KtLines 10 'base')
    # beta: three one-file sibling packages that fit one slice together.
    foreach ($n in @('one', 'two', 'three')) { Set-FixtureFile "$appMain/beta/$n/B_$n.kt" (New-KtLines 10 "b$n") }
    # hot: the only file carrying signals, so its slice must rank first.
    Set-FixtureFile "$appMain/hot/Hot.kt" @(
        'package fixture.hot',
        'val a = maybe!!.value!!',
        'fun wire() { player.addListener(listener) }',
        'fun more() { view.setOnClickListener(onClick) }',
        'val state = MutableStateFlow(0)'
    )
    Set-FixtureFile 'app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/launcher/L.kt' (New-KtLines 5 'launcher')
    Set-FixtureFile 'app_v2/src/test/java/com/sza/fastmediasorter/T.kt' (New-KtLines 5 'test')
    Set-FixtureFile 'app_v2/src/debug/java/com/sza/fastmediasorter/D.kt' (New-KtLines 5 'debug')
    Set-FixtureFile "$wearMain/w1/W1.kt" (New-KtLines 6 'w1')
    Set-FixtureFile "$wearMain/w2/W2.kt" (New-KtLines 6 'w2')
    Set-FixtureFile 'app_v2/lint-baseline.xml' @(
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<issues format="6">',
        '    <issue id="X"><location file="src/main/java/com/sza/fastmediasorter/baselined/Base.kt" line="1"/></issue>',
        '    <issue id="Y"><location file="src/main/java/com/sza/fastmediasorter/baselined/Base.kt" line="2"/></issue>',
        '</issues>'
    )
    Set-FixtureFile 'config/detekt/baseline-app_v2.xml' @(
        '<?xml version="1.0" ?>',
        '<SmellBaseline>',
        '  <CurrentIssues>',
        '    <ID>RuleOne:Base.kt$Base$val x</ID>',
        '    <ID>RuleTwo:Base.kt$Base$val y</ID>',
        '  </CurrentIssues>',
        '</SmellBaseline>'
    )
}

function Invoke-Partition {
    param([string]$OutName = 'manifest.json', [string[]]$Extra = @(), [string]$Root = $fixture)
    $out = Join-Path $fixture $OutName
    $output = & $pwshExe -NoProfile -NonInteractive -File $subject -Id S0001 -RepoRoot $Root -OutJson $out -MaxFiles 3 -MaxLoc 8000 -Quiet @Extra 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output; JsonPath = $out }
}

function Read-Manifest([string]$Path) {
    return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json)
}

try {
    Test-Case 'every fixture file sits in exactly one slice and the coverage line says so' {
        Reset-Fixture
        $r = Invoke-Partition
        Assert-Equal 0 $r.ExitCode 'exit code'
        Assert-True ($r.Output -match 'Coverage: 13 files in \d+ slices, 0 uncovered, 0 duplicated') 'coverage line'
        $json = Get-Content -LiteralPath $r.JsonPath -Raw
        foreach ($rel in @("$appMain/alpha/A1.kt", "$appMain/baselined/Base.kt", "$appMain/beta/one/B_one.kt", "$appMain/hot/Hot.kt", "$wearMain/w1/W1.kt")) {
            $hits = ([regex]::Matches($json, [regex]::Escape("`"$rel`""))).Count
            Assert-Equal 1 $hits "occurrences of $rel in the manifest"
        }
    }

    Test-Case 'a package above the file cap is cut into balanced -a and -b parts' {
        $m = Read-Manifest (Join-Path $fixture 'manifest.json')
        $alpha = @($m.slices | Where-Object { $_.packages -contains "com/sza/fastmediasorter/alpha" })
        Assert-Equal 2 $alpha.Count 'alpha parts'
        $names = @($alpha | ForEach-Object { $_.name -replace '^audit-slice-\d+-', '' } | Sort-Object)
        Assert-Equal 'app-alpha-a' $names[0] 'first part name'
        Assert-Equal 'app-alpha-b' $names[1] 'second part name'
        $counts = @($alpha | ForEach-Object { $_.fileCount } | Sort-Object)
        Assert-Equal 2 $counts[0] 'smaller part'
        Assert-Equal 3 $counts[1] 'larger part'
    }

    Test-Case 'three one-file sibling packages fuse into one slice that lists all three' {
        $m = Read-Manifest (Join-Path $fixture 'manifest.json')
        $beta = @($m.slices | Where-Object { $_.packages -contains "com/sza/fastmediasorter/beta/one" })
        Assert-Equal 1 $beta.Count 'beta slices'
        Assert-Equal 3 @($beta[0].packages).Count 'packages in the fused slice'
        Assert-Equal 3 $beta[0].fileCount 'files in the fused slice'
        Assert-True ($beta[0].name -like '*app-beta-one-two*' -or $beta[0].name -like '*app-beta-one-three*') "fused name carries the first and last leaf: $($beta[0].name)"
    }

    Test-Case 'test and debug sets are absent by default and present with -IncludeTests -IncludeDebug' {
        $plain = Get-Content -LiteralPath (Join-Path $fixture 'manifest.json') -Raw
        Assert-Equal 0 ([regex]::Matches($plain, 'src/test/java')).Count 'test files without the flag'
        Assert-Equal 0 ([regex]::Matches($plain, 'src/debug/java')).Count 'debug files without the flag'
        $r = Invoke-Partition -OutName 'with-classb.json' -Extra @('-IncludeTests', '-IncludeDebug')
        Assert-Equal 0 $r.ExitCode 'exit code with flags'
        $wide = Get-Content -LiteralPath $r.JsonPath -Raw
        Assert-Equal 1 ([regex]::Matches($wide, 'src/test/java')).Count 'test file with the flag'
        Assert-Equal 1 ([regex]::Matches($wide, 'src/debug/java')).Count 'debug file with the flag'
        Assert-True ($r.Output -match 'Coverage: 15 files') 'coverage counts the two class B files'
    }

    Test-Case 'two runs on the same tree produce identical manifests apart from generatedAt' {
        $r2 = Invoke-Partition -OutName 'manifest-2.json'
        Assert-Equal 0 $r2.ExitCode 'second run exit code'
        $a = @(Get-Content -LiteralPath (Join-Path $fixture 'manifest.json') | Where-Object { $_ -notmatch '"generatedAt"' })
        $b = @(Get-Content -LiteralPath $r2.JsonPath | Where-Object { $_ -notmatch '"generatedAt"' })
        Assert-Equal $a.Count $b.Count 'line count'
        $diff = @(Compare-Object $a $b)
        Assert-Equal 0 $diff.Count 'differing lines'
    }

    Test-Case 'the slice holding the baselined file reports lint 2 and detekt 2' {
        $m = Read-Manifest (Join-Path $fixture 'manifest.json')
        $slice = @($m.slices | Where-Object { @($_.files | Where-Object { $_.path -eq "$appMain/baselined/Base.kt" }).Count -eq 1 })
        Assert-Equal 1 $slice.Count 'slice holding Base.kt'
        Assert-Equal 2 $slice[0].lint 'lint share'
        Assert-Equal 2 $slice[0].detekt 'detekt share'
        Assert-Equal $false $slice[0].detektAmbiguous 'Base.kt is unique in the module'
    }

    Test-Case 'slices are ordered by risk descending, the signal-bearing file first' {
        $m = Read-Manifest (Join-Path $fixture 'manifest.json')
        Assert-True ($m.slices[0].name -like '*-app-hot') "first slice is the hot one: $($m.slices[0].name)"
        Assert-True ($m.slices[0].risk -gt $m.slices[1].risk) 'first risk above second'
        Assert-Equal 2 $m.slices[0].signals.nonNull 'non-null count'
        Assert-Equal 2 $m.slices[0].signals.listeners 'listener count'
        Assert-Equal 1 $m.slices[0].signals.sharedState 'shared-state count'
        $risks = @($m.slices | ForEach-Object { [double]$_.risk })
        for ($i = 1; $i -lt $risks.Count; $i++) { Assert-True ($risks[$i - 1] -ge $risks[$i]) "risk order at $i" }
    }

    Test-Case 'a missing module root is a cannot-verify naming the root' {
        $r = Invoke-Partition -OutName 'nowhere.json' -Root (Join-Path $fixture 'nowhere')
        Assert-Equal 2 $r.ExitCode 'exit code'
        Assert-True ($r.Output -match "app_v2/src' is missing") "message names the root: $($r.Output.Trim())"
    }

    Test-Case '-FileList narrows the manifest to exactly the listed files' {
        $list = Join-Path $fixture 'list.txt'
        Set-Content -LiteralPath $list -Value @("$appMain/alpha/A1.kt", "$wearMain/w2/W2.kt") -Encoding UTF8
        $r = Invoke-Partition -OutName 'listed.json' -Extra @('-FileList', $list)
        Assert-Equal 0 $r.ExitCode 'exit code'
        $m = Read-Manifest $r.JsonPath
        Assert-Equal 2 $m.tree.files 'files in the tree'
        Assert-Equal 2 @($m.slices).Count 'one slice per module'
        Assert-True ($r.Output -match 'Coverage: 2 files in 2 slices, 0 uncovered, 0 duplicated') 'coverage line'
        Set-Content -LiteralPath $list -Value @("$appMain/alpha/Missing.kt") -Encoding UTF8
        $r2 = Invoke-Partition -OutName 'listed-missing.json' -Extra @('-FileList', $list)
        Assert-Equal 2 $r2.ExitCode 'missing entry exit code'
    }
}
finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("PartitionAuditSlices.Tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
