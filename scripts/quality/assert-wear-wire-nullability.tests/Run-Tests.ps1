# Subject: scripts/quality/assert-wear-wire-nullability.ps1
<#
.SYNOPSIS
    S2887: Regression suite for assert-wear-wire-nullability.ps1.

.DESCRIPTION
    The gate refuses a bridge declaration whose Kotlin default is not what Gson leaves in the field
    when the key is absent. After S2887 cured the last three offenders, the repository contains no
    failing shape at all - so without this suite the rule's FAIL path is never executed by anything,
    and a widening that silently stopped reporting would look exactly like a clean tree.

    Every case runs the gate against a synthetic tree handed through -RepoRoot, which must carry all
    26 declared input files or the gate exits 2 by design. The fixture writes the whole set as
    minimal stubs and injects the case's declaration into one of them.

.OUTPUTS
    Exit 0 - every case passed.
    Exit 1 - at least one case failed.
    Exit 2 - could not verify: the gate script is missing.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-wear-wire-nullability.tests/Run-Tests.ps1
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gateScript = Join-Path $repoRoot 'scripts/quality/assert-wear-wire-nullability.ps1'
if (-not (Test-Path -LiteralPath $gateScript)) {
    Write-Error "assert-wear-wire-nullability.tests: could not verify - missing $gateScript" -ErrorAction Continue
    exit 2
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

# The gate's own declared input list, read from the script so the fixture cannot drift away from it.
$declaredInputs = Select-String -Path $gateScript -Pattern "^    '(.+\.kt)'$" |
    ForEach-Object { $_.Matches[0].Groups[1].Value }
if ($declaredInputs.Count -lt 2) {
    Write-Error 'assert-wear-wire-nullability.tests: could not verify - failed to read the declared input list' -ErrorAction Continue
    exit 2
}

# The file the case's declaration is injected into, and the one whose findings the case asserts on.
$subjectFile = $declaredInputs[0]

function New-Sandbox([string]$declaration) {
    $sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ('s2887-' + [Guid]::NewGuid().ToString('N'))
    foreach ($rel in $declaredInputs) {
        $full = Join-Path $sandbox $rel
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $full) | Out-Null
        $body = if ($rel -eq $subjectFile) { $declaration } else { '    val required: String' }
        @"
package fixture

data class FixturePayload(
$body
)
"@ | Set-Content -LiteralPath $full -Encoding utf8NoBOM
    }
    $sandbox
}

function Invoke-Gate([string]$declaration, [string]$baselineBody) {
    $sandbox = New-Sandbox $declaration
    $args = @('-NoProfile', '-File', $gateScript, '-RepoRoot', $sandbox, '-Gate')
    if ($null -ne $baselineBody) {
        $baseline = Join-Path $sandbox 'baseline.txt'
        Set-Content -LiteralPath $baseline -Value $baselineBody -Encoding utf8NoBOM
        $args += @('-BaselinePath', $baseline)
    }
    $output = & $pwshExe @args 2>&1 | Out-String
    $result = [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    $result
}

function Test-Case([string]$name, [string]$declaration, [int]$expected, [string]$baselineBody = $null) {
    $r = Invoke-Gate $declaration $baselineBody
    Assert-That $name ($r.ExitCode -eq $expected) "expected exit $expected, got $($r.ExitCode). Output: $($r.Output)"
}

Write-Host 'assert-wear-wire-nullability.tests: primitives at their JVM zero pass'
Test-Case 'Boolean = false passes'   '    val flag: Boolean = false' 0
Test-Case 'Int = 0 passes'           '    val count: Int = 0' 0
Test-Case 'Long = 0L passes'         '    val stamp: Long = 0L' 0
Test-Case 'Double = 0.0 passes'      '    val ratio: Double = 0.0' 0
Test-Case 'Int = 0 with a trailing comment passes' '    val count: Int = 0, // and a note' 0

Write-Host 'assert-wear-wire-nullability.tests: primitives away from their JVM zero fail'
Test-Case 'Boolean = true fails'     '    val flag: Boolean = true' 1
Test-Case 'Int = 4 fails'            '    val version: Int = 4' 1
Test-Case 'Int = CONSTANT fails'     '    val version: Int = SCHEMA_VERSION' 1
Test-Case 'Long = 1L fails'          '    val stamp: Long = 1L' 1

Write-Host 'assert-wear-wire-nullability.tests: reference types with any default fail'
Test-Case 'String = "/" fails'       '    val basePath: String = "/"' 1
Test-Case 'String = "" fails'        '    val domain: String = ""' 1
Test-Case 'List = emptyList() fails' '    val items: List<String> = emptyList()' 1
Test-Case 'Map = emptyMap() fails'   '    val extras: Map<String, String> = emptyMap()' 1

Write-Host 'assert-wear-wire-nullability.tests: the cure and the required key pass'
Test-Case 'Boolean? = null passes'   '    val flag: Boolean? = null' 0
Test-Case 'String? = null passes'    '    val basePath: String? = null' 0
Test-Case 'List? = null passes'      '    val items: List<String>? = null' 0
Test-Case 'no default passes'        '    val basePath: String' 0

Write-Host 'assert-wear-wire-nullability.tests: baseline behaviour'
Test-Case 'a baselined finding passes' '    val version: Int = 4' 0 "$subjectFile::version | a stated reason"
Test-Case 'a baseline row without a justification refuses' '    val version: Int = 4' 2 "$subjectFile::version"
Test-Case 'a baseline row for another field does not cover this one' '    val version: Int = 4' 1 "$subjectFile::other | a stated reason"

Write-Host ''
Write-Host "assert-wear-wire-nullability.tests: $($script:pass) passed, $($script:fail) failed"
if ($script:fail -gt 0) { exit 1 }
exit 0
