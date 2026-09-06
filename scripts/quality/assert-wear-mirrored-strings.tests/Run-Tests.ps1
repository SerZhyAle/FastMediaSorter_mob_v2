# Run-Tests.ps1 (S2562) - regression suite for the mirrored-strings gate's locale scope.
#
# The gate compares one label's phone copy against its watch copy. S2562 split it in two by the
# AUTHOR of the text being compared, and that split is the thing pinned here:
#   * -Scope Authored compares only the locales the owner writes by hand (en/ru/uk per Rule 30);
#   * -Scope All compares every declared locale, and runs at the release boundary where the batch
#     translation that produces the other ten has actually happened.
#
# Why a suite at all: the difference between the two scopes is invisible to every other gate, and
# collapsing Authored back to the full locale set would silently restore the defect the ticket was
# opened for - a closure refused over 25 findings in keys and locales it never touched. Case A is
# that defect stated directly, and it is the one that must never go green in both directions.
#
# The gate resolves both resource roots from its own $PSScriptRoot, so it cannot be aimed at a
# fixture by parameter. Each case therefore builds a throwaway repository - the gate script, its
# declaration and locale-set.ps1 in the right relative places, plus the two res/ trees - and runs the
# copied gate there. locale-set.ps1 is copied rather than stubbed because Get-StrictLocales is the
# single source of the authored set the gate reads; a stub here would pass while the real pairing
# broke. Neither function it uses reads locales_config.xml, so no such file is needed.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-wear-mirrored-strings.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   could not verify - a file the sandbox must copy is missing from the repository.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gateSource = Join-Path $repoRoot 'scripts/quality/assert-wear-mirrored-strings.ps1'
$localeSetSource = Join-Path $repoRoot 'scripts/utils/locale-set.ps1'
foreach ($required in @($gateSource, $localeSetSource)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Error "assert-wear-mirrored-strings.tests: could not verify - missing $required" -ErrorAction Continue
        exit 2
    }
}

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# Writes one values*/strings.xml. $Entries is key -> text.
function Write-StringTable([string]$ResRoot, [string]$Locale, [hashtable]$Entries) {
    $dir = Join-Path $ResRoot $Locale
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $lines = @('<?xml version="1.0" encoding="utf-8"?>', '<resources>')
    foreach ($key in ($Entries.Keys | Sort-Object)) {
        $lines += "    <string name=""$key"">$($Entries[$key])</string>"
    }
    $lines += '</resources>'
    Set-Content -LiteralPath (Join-Path $dir 'strings.xml') -Value ($lines -join "`n") -Encoding utf8NoBOM
}

