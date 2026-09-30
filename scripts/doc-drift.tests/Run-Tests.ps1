<#
.SYNOPSIS
    Runs every *.Tests.ps1 suite of the doc-drift parsers and prints one RESULT line.

.NOTES
    Exit codes:
      0  every test passed.
      1  at least one test failed; each failure is printed after the RESULT line.
#>
$ErrorActionPreference = 'Stop'

$testsRoot = $PSScriptRoot
. (Join-Path $testsRoot 'Test-Helpers.ps1')

$testFiles = @(Get-ChildItem -LiteralPath $testsRoot -Filter '*.Tests.ps1' | Sort-Object Name)
foreach ($file in $testFiles) {
    . $file.FullName
}

Write-Output ("RESULT | pass: {0} | fail: {1}" -f $script:TestStats.pass, $script:TestStats.fail)
foreach ($failure in $script:TestStats.failures) {
    Write-Output $failure
}

if ($script:TestStats.fail -eq 0) {
    exit 0
}
exit 1
