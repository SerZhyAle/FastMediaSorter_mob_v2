#requires -Version 7.0
<#
.SYNOPSIS
    S1547 research probe: prove by execution that a dot-sourced 'Stop' collapses an exit code,
    and that the one live candidate shape does NOT collapse.

.DESCRIPTION
    Research item 1 of S1547 asks for evidence from actual runs rather than from reading sources.
    The catalog mutators cannot have their failure paths invoked safely (they rewrite the journal),
    so the shapes are reproduced verbatim in a sandbox instead:

      A  child dot-sources a library that sets Stop, then bare Write-Error + exit 3  -> expect 1
      B  same, but -ErrorAction Continue on the same line                            -> expect 3
      C  same, but multi-line Write-Error with -ErrorAction Continue on line 2       -> expect 3
         (this is the live shape at scripts/spec_catalog/purge-probe-records.ps1:75)

    Read-only against the repository. Sandbox is removed in the finally block.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$sandbox = Join-Path $env:TEMP "s1547.inheritance.$PID"
New-Item -ItemType Directory -Path $sandbox -Force | Out-Null

$fail = 0
try {
    [System.IO.File]::WriteAllText((Join-Path $sandbox '_lib.ps1'), @'
$ErrorActionPreference = 'Stop'
function Get-Nothing { return $null }
'@)

    $cases = @(
        @{ Name = 'A bare Write-Error under inherited Stop'; Expect = 1; Body = @'
. (Join-Path $PSScriptRoot '_lib.ps1')
Write-Error "aborted for a reason"
exit 3
'@ },
        @{ Name = 'B -ErrorAction Continue on the same line'; Expect = 3; Body = @'
. (Join-Path $PSScriptRoot '_lib.ps1')
Write-Error "aborted for a reason" -ErrorAction Continue
exit 3
'@ },
        @{ Name = 'C multi-line, -ErrorAction Continue on the continuation line'; Expect = 3; Body = @'
. (Join-Path $PSScriptRoot '_lib.ps1')
Write-Error ("aborted for a reason, " +
    "spelled across two lines.") -ErrorAction Continue
exit 3
'@ }
    )

    $i = 0
    foreach ($c in $cases) {
        $i++
        $p = Join-Path $sandbox "case$i.ps1"
        [System.IO.File]::WriteAllText($p, $c.Body)
        & $pwshExe -NoProfile -File $p *> $null
        $actual = $LASTEXITCODE
        $ok = ($actual -eq $c.Expect)
        if (-not $ok) { $fail++ }
        $tag = if ($ok) { 'PASS' } else { 'FAIL' }
        $color = if ($ok) { 'Green' } else { 'Red' }
        Write-Host ("  {0}  {1} -> expected: {2} | actual: {3}" -f $tag, $c.Name, $c.Expect, $actual) -ForegroundColor $color
    }
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
if ($fail -gt 0) {
    Write-Error "$fail case(s) did not match the expected exit code." -ErrorAction Continue
    exit 1
}
Write-Host 'All inheritance cases matched their expected exit codes.' -ForegroundColor Green
exit 0