# Builds a throwaway repository and returns its root. $Phone/$Watch are locale -> (key -> text).
# $Declaration is the literal .psd1 body; $null writes no declaration file at all.
function New-Sandbox([hashtable]$Phone, [hashtable]$Watch, [string]$Declaration) {
    $sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ("s2562-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path (Join-Path $sandbox 'scripts/quality') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $sandbox 'scripts/utils') | Out-Null

    Copy-Item -LiteralPath $gateSource -Destination (Join-Path $sandbox 'scripts/quality/assert-wear-mirrored-strings.ps1')
    Copy-Item -LiteralPath $localeSetSource -Destination (Join-Path $sandbox 'scripts/utils/locale-set.ps1')

    if ($null -ne $Declaration) {
        Set-Content -LiteralPath (Join-Path $sandbox 'scripts/quality/wear-mirrored-strings.psd1') `
            -Value $Declaration -Encoding utf8NoBOM
    }

    $phoneRes = Join-Path $sandbox 'app_v2/src/main/res'
    $watchRes = Join-Path $sandbox 'wear/src/main/res'
    New-Item -ItemType Directory -Force -Path $phoneRes | Out-Null
    New-Item -ItemType Directory -Force -Path $watchRes | Out-Null
    foreach ($locale in $Phone.Keys) { Write-StringTable $phoneRes $locale $Phone[$locale] }
    foreach ($locale in $Watch.Keys) { Write-StringTable $watchRes $locale $Watch[$locale] }

    return $sandbox
}

function Invoke-SandboxGate([string]$Sandbox, [string]$Scope, [switch]$NoGate) {
    $callArgs = @('-NoProfile', '-File', (Join-Path $Sandbox 'scripts/quality/assert-wear-mirrored-strings.ps1'))
    if (-not $NoGate) { $callArgs += '-Gate' }
    $callArgs += @('-Quiet', '-Scope', $Scope)
    & $pwshExe @callArgs 2>&1 | Out-Null
    return $LASTEXITCODE
}

# Runs one fixture under both scopes and asserts each exit code, then deletes the sandbox.
function Assert-Scopes([string]$Name, [hashtable]$Phone, [hashtable]$Watch, [string]$Declaration,
    [int]$ExpectAuthored, [int]$ExpectAll) {
    $sandbox = New-Sandbox $Phone $Watch $Declaration
    try {
        $authored = Invoke-SandboxGate $sandbox 'Authored'
        $all = Invoke-SandboxGate $sandbox 'All'
        Assert-That "$Name / -Scope Authored" ($authored -eq $ExpectAuthored) "expected exit $ExpectAuthored, got $authored"
        Assert-That "$Name / -Scope All" ($all -eq $ExpectAll) "expected exit $ExpectAll, got $all"
    }
    finally {
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$mirroredDecl = @'
@{
    Pairs = @(
        @{ Phone = 'shared_label'; Watch = 'shared_label'; Mode = 'Mirrored'; Reason = '' }
    )
}
'@

# --- A: the defect the ticket was opened for. A divergence produced by the release-boundary batch
# translation must not refuse a per-ticket closure, and must still be reported at the release. ---
Write-Host 'A: a non-authored locale is release scope only' -ForegroundColor Yellow
Assert-Scopes 'A1 divergence in values-de' `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-de' = @{ shared_label = 'Hintergrund' } } `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-de' = @{ shared_label = 'Uhr-Hintergrund' } } `
    $mirroredDecl 0 1

# --- B: the other half of the split. An authored locale is the change author's own text, so it is
# fatal at closure - narrowing the scope must not have made en/ru/uk unenforced. ---
Write-Host 'B: an authored locale stays fatal per ticket' -ForegroundColor Yellow
Assert-Scopes 'B1 divergence in values-ru' `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-ru' = @{ shared_label = 'Фон' } } `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-ru' = @{ shared_label = 'Фон часов' } } `
    $mirroredDecl 1 1

Assert-Scopes 'B2 divergence in values (en)' `
    @{ 'values' = @{ shared_label = 'Background' } } `
    @{ 'values' = @{ shared_label = 'Watch background' } } `
    $mirroredDecl 1 1

# --- C: the locale-free checks. Both are author intent that cannot be recovered later, so neither
# may be relaxed by a narrower locale scope. ---
Write-Host 'C: the locale-free checks run under both scopes' -ForegroundColor Yellow
Assert-Scopes 'C1 undeclared colliding key' `
    @{ 'values' = @{ shared_label = 'Background'; stray_key = 'Same' } } `
    @{ 'values' = @{ shared_label = 'Background'; stray_key = 'Same' } } `
    $mirroredDecl 1 1

Assert-Scopes 'C2 Independent without a Reason' `
    @{ 'values' = @{ shared_label = 'Background' } } `
    @{ 'values' = @{ shared_label = 'Background' } } `
    @'
@{
    Pairs = @(
        @{ Phone = 'shared_label'; Watch = 'shared_label'; Mode = 'Independent'; Reason = '' }
    )
}
'@ 1 1

# --- D: a pair whose two sides are meant to differ is silenced in every locale, including the ones
# only the release scope compares. ---
Write-Host 'D: Independent with a Reason silences both scopes' -ForegroundColor Yellow
Assert-Scopes 'D1 declared Independent' `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-de' = @{ shared_label = 'Hintergrund' } } `
    @{ 'values' = @{ shared_label = 'Watch background' }; 'values-de' = @{ shared_label = 'Uhr-Hintergrund' } } `
    @'
@{
    Pairs = @(
        @{ Phone = 'shared_label'; Watch = 'shared_label'; Mode = 'Independent'; Reason = 'The watch names the device.' }
    )
}
'@ 0 0

# --- E: a clean tree passes under both, so the cases above fail for the reason claimed and not
# because the sandbox is broken. ---
Write-Host 'E: a tree in step passes under both scopes' -ForegroundColor Yellow
Assert-Scopes 'E1 every locale agrees' `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-ru' = @{ shared_label = 'Фон' }; 'values-de' = @{ shared_label = 'Hintergrund' } } `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-ru' = @{ shared_label = 'Фон' }; 'values-de' = @{ shared_label = 'Hintergrund' } } `
    $mirroredDecl 0 0

# --- F: check 2, the one-sided declaration. Under Authored it is judged in an authored locale and
# ignored in a batch-translated one - the same split as the text comparison, since a pair the batch
# import has not reached yet is not the closing author's omission. ---
Write-Host 'F: a one-sided declaration follows the same split' -ForegroundColor Yellow
Assert-Scopes 'F1 missing watch side in values-de' `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-de' = @{ shared_label = 'Hintergrund' } } `
    @{ 'values' = @{ shared_label = 'Background' } } `
    $mirroredDecl 0 1

Assert-Scopes 'F2 missing watch side in values-ru' `
    @{ 'values' = @{ shared_label = 'Background' }; 'values-ru' = @{ shared_label = 'Фон' } } `
    @{ 'values' = @{ shared_label = 'Background' } } `
    $mirroredDecl 1 1

