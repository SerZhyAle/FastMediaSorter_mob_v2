<#
.SYNOPSIS
    Both-sides harness for guard-bash-unavailable-command.ps1 (S1594).

.DESCRIPTION
    The hook under test is GLOBAL - it lives in the per-machine Claude home
    (~/.claude/hooks/) and is not version-controlled with this checkout. This harness
    is deliberately kept inside the repository so the contract is versioned even
    though the hook is not; strategic S1594 §7 records that gap and names this as its
    mitigation.

    Both sides are covered on purpose: a guard seen only refusing proves nothing
    about what it lets through, and the allowed side is the larger risk here - a
    cmdlet name mentioned in a quoted string, a heredoc body or an argument is
    ordinary and must keep working, and a false refusal makes the Bash tool
    partly unusable.

    The registered pre-filter in ~/.claude/settings.json is tested too, under Git
    Bash specifically. That is not redundant: the first pattern shipped during
    S1594 was `[A-Z]-[A-Z]`, which silently skipped every real cmdlet, because in
    `Select-Object` the character before the hyphen is lowercase. A hook that is
    never reached is indistinguishable from a hook that allows everything, and only
    a pre-filter test catches it.

    Exit codes: 0 - every case passed; 1 - at least one case failed; 2 - could not
    verify (hook, settings file or Git Bash missing).
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$hook = Join-Path $env:USERPROFILE '.claude\hooks\guard-bash-unavailable-command.ps1'
$settings = Join-Path $env:USERPROFILE '.claude\settings.json'
# The Bash tool runs Git Bash. C:\WINDOWS\system32\bash.exe is WSL and would test
# the wrong shell entirely, so the Git Bash path is pinned rather than resolved.
$gitBash = 'C:\Program Files\Git\bin\bash.exe'

if (-not (Test-Path -LiteralPath $hook)) {
    Write-Error "Hook not found: $hook" -ErrorAction Continue
    exit 2
}

$pwshExe = (Get-Process -Id $PID).Path
$passed = 0
$failed = 0

function Invoke-Guard([string]$Command) {
    $payload = @{ tool_name = 'Bash'; tool_input = @{ command = $Command } } | ConvertTo-Json -Depth 5
    $payload | & $pwshExe -NoProfile -File $hook 2>&1 | Out-Null
    return $LASTEXITCODE
}

function Assert-Exit([string]$Name, [string]$Command, [int]$Expected) {
    $actual = Invoke-Guard -Command $Command
    if ($actual -eq $Expected) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:passed++
    }
    else {
        Write-Host "  FAIL  $Name - expected exit $Expected, got $actual" -ForegroundColor Red
        $script:failed++
    }
}

Write-Host 'Refused: heads that cannot work in the Bash tool' -ForegroundColor Yellow

Assert-Exit 'R1 cmdlet after a pipe'          'ls | Select-Object -First 3'      2
Assert-Exit 'R2 cmdlet Out-String'            'grep -c x f | Out-String'         2
Assert-Exit 'R3 cmdlet Select-String'         'cat f | Select-String pattern'    2
Assert-Exit 'R4 cmdlet Get-ChildItem as head' 'Get-ChildItem -Recurse'           2
Assert-Exit 'R5 cmdlet after a semicolon'     'echo a; Write-Output b'           2
Assert-Exit 'R6 node - not installed'         'node script.js'                   2
Assert-Exit 'R7 npm - not installed'          'npm install'                      2
Assert-Exit 'R8 npx - not installed'          'npx something'                    2
Assert-Exit 'R9 PowerShell batching idiom'    '& { echo a; echo b }'             2

Write-Host ''
Write-Host 'Allowed: forms that must keep working' -ForegroundColor Yellow

Assert-Exit 'A1 python3 - the shim resolves it' 'python3 --version'                          0
Assert-Exit 'A2 real Bash if-block'             'if [ -f x ]; then echo y; fi'               0
Assert-Exit 'A3 and-list before a brace group'  'mkdir -p x && { cd x; ls; }'                0
Assert-Exit 'A4 cmdlet name double-quoted'      'grep -n "Select-Object" file.txt'           0
Assert-Exit 'A5 cmdlet name single-quoted'      "grep -n 'Get-ChildItem' file.txt"           0
Assert-Exit 'A6 cmdlet name as a plain arg'     'echo Select-Object'                         0
Assert-Exit 'A7 pwsh -Command wrapper'          'pwsh -NoProfile -Command "& { Get-ChildItem }"' 0
Assert-Exit 'A8 hyphenated real command'        'docker-compose up -d'                       0
Assert-Exit 'A9 capital flags, not a cmdlet'    'grep -A 5 pattern f | sort -R'              0
Assert-Exit 'A10 env-assignment prefix'         'FOO=bar git status'                         0
Assert-Exit 'A11 node inside a path, not head'  'ls node_modules/'                           0

