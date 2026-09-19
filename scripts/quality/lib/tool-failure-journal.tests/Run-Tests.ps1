#requires -Version 7.0
# Subject: scripts/quality/lib/tool-failure-journal.ps1
<#
.SYNOPSIS
    Regression suite for the failing-invocation journal (S3288).

.DESCRIPTION
    Every property this library has is invisible in normal operation, which is precisely why it
    needs a suite. The writer swallows its own errors by design, so a writer that stopped writing
    would look exactly like a session with no failures in it; the trim keeps the file bounded, so a
    trim that stopped working would be noticed only as a second unattended 30 MB journal; and the
    output cap keeps the END of what a script printed, because a script states its reason last.

    The live journal is never touched. Each case dot-sources a COPY of the subject placed inside a
    sandbox that mimics the repository layout, so the path the library derives from its own location
    lands in the sandbox - the same code under test, writing somewhere disposable.

.NOTES
    Exit codes:
      0   all cases pass.
      1   at least one case failed.
      2   the fixtures could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Four levels: scripts/quality/lib/<this suite>/ - one deeper than a suite sitting beside a gate.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..' '..')).Path
$subject = Join-Path $repoRoot 'scripts/quality/lib/tool-failure-journal.ps1'
$scratchRoot = Join-Path $repoRoot 'temp/scratch/S3288-tool-failure-journal'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
        $script:fail++
    }
}

# A sandbox reproduces the three directory levels the library walks up from its own location, so the
# journal it derives lands here instead of in the repository's metrics directory.
function New-Sandbox([string]$Name) {
    $sandbox = Join-Path $scratchRoot $Name
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
    $libDir = Join-Path $sandbox 'scripts/quality/lib'
    New-Item -ItemType Directory -Path $libDir -Force | Out-Null
    Copy-Item -LiteralPath $subject -Destination (Join-Path $libDir 'tool-failure-journal.ps1') -Force
    return $sandbox
}

# Dot-sourcing the same copy twice in one process would redefine the functions in this scope, and a
# later case would then write through an earlier case's location. A child process per case keeps
# each sandbox honest.
function Invoke-InSandbox([string]$Sandbox, [scriptblock]$Body) {
    $pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
        "$env:ProgramFiles\PowerShell\7\pwsh.exe"
    }
    else { 'pwsh' }

    $libPath = Join-Path $Sandbox 'scripts/quality/lib/tool-failure-journal.ps1'
    $script = @"
Set-StrictMode -Version Latest
. '$libPath'
$($Body.ToString())
"@
    $scriptFile = Join-Path $Sandbox 'case.ps1'
    Set-Content -LiteralPath $scriptFile -Value $script -Encoding utf8
    $output = & $pwshExe -NoProfile -File $scriptFile 2>&1
    return [pscustomobject]@{
        Code   = $LASTEXITCODE
        Output = (($output | ForEach-Object { [string]$_ }) -join "`n")
    }
}

function Get-JournalPath([string]$Sandbox) {
    return (Join-Path (Join-Path $Sandbox 'temp/metrics') 'tool-failures.jsonl')
}

