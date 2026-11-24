#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for the agent-walk driver and its scenario files: the invocation refusals that need
    no device, the on-screen digest, and the shape every scenario must keep.

# Subject: scripts/devtest/walk.ps1, scripts/devtest/walks/*.md

.DESCRIPTION
    Hermetic: no adb call and no device. The invocation cases run walk.ps1 as a child process on
    arguments it refuses before it reaches a device. The digest cases extract Read-TreeNodes and
    Format-Digest from the script's own syntax tree and run them on a hand-written uiautomator tree,
    so the suite exercises the shipped functions and not a copy. The scenario cases read
    scripts/devtest/walks/*.md and hold each one to the format in scripts/devtest/walks/README.md.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "scripts/devtest/walk.ps1"

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$walk = Join-Path $repoRoot 'scripts/devtest/walk.ps1'
$walksDir = Join-Path $repoRoot 'scripts/devtest/walks'

if (-not (Test-Path -LiteralPath $walk)) {
    Write-Host "FAIL | not found: $walk" -ForegroundColor Red
    exit 1
}

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
        return
    }
    Write-Host "FAIL | $Label" -ForegroundColor Red
    Write-Host "     | expected: $Expected | actual: $Actual" -ForegroundColor Red
    $script:failed++
}

function Assert-True {
    param([bool]$Condition, [string]$Label)
    Assert-Equal $true $Condition $Label
}

function Invoke-Walk {
    param([string[]]$WalkArgs)
    $out = & pwsh -NoProfile -File $walk @WalkArgs 2>&1
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Text = (($out | ForEach-Object { $_.ToString() }) -join "`n") }
}

# ---------------------------------------------------------------------------------------------------
# invocation refusals - all of them answered before a device is touched
# ---------------------------------------------------------------------------------------------------

$scenarioFiles = @(Get-ChildItem -LiteralPath $walksDir -Filter '*.md' -File | Where-Object { $_.BaseName -ne 'README' })
Assert-True ($scenarioFiles.Count -ge 1) 'the walks folder holds at least one scenario'

$list = Invoke-Walk @('list')
Assert-Equal 0 $list.Exit 'list exits 0'
foreach ($f in $scenarioFiles) {
    Assert-True ($list.Text -match [regex]::Escape($f.BaseName)) "list names scenario $($f.BaseName)"
}

Assert-Equal 1 (Invoke-Walk @('begin')).Exit 'begin without -Scenario exits 1'
Assert-Equal 1 (Invoke-Walk @('begin', '-Scenario', 'no-such-scenario')).Exit 'begin with an unknown scenario exits 1'
$firstId = $scenarioFiles[0].BaseName
Assert-Equal 1 (Invoke-Walk @('begin', '-Scenario', $firstId, '-Fresh')).Exit 'begin -Fresh without -Yes exits 1 before any device call'
Assert-Equal 1 (Invoke-Walk @('no-such-verb')).Exit 'an unknown verb exits 1'
Assert-Equal 1 (Invoke-Walk @('step')).Exit 'step without -Name exits 1'
Assert-Equal 1 (Invoke-Walk @('fs')).Exit 'fs without -Path exits 1'

# ---------------------------------------------------------------------------------------------------
# the digest, run on the shipped functions
# ---------------------------------------------------------------------------------------------------

$tokens = $null; $errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($walk, [ref]$tokens, [ref]$errors)
Assert-Equal 0 $errors.Count 'walk.ps1 parses without errors'
$wanted = @('Read-TreeNodes', 'Format-Digest')
$functions = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -in $wanted }, $true)
Assert-Equal $wanted.Count @($functions).Count 'both digest functions are present in walk.ps1'
foreach ($fn in $functions) { . ([scriptblock]::Create($fn.Extent.Text)) }

$DIGEST_LABEL_CAP = 45
$MIN_TARGET_DP = 44
$CRASH_TEXT = 'DEBUG CRASH|isn.t responding|keeps stopping|has stopped'

$treeXml = @'
<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node text="" resource-id="" content-desc="" clickable="false" enabled="true" bounds="[0,0][1080,2400]">
    <node text="Save" resource-id="com.x:id/btnSave" content-desc="" clickable="true" enabled="true" bounds="[100,100][300,260]">
      <node text="Save" resource-id="" content-desc="" clickable="false" enabled="true" bounds="[120,120][280,240]" />
    </node>
    <node text="Tiny" resource-id="com.x:id/btnTiny" content-desc="" clickable="true" enabled="true" bounds="[10,300][60,350]" />
    <node text="Tiny" resource-id="com.x:id/btnTiny" content-desc="" clickable="true" enabled="true" bounds="[10,400][60,450]" />
    <node text="Wide" resource-id="com.x:id/wide" content-desc="" clickable="false" enabled="true" bounds="[900,500][1200,600]" />
    <node text="" resource-id="" content-desc="Привет" clickable="false" enabled="true" bounds="[100,700][400,800]" />
    <node text="FastMediaSorter DEBUG CRASH" resource-id="" content-desc="" clickable="false" enabled="true" bounds="[100,900][900,1000]" />
  </node>
</hierarchy>
'@
$treeFile = Join-Path ([System.IO.Path]::GetTempPath()) ('walk-suite-' + [guid]::NewGuid().ToString('N') + '.xml')
[System.IO.File]::WriteAllText($treeFile, $treeXml, [System.Text.UTF8Encoding]::new($false))
try {
    $nodes = @(Read-TreeNodes $treeFile)
    Assert-Equal 8 $nodes.Count 'every node with bounds is read'
    $digest = Format-Digest -Nodes $nodes -Density 420 -Header 'STEP 00 fixture'
    $text = ($digest.Lines -join "`n")
    Assert-True ($text -match 'labels \(6 of 8 nodes\)') 'the wrapped duplicate label is dropped from the listing'
    Assert-True ($text -match 'Привет') 'a Cyrillic content-description survives the read'
    Assert-True ($text -notmatch '<>') 'a node without an id prints no empty id'
    Assert-Equal 1 @($digest.Flags | Where-Object { $_ -like 'small-target: ''Tiny''*x2' }).Count 'two identical undersized targets collapse into one flag with a count'
    Assert-True (@($digest.Flags | Where-Object { $_ -like 'off-screen: ''Wide''*' }).Count -eq 1) 'a label running past the screen edge is flagged off-screen'
    Assert-True (@($digest.Flags | Where-Object { $_ -like 'crash-text:*' }).Count -eq 1) 'crash text on screen is flagged'
    Assert-True (@($digest.Flags | Where-Object { $_ -like 'small-target: ''Save''*' }).Count -eq 0) 'a button of ordinary size is not flagged small'
    Assert-True ($text -match 'tap by id:') 'clickable nodes are listed by id'
    Assert-True (($text -match 'btnTiny -Index 1') -and ($text -match 'btnTiny -Index 2')) 'a repeated id is listed once per instance with the -Index that taps it'
    Assert-True ($text -match "btnTiny -Index 2  near '") 'a repeated id names the nearest label'
    $bottom = Format-Digest -Nodes $nodes -Density 420 -Header 'STEP 00 fixture' -Cap 2 -Bottom
    Assert-True (($bottom.Lines -join "`n") -match 'earlier label') '-Bottom says how many earlier labels it left out'
    $sliver = @'
<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node text="" resource-id="" content-desc="" clickable="false" enabled="true" bounds="[0,0][1080,2400]">
    <node text="Cut row" resource-id="com.x:id/btnCut" content-desc="" clickable="true" enabled="true" bounds="[100,2398][400,2400]" />
  </node>
</hierarchy>
'@
    $sliverFile = $treeFile + '.sliver.xml'
    [System.IO.File]::WriteAllText($sliverFile, $sliver, [System.Text.UTF8Encoding]::new($false))
    try {
        $cut = Format-Digest -Nodes @(Read-TreeNodes $sliverFile) -Density 420 -Header 'STEP 00 sliver'
        Assert-Equal 0 @($cut.Flags | Where-Object { $_ -like 'small-target:*' }).Count 'a row sliced by the scroll edge is not reported as a small button'
    }
    finally {
        Remove-Item -LiteralPath $sliverFile -Force -ErrorAction SilentlyContinue
    }
}
finally {
    Remove-Item -LiteralPath $treeFile -Force -ErrorAction SilentlyContinue
}

$thin = Format-Digest -Nodes @() -Density 420 -Header 'STEP 00 empty'
Assert-True (@($thin.Flags | Where-Object { $_ -like 'thin-tree:*' }).Count -eq 1) 'an empty tree is flagged as thin, not read as a clean screen'

# ---------------------------------------------------------------------------------------------------
# scenario shape (scripts/devtest/walks/README.md)
# ---------------------------------------------------------------------------------------------------

$ids = @($scenarioFiles | ForEach-Object { $_.BaseName })
foreach ($f in $scenarioFiles) {
    $lines = @(Get-Content -LiteralPath $f.FullName -Encoding UTF8)
    $id = $f.BaseName
    # The closure stamps a registered document with a first line of its own, so the title is the first real line.
    $titleLine = @($lines | Where-Object { $_ -and $_ -notmatch '^<sub class="doc-stamp">' })[0]
    Assert-True ($titleLine -match '^# \S') "$id starts with a title"
    $requires = @($lines | Where-Object { $_ -match '^- requires:\s*\S' })
    Assert-Equal 1 $requires.Count "$id declares requires once"
    if ($requires.Count -eq 1) {
        $needs = @(($requires[0] -replace '^- requires:\s*', '') -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne 'none' })
        foreach ($need in $needs) { Assert-True ($need -in $ids) "$id requires a scenario that exists ($need)" }
    }
    Assert-Equal 1 @($lines | Where-Object { $_ -match '^- flavor:\s*\S' }).Count "$id declares its flavor once"
    Assert-Equal 1 @($lines | Where-Object { $_ -eq '## Intent' }).Count "$id has one Intent section"
    Assert-Equal 1 @($lines | Where-Object { $_ -eq '## Walk' }).Count "$id has one Walk section"

    $checkpoints = @($lines | Where-Object { $_ -match '^### \d+\. ' })
    Assert-True ($checkpoints.Count -ge 1) "$id has at least one checkpoint"
    $names = New-Object System.Collections.Generic.List[string]
    $current = $null
    $blocks = @{}
    foreach ($l in $lines) {
        if ($l -match '^### (\d+)\. (\S+) - ') { $current = "$($Matches[1]) $($Matches[2])"; $blocks[$current] = New-Object System.Collections.Generic.List[string]; continue }
        if ($l -match '^##? ') { $current = $null; continue }
        if ($current) { $blocks[$current].Add($l) }
    }
    Assert-Equal $checkpoints.Count $blocks.Count "$id checkpoint headings are all of the form '### N. <slug> - <what>'"
    foreach ($key in $blocks.Keys) {
        $body = $blocks[$key] -join "`n"
        Assert-True ($body -match '(?m)^- Do:') "$id checkpoint $key has a Do"
        Assert-True ($body -match '(?m)^- (Expect|Truth):') "$id checkpoint $key has an Expect or a Truth"
        $slug = ($key -split ' ', 2)[1]
        Assert-True ($slug -match '^[a-z0-9]+\.[a-z0-9-]+$') "$id checkpoint $key slug is <scenario>.<name>"
    }
    foreach ($m in [regex]::Matches((Get-Content -LiteralPath $f.FullName -Raw -Encoding UTF8), 'walk\.ps1 (?:step|act)[^\r\n`]*?-Name ([A-Za-z0-9._-]+)')) {
        $names.Add($m.Groups[1].Value)
    }
    $dupes = @($names | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name })
    Assert-Equal 0 $dupes.Count "$id uses each frame name once ($($dupes -join ', '))"
}

Write-Host ''
Write-Host "walk.tests: $(if ($script:failed -gt 0) { 'FAIL' } else { 'PASS' }) (run=$($script:passed + $script:failed) skipped=0)"
if ($script:failed -gt 0) { exit 1 }
exit 0