# --- G: "could not verify" must stay distinguishable from "found a defect" under both scopes -
# a caller that reads 2 as 1 fixes what was reported and ships what was never judged. ---
Write-Host 'G: an unverifiable run exits 2, not 1' -ForegroundColor Yellow
Assert-Scopes 'G1 declaration absent' `
    @{ 'values' = @{ shared_label = 'Background' } } `
    @{ 'values' = @{ shared_label = 'Background' } } `
    $null 2 2

Assert-Scopes 'G2 declaration parses to zero pairs' `
    @{ 'values' = @{ shared_label = 'Background' } } `
    @{ 'values' = @{ shared_label = 'Background' } } `
    "@{ Pairs = @() }" 2 2

# --- H: a scope that selected no locale must not report PASS. Without this the narrow run would
# answer "no divergence" on a tree it never opened, which is the failure mode the scope split itself
# could otherwise introduce. ---
Write-Host 'H: -Scope Authored matching no locale cannot pass' -ForegroundColor Yellow
$sandboxH = New-Sandbox `
    @{ 'values-de' = @{ shared_label = 'Hintergrund' } } `
    @{ 'values-de' = @{ shared_label = 'Uhr-Hintergrund' } } `
    $mirroredDecl
try {
    $codeH = Invoke-SandboxGate $sandboxH 'Authored'
    Assert-That 'H1 no authored locale present -> exit 2' ($codeH -eq 2) "expected exit 2, got $codeH"
}
finally {
    Remove-Item -LiteralPath $sandboxH -Recurse -Force -ErrorAction SilentlyContinue
}

# --- I: without -Gate the gate reports advisorily and exits 0, the shape its siblings use. The
# scope switch must not have turned an advisory run into a blocking one. ---
Write-Host 'I: without -Gate a divergence stays advisory' -ForegroundColor Yellow
$sandboxI = New-Sandbox `
    @{ 'values-ru' = @{ shared_label = 'Фон' }; 'values' = @{ shared_label = 'Background' } } `
    @{ 'values-ru' = @{ shared_label = 'Фон часов' }; 'values' = @{ shared_label = 'Background' } } `
    $mirroredDecl
try {
    $codeI = Invoke-SandboxGate $sandboxI 'Authored' -NoGate
    Assert-That 'I1 advisory run exits 0' ($codeI -eq 0) "expected exit 0, got $codeI"
}
finally {
    Remove-Item -LiteralPath $sandboxI -Recurse -Force -ErrorAction SilentlyContinue
}

# --- J: the live repository under the scope post-change.ps1 actually passes. The cases above prove
# the mechanism on fixtures; this proves the tree a closure is judged against is green today. ---
Write-Host 'J: the live repository passes the per-ticket scope' -ForegroundColor Yellow
& $pwshExe -NoProfile -File $gateSource -Gate -Quiet -Scope Authored 2>&1 | Out-Null
$codeJ = $LASTEXITCODE
Assert-That 'J1 live tree -Scope Authored exits 0' ($codeJ -eq 0) "expected exit 0, got $codeJ"

Write-Host ''
if ($script:fail -gt 0) {
    Write-Host "assert-wear-mirrored-strings.tests: FAIL - $($script:fail) case(s) failed, $($script:pass) passed." -ForegroundColor Red
    exit 1
}
Write-Host "assert-wear-mirrored-strings.tests: PASS - $($script:pass) case(s)." -ForegroundColor Green
exit 0
