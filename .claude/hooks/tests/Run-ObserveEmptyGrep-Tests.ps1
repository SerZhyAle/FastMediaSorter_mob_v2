<#
Run-ObserveEmptyGrep-Tests.ps1 - contract tests for observe-empty-grep.ps1 (S1599).

Two independent claims are tested, because passing one says nothing about the other:
  1. CORRECTNESS - given a payload, does the hook speak or stay silent as specified?
  2. REACHABILITY - does the pre-filter registered in .claude/settings.json actually
     deliver that payload to the hook? An unreachable hook is indistinguishable from
     one that allows everything: nothing fails and nothing logs (S1599 strategic
     section 7, and the S1594 pre-filter that matched none of its targets).

Exit codes (CLAUDE.md Rule 7):
  0  all cases passed
  1  at least one case failed
  2  could not run - hook or settings file missing
#>

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$Hook = Join-Path $RepoRoot '.claude\hooks\observe-empty-grep.ps1'
$Settings = Join-Path $RepoRoot '.claude\settings.json'

if (-not (Test-Path -LiteralPath $Hook)) {
    Write-Error "hook not found: $Hook" -ErrorAction Continue
    exit 2
}

$script:Pass = 0
$script:Fail = 0

function Invoke-Hook([string]$json) {
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($tmp, $json, [System.Text.UTF8Encoding]::new($false))
        $out = & { Get-Content -LiteralPath $tmp -Raw | pwsh -NoProfile -File $Hook } 2>$null
        return [PSCustomObject]@{ Out = ($out -join "`n"); Code = $LASTEXITCODE }
    }
    finally { Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue }
}

function New-Payload([hashtable]$toolInput, [string]$response, [string]$tool = 'Grep') {
    @{
        tool_name     = $tool
        tool_input    = $toolInput
        tool_response = $response
    } | ConvertTo-Json -Depth 6 -Compress
}

function Assert-Case([string]$name, [string]$json, [bool]$expectContext) {
    $r = Invoke-Hook $json
    $spoke = $r.Out -match 'additionalContext'
    $codeOk = ($r.Code -eq 0)
    if ($spoke -eq $expectContext -and $codeOk) {
        $script:Pass++
        Write-Host "  ok   $name"
    }
    else {
        $script:Fail++
        Write-Host "  FAIL $name - expected context=$expectContext exit=0 | actual context=$spoke exit=$($r.Code)"
        if ($r.Out) { Write-Host "       out: $($r.Out.Substring(0, [Math]::Min(200, $r.Out.Length)))" }
    }
}

Write-Host 'observe-empty-grep - correctness'

# A pattern certain to exist repo-wide but not under the named path. 'CLAUDE.md' names
# itself at the root, so temp/ is a directory it provably does not live in.
$widenable = New-Payload @{ pattern = 'assert-neuroslop'; path = 'temp' } 'No matches found'
Assert-Case 'widenable miss attaches context' $widenable $true

Assert-Case 'non-Grep tool ignored' `
    (New-Payload @{ pattern = 'assert-neuroslop'; path = 'temp' } 'No matches found' 'Read') $false

Assert-Case 'no path -> nothing to widen' `
    (New-Payload @{ pattern = 'assert-neuroslop' } 'No matches found') $false

Assert-Case 'result had matches -> silent' `
    (New-Payload @{ pattern = 'assert-neuroslop'; path = 'scripts' } "Found 3 files`nscripts/a.ps1") $false

Assert-Case 'pattern exists nowhere -> silent' `
    (New-Payload @{ pattern = 'zzz_no_such_symbol_anywhere_S1599'; path = 'temp' } 'No matches found') $false

Assert-Case 'path is repo root -> not a narrowing' `
    (New-Payload @{ pattern = 'assert-neuroslop'; path = '.' } 'No matches found') $false

Write-Host 'observe-empty-grep - absence checks suppressed'

foreach ($p in @('Timber\.d\("S1410:', 'TODO\(phase-01\)', 'Log\.d\(', 'S1599:', 'GlobalScope')) {
    Assert-Case "absence check '$p' suppressed" `
        (New-Payload @{ pattern = $p; path = 'app_v2' } 'No matches found') $false
}

Write-Host 'observe-empty-grep - fail-silent'

foreach ($bad in @('', 'not json at all', '{"tool_name":', '{}')) {
    $r = Invoke-Hook $bad
    if ($r.Code -eq 0 -and -not ($r.Out -match 'additionalContext')) {
        $script:Pass++; Write-Host "  ok   malformed payload '$($bad.Substring(0, [Math]::Min(14, $bad.Length)))' -> silent exit 0"
    }
    else {
        $script:Fail++; Write-Host "  FAIL malformed payload -> exit=$($r.Code) out=$($r.Out)"
    }
}

Write-Host 'observe-empty-grep - reachability of the registered pre-filter'

# The pre-filter is a shell string in settings.json, not PowerShell. Testing the hook
# alone would pass even if the registration never routed a single call to it, which is
# exactly the failure mode S1594 shipped and did not notice.
$bash = 'C:\Program Files\Git\bin\bash.exe'
if (-not (Test-Path -LiteralPath $Settings)) {
    Write-Host '  SKIP settings.json missing'
}
elseif (-not (Test-Path -LiteralPath $bash)) {
    Write-Host '  SKIP Git Bash not at the pinned path'
}
else {
    $cfg = Get-Content -LiteralPath $Settings -Raw -Encoding UTF8 | ConvertFrom-Json
    $entry = @($cfg.hooks.PostToolUse) | Where-Object { $_.matcher -match 'Grep' } | Select-Object -First 1
    $cmd = if ($entry) { [string]@($entry.hooks)[0].command } else { '' }

    if ([string]::IsNullOrWhiteSpace($cmd) -or $cmd -notmatch 'observe-empty-grep') {
        $script:Fail++
        Write-Host '  FAIL no PostToolUse entry for Grep pointing at observe-empty-grep'
    }
    else {
        # Substitute the shell variable the harness would expand, and neutralise the hook
        # call itself: the claim under test is "does control reach the pwsh invocation",
        # not "what does the hook then print".
        $probe = $cmd.Replace('$CLAUDE_PROJECT_DIR', $RepoRoot.Replace('\', '/'))
        # Stop at the ';;' that closes the case arm - swallowing it would break 'esac'
        # and turn a reachability failure into a shell syntax error that looks the same.
        $probe = [regex]::Replace($probe, 'pwsh[^;]*', 'echo REACHED ')

        foreach ($case in @(
                @{ Name = 'empty result reaches the hook'; Json = $widenable; Want = $true },
                @{ Name = 'non-empty result skips the hook'; Json = (New-Payload @{ pattern = 'x'; path = 'scripts' } 'Found 3 files'); Want = $false }
            )) {
            $out = ($case.Json | & $bash -c $probe) 2>$null
            $reached = ("$out" -match 'REACHED')
            if ($reached -eq $case.Want) {
                $script:Pass++; Write-Host "  ok   $($case.Name)"
            }
            else {
                $script:Fail++
                Write-Host "  FAIL $($case.Name) - expected reached=$($case.Want) | actual reached=$reached"
            }
        }
    }
}

Write-Host ''
if ($script:Fail -eq 0) {
    Write-Host "PASS - $($script:Pass) case(s)"
    exit 0
}
Write-Error "FAIL - $($script:Fail) of $($script:Pass + $script:Fail) case(s) failed" -ErrorAction Continue
exit 1