try {
    if (Test-Path -LiteralPath $scratchRoot) { Remove-Item -LiteralPath $scratchRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $scratchRoot -Force | Out-Null
}
catch {
    Write-Error "could not prepare the scratch root: $_" -ErrorAction Continue
    exit 2
}

Write-Host "tool-failure-journal.tests" -ForegroundColor Cyan

# --- Case 1: a written row carries every declared field ------------------------------------------
$sandbox = New-Sandbox 'fields'
Invoke-InSandbox $sandbox {
    Write-ToolFailureRecord -Tool 'Bash' -Command 'pwsh -NoProfile -File scripts/quality/assert-detekt.ps1' `
        -ExitCode 1 -OutputTail 'assert-detekt: FAIL' -WorkingDirectory 'P:/repo'
} | Out-Null

$journal = Get-JournalPath $sandbox
$rowText = if (Test-Path -LiteralPath $journal) { (Get-Content -LiteralPath $journal -Raw).Trim() } else { '' }
$row = $null
if ($rowText) { $row = $rowText | ConvertFrom-Json }

Assert-That 'the row parses as JSON' ($null -ne $row) "journal at $journal held: '$rowText'"

if ($null -ne $row) {
    $names = @($row.PSObject.Properties.Name)
    $expected = @('timestampUtc', 'sessionId', 'tool', 'command', 'exitCode', 'outputTail', 'cwd')
    $missing = @($expected | Where-Object { $names -notcontains $_ })
    Assert-That 'every declared field is present' ($missing.Count -eq 0) "missing: $($missing -join ', ')"
    Assert-That 'the exit code is recorded as a number' ([int]$row.exitCode -eq 1) "exitCode=$($row.exitCode)"
    Assert-That 'the command is recorded verbatim' ($row.command -like '*assert-detekt.ps1') "command=$($row.command)"
}

# --- Case 2: an over-long output keeps its TAIL, capped ------------------------------------------
$sandbox = New-Sandbox 'cap'
Invoke-InSandbox $sandbox {
    $long = ('x' * 2500) + 'THE-REASON-IS-HERE'
    Write-ToolFailureRecord -Tool 'PowerShell' -Command 'x.ps1' -ExitCode 2 -OutputTail $long -WorkingDirectory '.'
} | Out-Null

$row = (Get-Content -LiteralPath (Get-JournalPath $sandbox) -Raw).Trim() | ConvertFrom-Json
Assert-That 'an over-long output is capped at 2000 characters' ($row.outputTail.Length -eq 2000) `
    "length=$($row.outputTail.Length)"
Assert-That 'the cap keeps the end, where the reason is' ($row.outputTail.EndsWith('THE-REASON-IS-HERE')) `
    'the truncation dropped the tail instead of the head'

# --- Case 3: the journal is trimmed to its last 500 rows -----------------------------------------
$sandbox = New-Sandbox 'trim'
Invoke-InSandbox $sandbox {
    foreach ($i in 1..501) {
        Write-ToolFailureRecord -Tool 'Bash' -Command "run-$i.ps1" -ExitCode 1 -OutputTail "out $i" -WorkingDirectory '.'
    }
} | Out-Null

$lines = @(Get-Content -LiteralPath (Get-JournalPath $sandbox))
Assert-That 'a 501st row leaves exactly 500 rows' ($lines.Count -eq 500) "rows=$($lines.Count)"
$lastRow = $lines[-1] | ConvertFrom-Json
$firstRow = $lines[0] | ConvertFrom-Json
Assert-That 'the newest row survives the trim' ($lastRow.command -eq 'run-501.ps1') "last=$($lastRow.command)"
Assert-That 'the oldest row is the one dropped' ($firstRow.command -eq 'run-2.ps1') "first=$($firstRow.command)"

# --- Case 4: an unwritable journal returns without throwing --------------------------------------
# A directory occupying the journal's own path makes every write fail at the OS level, which is the
# cheapest way to reach the swallow branch without depending on permissions.
$sandbox = New-Sandbox 'unwritable'
New-Item -ItemType Directory -Path (Get-JournalPath $sandbox) -Force | Out-Null

$result = Invoke-InSandbox $sandbox {
    Write-ToolFailureRecord -Tool 'Bash' -Command 'x.ps1' -ExitCode 1 -OutputTail 'out' -WorkingDirectory '.'
    Write-Host 'REACHED-THE-END'
}
Assert-That 'an unwritable journal does not throw' ($result.Output -match 'REACHED-THE-END') `
    "output was: $($result.Output)"
Assert-That 'an unwritable journal leaves the caller at exit 0' ($result.Code -eq 0) "exit $($result.Code)"

if (Test-Path -LiteralPath $scratchRoot) { Remove-Item -LiteralPath $scratchRoot -Recurse -Force }

Write-Host ""
Write-Host ("tool-failure-journal.tests: {0} passed, {1} failed." -f $script:pass, $script:fail) `
    -ForegroundColor ($script:fail -gt 0 ? 'Red' : 'Green')
exit ($script:fail -gt 0 ? 1 : 0)