Write-Host ''
Write-Host 'Fail-open: a malformed payload never breaks the Bash tool' -ForegroundColor Yellow

'{ not json at all' | & $pwshExe -NoProfile -File $hook 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host '  PASS  F1 malformed JSON allows' -ForegroundColor Green; $passed++ }
else { Write-Host "  FAIL  F1 malformed JSON - expected 0, got $LASTEXITCODE" -ForegroundColor Red; $failed++ }

'' | & $pwshExe -NoProfile -File $hook 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host '  PASS  F2 empty stdin allows' -ForegroundColor Green; $passed++ }
else { Write-Host "  FAIL  F2 empty stdin - expected 0, got $LASTEXITCODE" -ForegroundColor Red; $failed++ }

$heredoc = "cat <<EOF`nSelect-Object -First 3`nEOF"
$actual = Invoke-Guard -Command $heredoc
if ($actual -eq 0) { Write-Host '  PASS  F3 cmdlet inside a heredoc body allows' -ForegroundColor Green; $passed++ }
else { Write-Host "  FAIL  F3 heredoc body - expected 0, got $actual" -ForegroundColor Red; $failed++ }

Write-Host ''
Write-Host 'Pre-filter: the registered case pattern must reach the hook' -ForegroundColor Yellow

if (-not (Test-Path -LiteralPath $settings)) {
    Write-Host "  SKIP  settings.json not found at $settings" -ForegroundColor DarkYellow
}
elseif (-not (Test-Path -LiteralPath $gitBash)) {
    Write-Host "  SKIP  Git Bash not found at $gitBash" -ForegroundColor DarkYellow
}
else {
    $registration = (Get-Content -LiteralPath $settings -Raw | ConvertFrom-Json).hooks.PreToolUse |
        ForEach-Object { $_.hooks } |
        Where-Object { $_.command -like '*guard-bash-unavailable-command.ps1*' } |
        Select-Object -First 1

    if ($null -eq $registration) {
        Write-Host '  FAIL  P0 hook is not registered in settings.json' -ForegroundColor Red
        $failed++
    }
    else {
        # Recover the case pattern from the live registration so this test tracks
        # whatever is actually wired, not a copy that can drift.
        $m = [regex]::Match($registration.command, 'case\s+"\$input"\s+in\s+(?<pat>.+?)\)\s*printf')
        if (-not $m.Success) {
            Write-Host '  FAIL  P0 could not parse the case pattern from the registration' -ForegroundColor Red
            $failed++
        }
        else {
            $pattern = $m.Groups['pat'].Value
            $mustReach = @(
                'ls | Select-Object -First 3', 'grep -c x f | Out-String',
                'cat f | Select-String p', 'Get-ChildItem -Recurse',
                'echo a | Write-Output b', 'x | ConvertTo-Json',
                'node script.js', 'npm install', '& { echo a; echo b }'
            )
            $mustSkip = @('git status', 'ls -la', 'grep -A 5 pat f', 'sort -R f', 'docker-compose up', 'ls -F')

            foreach ($c in $mustReach) {
                $script = "case '$c' in $pattern) exit 1 ;; *) exit 0 ;; esac"
                & $gitBash -c $script
                if ($LASTEXITCODE -eq 1) { Write-Host "  PASS  P-reach  $c" -ForegroundColor Green; $passed++ }
                else { Write-Host "  FAIL  P-reach  $c - pre-filter skipped it, hook never runs" -ForegroundColor Red; $failed++ }
            }
            foreach ($c in $mustSkip) {
                $script = "case '$c' in $pattern) exit 1 ;; *) exit 0 ;; esac"
                & $gitBash -c $script
                if ($LASTEXITCODE -eq 0) { Write-Host "  PASS  P-skip   $c" -ForegroundColor Green; $passed++ }
                else { Write-Host "  FAIL  P-skip   $c - pre-filter pays pwsh startup needlessly" -ForegroundColor Red; $failed++ }
            }
        }
    }
}

Write-Host ''
if ($failed -gt 0) {
    Write-Host "guard-bash-unavailable-command.tests: $passed passed, $failed failed." -ForegroundColor Red
    exit 1
}
Write-Host "guard-bash-unavailable-command.tests: $passed passed, 0 failed." -ForegroundColor Green
exit 0
