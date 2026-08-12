<#
.SYNOPSIS
    Behaviour harness for guard-uncapped-read.ps1 after the S1594 rewrite.

.DESCRIPTION
    The hook under test is GLOBAL - it lives in the per-machine Claude home
    (~/.claude/hooks/) and is not version-controlled with this checkout. This harness
    is deliberately kept inside the repository so the contract is versioned even
    though the hook is not; strategic S1594 §7 records that gap and names this as its
    mitigation.

    The hook no longer blocks. It rewrites the Read tool's input, injecting a `limit`
    and telling the model when that window truncated the file. That makes the
    assertions here stronger than an exit code: what matters is the JSON emitted, and
    in particular that the truncation notice appears when and only when content was
    actually cut. A notice that fires on a read that lost nothing trains the reader to
    ignore it; a missing notice on a read that lost 1,200 lines is a correctness bug,
    because the model would reason about a file it only partly saw.

    Fixtures are generated under temp/S1594/fixtures/ rather than pointed at
    repository files, whose line counts drift and would make the suite fail for
    reasons unrelated to the hook.

    Exit codes: 0 - every case passed; 1 - at least one case failed; 2 - could not
    verify (hook missing).
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$hook = Join-Path $env:USERPROFILE '.claude\hooks\guard-uncapped-read.ps1'
if (-not (Test-Path -LiteralPath $hook)) {
    Write-Error "Hook not found: $hook" -ErrorAction Continue
    exit 2
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$fixtures = Join-Path $repoRoot 'temp\S1594\fixtures'
New-Item -ItemType Directory -Force -Path $fixtures | Out-Null

$pwshExe = (Get-Process -Id $PID).Path
$passed = 0
$failed = 0

function New-Fixture([string]$Name, [int]$Lines) {
    $path = Join-Path $fixtures $Name
    Set-Content -LiteralPath $path -Value ((1..$Lines | ForEach-Object { "line $_" }) -join "`n")
    return $path
}

function Invoke-Guard([string]$Path, [hashtable]$Extra) {
    $toolInput = @{ file_path = $Path }
    if ($Extra) { $Extra.GetEnumerator() | ForEach-Object { $toolInput[$_.Key] = $_.Value } }
    $payload = @{ tool_name = 'Read'; tool_input = $toolInput } | ConvertTo-Json -Depth 5 -Compress
    return (($payload | & $pwshExe -NoProfile -File $hook 2>&1) -join '')
}

function Assert([string]$Name, [bool]$Condition, [string]$Detail) {
    if ($Condition) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:passed++
    }
    else {
        Write-Host "  FAIL  $Name - $Detail" -ForegroundColor Red
        $script:failed++
    }
}

$short = New-Fixture 'short.md' 50
$mid = New-Fixture 'mid.md' 300
$long = New-Fixture 'long.md' 2000

Write-Host 'Untouched: reads the hook must not modify' -ForegroundColor Yellow

$out = Invoke-Guard $short $null
Assert 'U1 file at or under the threshold' ([string]::IsNullOrWhiteSpace($out)) "expected no output, got: $out"

$out = Invoke-Guard $long @{ limit = 50 }
Assert 'U2 explicit limit is the escape hatch' ([string]::IsNullOrWhiteSpace($out)) "expected no output, got: $out"

$out = Invoke-Guard $long @{ offset = 100 }
Assert 'U3 explicit offset is the escape hatch' ([string]::IsNullOrWhiteSpace($out)) "expected no output, got: $out"

$out = Invoke-Guard (Join-Path $repoRoot '.claude\commands\build.md') $null
Assert 'U4 .claude/commands is exempt' ([string]::IsNullOrWhiteSpace($out)) "expected no output, got: $out"

$out = Invoke-Guard (Join-Path $repoRoot '.claude/skills/document-registry/SKILL.md') $null
Assert 'U5 .claude/skills is exempt (forward slashes)' ([string]::IsNullOrWhiteSpace($out)) "expected no output, got: $out"

