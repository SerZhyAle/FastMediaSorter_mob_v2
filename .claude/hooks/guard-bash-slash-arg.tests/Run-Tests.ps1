<#
.SYNOPSIS
    Both-sides harness for guard-bash-slash-arg.ps1 (S1458).

.DESCRIPTION
    Drives the hook over synthetic tool payloads and asserts its exit code. Both sides are covered
    on purpose: a guard seen only refusing proves nothing about what it lets through, and the
    measured perimeter says the allowed side is the larger risk - legitimate POSIX paths outnumber
    command names in the real corpus by roughly forty to one.

    Exit codes: 0 - every case passed; 1 - at least one case failed.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$hook = Join-Path $PSScriptRoot '..\guard-bash-slash-arg.ps1'
$pwshExe = (Get-Process -Id $PID).Path
$passed = 0
$failed = 0

function Invoke-Guard([string]$Command, [string]$ToolName = 'Bash') {
    $payload = @{ tool_name = $ToolName; tool_input = @{ command = $Command } } | ConvertTo-Json -Depth 5
    $payload | & $pwshExe -NoProfile -File $hook 2>&1 | Out-Null
    return $LASTEXITCODE
}

function Assert-Exit([string]$Name, [string]$Command, [int]$Expected, [string]$ToolName = 'Bash') {
    $actual = Invoke-Guard -Command $Command -ToolName $ToolName
    if ($actual -eq $Expected) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:passed++
    }
    else {
        Write-Host "  FAIL  $Name - expected exit $Expected, got $actual" -ForegroundColor Red
        $script:failed++
    }
}

Write-Host 'Refused: a command name where an argument value is expected' -ForegroundColor Yellow

Assert-Exit 'R1 reason carrying a skill name' `
    'pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name Code -Reason "/spec-dev S1458 phase 02"' 2

Assert-Exit 'R2 a different parameter, same value shape' `
    'pwsh -NoProfile -File x.ps1 -Label "/quick"' 2

Assert-Exit 'R3 the name that is also a path segment, used as a name' `
    'pwsh -NoProfile -File x.ps1 -Reason "/release 31"' 2

Assert-Exit 'R4 single-quoted value' `
    "pwsh -NoProfile -File x.ps1 -Reason '/spec-all S1458'" 2

Write-Host ''
Write-Host 'Allowed: every form the measured corpus says must keep working' -ForegroundColor Yellow

Assert-Exit 'A1 doubled leading slash - the caller-side workaround' `
    'pwsh -NoProfile -File x.ps1 -Reason "//spec-dev S1458 phase 02"' 0

Assert-Exit 'A2 MSYS2_ARG_CONV_EXCL prefix' `
    "MSYS2_ARG_CONV_EXCL='*' pwsh -NoProfile -File x.ps1 -Reason `"/spec-dev S1458`"" 0

Assert-Exit 'A3 absolute POSIX path - the corpus majority' `
    'pwsh -NoProfile -File x.ps1 -Path "/c/Users/serzh/x"' 0

Assert-Exit 'A4 device path' `
    'pwsh -NoProfile -File x.ps1 -Remote /sdcard/Download' 0

Assert-Exit 'A5 /dev/null redirect' `
    'pwsh -NoProfile -File x.ps1 -Out /dev/null' 0

Assert-Exit 'A6 the colliding name as a real path segment' `
    'pwsh -NoProfile -File x.ps1 -Dir "/release/build"' 0

Assert-Exit 'A7 command mentioning no pwsh at all' `
    'ls -la /spec-dev' 0

Assert-Exit 'A8 a non-Bash payload' `
    'pwsh -NoProfile -File x.ps1 -Reason "/spec-dev S1458"' 0 'Read'

Write-Host ''
if ($failed -gt 0) {
    Write-Host "guard-bash-slash-arg.tests: $passed passed, $failed failed." -ForegroundColor Red
    exit 1
}
Write-Host "guard-bash-slash-arg.tests: $passed passed, 0 failed." -ForegroundColor Green
exit 0
