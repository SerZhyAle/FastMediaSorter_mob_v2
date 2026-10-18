#requires -Version 7.0
# Subject: scripts/quality.tests/Run-Tests.ps1
# Exit codes: 0 = all assertions passed; 1 = an assertion failed.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$subject = Join-Path $repoRoot 'scripts/quality.tests/Run-Tests.ps1'
$fixtureRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot "temp/scratch/quality-dispatcher-tests-$PID"))
$scratchRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'temp/scratch')) + [IO.Path]::DirectorySeparatorChar
if (-not $fixtureRoot.StartsWith($scratchRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Fixture root escaped scratch: $fixtureRoot"
}
$passed = 0
$failed = 0

function Assert-That([string]$Name, [bool]$Condition, [string]$Detail) {
    if ($Condition) { $script:passed++; Write-Host "  PASS $Name" }
    else { $script:failed++; Write-Host "  FAIL $Name - $Detail" }
}

$cases = @(
    @{ Name='empty'; Codes=@(); Words=@(); Exit=2; Word='COULD NOT VERIFY' },
    @{ Name='pass'; Codes=@(0); Words=@('PASS'); Exit=0; Word='PASS' },
    @{ Name='failure'; Codes=@(1); Words=@('FAIL'); Exit=1; Word='FAIL' },
    @{ Name='unverified'; Codes=@(2); Words=@('COULD NOT VERIFY'); Exit=2; Word='COULD NOT VERIFY' },
    @{ Name='advisory'; Codes=@(3); Words=@('PASS WITH ADVISORIES'); Exit=3; Word='PASS WITH ADVISORIES' },
    @{ Name='queued'; Codes=@(4); Words=@('COULD NOT VERIFY'); Exit=2; Word='COULD NOT VERIFY' },
    @{ Name='unknown'; Codes=@(7); Words=@('FAIL'); Exit=1; Word='FAIL' },
    @{ Name='disabled'; Codes=@(0,0); Words=@('NOT APPLICABLE','NOT APPLICABLE'); Exit=0; Word='NOT APPLICABLE' },
    @{ Name='mixed-disabled'; Codes=@(0,0); Words=@('NOT APPLICABLE','PASS'); Exit=0; Word='PASS' },
    @{ Name='failure-precedence'; Codes=@(2,3,1); Words=@('COULD NOT VERIFY','PASS WITH ADVISORIES','FAIL'); Exit=1; Word='FAIL' },
    @{ Name='unverified-precedence'; Codes=@(3,2); Words=@('PASS WITH ADVISORIES','COULD NOT VERIFY'); Exit=2; Word='COULD NOT VERIFY' }
)
try {
    foreach ($case in $cases) {
        $caseRoot = Join-Path $fixtureRoot $case.Name
        New-Item -ItemType Directory -Path $caseRoot -Force | Out-Null
        for ($index = 0; $index -lt $case.Codes.Count; $index++) {
            $body = "Write-Host 'child-$index`: $($case.Words[$index])'`nexit $($case.Codes[$index])`n"
            [IO.File]::WriteAllText((Join-Path $caseRoot "$index.Tests.ps1"), $body)
        }
        $output = @(& pwsh -NoProfile -File $subject -TestRoot $caseRoot 2>&1)
        $code = $LASTEXITCODE
        Assert-That "$($case.Name) maps its exit code" ($code -eq $case.Exit) "expected $($case.Exit); actual $code"
        $tail = [string]$output[-1]
        Assert-That "$($case.Name) ends on its own verdict" ($tail -match ('^quality\.tests: ' + [regex]::Escape($case.Word) + '(?:\s|\(|$)')) $tail
    }
    $output = @(& pwsh -NoProfile -File $subject -TestRoot (Join-Path $fixtureRoot 'missing') 2>&1)
    Assert-That 'missing discovery root cannot verify' ($LASTEXITCODE -eq 2 -and [string]$output[-1] -match '^quality\.tests: COULD NOT VERIFY') ($output -join ' | ')
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
}
Write-Host "quality-dispatcher.tests: $(if ($failed) { 'FAIL' } else { 'PASS' }) (run=$($passed + $failed) skipped=0)"
exit $(if ($failed) { 1 } else { 0 })