$out = Invoke-Guard (Join-Path $fixtures 'does-not-exist.md') $null
Assert 'U6 missing file falls open' ([string]::IsNullOrWhiteSpace($out)) "expected no output, got: $out"

Write-Host ''
Write-Host 'Rewritten: a window is injected' -ForegroundColor Yellow

$out = Invoke-Guard $mid $null
$json = $out | ConvertFrom-Json
Assert 'R1 mid-length file gets a limit' ($json.hookSpecificOutput.updatedInput.limit -eq 800) `
    "expected limit 800, got $($json.hookSpecificOutput.updatedInput.limit)"
Assert 'R2 mid-length file gets NO notice' ($null -eq $json.hookSpecificOutput.additionalContext) `
    'a file shorter than the window lost nothing, so a notice would be noise'
Assert 'R3 decision is allow' ($json.hookSpecificOutput.permissionDecision -eq 'allow') `
    "expected allow, got $($json.hookSpecificOutput.permissionDecision)"
Assert 'R4 file_path survives the rewrite' ($json.hookSpecificOutput.updatedInput.file_path -eq $mid) `
    'updatedInput must carry the FULL original input, not just the added key'

$out = Invoke-Guard $long $null
$json = $out | ConvertFrom-Json
Assert 'R5 long file gets a limit' ($json.hookSpecificOutput.updatedInput.limit -eq 800) `
    "expected limit 800, got $($json.hookSpecificOutput.updatedInput.limit)"
Assert 'R6 long file DOES get a notice' ($null -ne $json.hookSpecificOutput.additionalContext) `
    'truncating without saying so lets the model reason about a file it only partly saw'
Assert 'R7 notice states the real line count' ($json.hookSpecificOutput.additionalContext -match '2000') `
    "notice must name the true length: $($json.hookSpecificOutput.additionalContext)"
Assert 'R8 notice states how much is hidden' ($json.hookSpecificOutput.additionalContext -match '1200') `
    'notice must quantify what is not shown'
Assert 'R9 hookEventName is set' ($json.hookSpecificOutput.hookEventName -eq 'PreToolUse') `
    "expected PreToolUse, got $($json.hookSpecificOutput.hookEventName)"

Write-Host ''
Write-Host 'Fail-open: a malformed payload never corrupts a read' -ForegroundColor Yellow

$out = ('{ not json at all' | & $pwshExe -NoProfile -File $hook 2>&1) -join ''
Assert 'F1 malformed JSON emits nothing' ([string]::IsNullOrWhiteSpace($out)) "got: $out"

$out = ('' | & $pwshExe -NoProfile -File $hook 2>&1) -join ''
Assert 'F2 empty stdin emits nothing' ([string]::IsNullOrWhiteSpace($out)) "got: $out"

$payload = @{ tool_name = 'Read'; tool_input = @{} } | ConvertTo-Json -Depth 5 -Compress
$out = (($payload | & $pwshExe -NoProfile -File $hook 2>&1) -join '')
Assert 'F3 payload without file_path emits nothing' ([string]::IsNullOrWhiteSpace($out)) "got: $out"

Write-Host ''
Write-Host 'Source invariants' -ForegroundColor Yellow

$src = Get-Content -LiteralPath $hook -Raw
Assert 'S1 hook never blocks' ($src -notmatch '(?m)^\s*exit\s+2\s*$') 'an exit 2 path remains - this hook must never block'
$body = ($src -replace '(?s)^<#.*?#>', '')
Assert 'S2 permissionDecisionReason is never emitted' ($body -notmatch 'permissionDecisionReason') `
    'it is not surfaced to the model, so it cannot carry the notice'

Write-Host ''
if ($failed -gt 0) {
    Write-Host "guard-uncapped-read.tests: $passed passed, $failed failed." -ForegroundColor Red
    exit 1
}
Write-Host "guard-uncapped-read.tests: $passed passed, 0 failed." -ForegroundColor Green
exit 0
