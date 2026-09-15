# Run-Tests.ps1 (S3152) - regression suite for scripts/quality/audit-stale-suppressions.ps1.
#
# The audit deletes source annotations, so a wrong scope or a wrong ordinal silently removes a live
# suppression. Each fixture pins one decision: live vs stale, a multi-name annotation losing one name,
# @file:Suppress, an inline parameter annotation, a trailing comment, stacked annotations with indented
# arguments, nested scopes, an unclassified warning, a file edited after Mask, and byte-exact Restore
# of a BOM + CRLF file.
#
# Hermetic: every case runs in a fresh directory under the system temp path.
#
# Usage:  pwsh -NoProfile -File scripts/quality/audit-stale-suppressions.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$audit = (Resolve-Path (Join-Path $PSScriptRoot '..' 'audit-stale-suppressions.ps1')).Path
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

function New-Fixture([hashtable]$files) {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("s3152-" + [guid]::NewGuid().ToString('N'))
    foreach ($name in $files.Keys) {
        $path = Join-Path $root "src/$name"
        New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
        [System.IO.File]::WriteAllText($path, $files[$name], (New-Object System.Text.UTF8Encoding($false)))
    }
    return (Resolve-Path $root).Path
}

function Invoke-Audit([string]$root, [string[]]$arguments) {
    $output = & pwsh -NoProfile -File $audit -RepoRoot $root -OutDir 'out' @arguments 2>&1
    return @{ Code = $LASTEXITCODE; Output = ($output -join "`n") }
}

function Read-Fixture([string]$root, [string]$name) {
    return [System.IO.File]::ReadAllText((Join-Path $root "src/$name"))
}

function Get-Warning([string]$root, [string]$name, [int]$line, [string]$message) {
    return "w: file:///$($root.Replace('\', '/'))/src/${name}:${line}:5 $message"
}

Write-Host 'audit-stale-suppressions: Mask -> Analyze -> Apply' -ForegroundColor Yellow

$a = @'
package a

class A {
    @Suppress("DEPRECATION")
    fun live() {
        old()
    }

    @Suppress("DEPRECATION", "MagicNumber")
    fun stale() {
        val x = 42
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    fun half(o: Any) {
        val l = o as List<String>
    }
}
'@ -replace "`r`n", "`n"

$b = "@file:Suppress(`"DEPRECATION`")`n`npackage b`n`nfun f() = Unit`n"

$c = @'
package c

fun g(@Suppress("UNUSED_PARAMETER") p: Int) = Unit

@Suppress("SENSELESS_COMPARISON") // kept by a warning the audit cannot classify
fun h(x: String) = x != null
'@ -replace "`r`n", "`n"

$d = "package d`n`n@Suppress(`"DEPRECATION`")`nfun k() = Unit`n"

$e = @'
package e

@Suppress("DEPRECATION")
@Deprecated(
    "x"
)
fun m() {
    old()
}
'@ -replace "`r`n", "`n"

$f = @'
package f

@Suppress("DEPRECATION")
class F {
    @Suppress("DEPRECATION")
    fun n() {
        old()
    }
}
'@ -replace "`r`n", "`n"

$h = "package h`n`n@Suppress(`"UNUSED_PARAMETER`")`nfun r(p: Int) = Unit`n"

$root = New-Fixture @{ 'A.kt' = $a; 'B.kt' = $b; 'C.kt' = $c; 'D.kt' = $d; 'E.kt' = $e; 'F.kt' = $f; 'H.kt' = $h }
try {
    $mask = Invoke-Audit $root @('-Verb', 'Mask', '-Roots', 'src')
    Assert-That 'M1 Mask exits 0' ($mask.Code -eq 0) $mask.Output
    Assert-That 'M2 Mask keeps the line count' ((Read-Fixture $root 'A.kt').Split("`n").Count -eq $a.Split("`n").Count) 'line count changed'
    Assert-That 'M3 Mask leaves no bare compiler name' (-not ((Read-Fixture $root 'A.kt') -match '"DEPRECATION"')) (Read-Fixture $root 'A.kt')
    Assert-That 'M4 Mask keeps a detekt name untouched' ((Read-Fixture $root 'A.kt').Contains('"MagicNumber"')) (Read-Fixture $root 'A.kt')

    $again = Invoke-Audit $root @('-Verb', 'Mask', '-Roots', 'src')
    Assert-That 'M5 Mask over a masked tree exits 2' ($again.Code -eq 2) "exit $($again.Code)"

    $log = Join-Path $root 'compile.log'
    @(
        '> Task :app:compileKotlin'
        (Get-Warning $root 'A.kt' 6 "'fun old(): Unit' is deprecated. Deprecated in Java.")
        (Get-Warning $root 'A.kt' 16 "Unchecked cast of 'Any' to 'List<String>'.")
        (Get-Warning $root 'C.kt' 6 'A diagnostic text this audit has never seen.')
        (Get-Warning $root 'E.kt' 8 "'fun old(): Unit' is deprecated.")
        (Get-Warning $root 'F.kt' 7 "'fun old(): Unit' is deprecated.")
    ) | Set-Content -LiteralPath $log -Encoding utf8

    $emptyLog = Join-Path $root 'empty.log'
    '> Task :app:compileKotlin UP-TO-DATE' | Set-Content -LiteralPath $emptyLog -Encoding utf8
    $refused = Invoke-Audit $root @('-Verb', 'Analyze', '-Logs', $emptyLog)
    Assert-That 'N1 Analyze refuses a log with no warning line' ($refused.Code -eq 2) "exit $($refused.Code)"

    $detektLog = Join-Path $root 'detekt.log'
    $detektRoot = $root.Replace('\', '/').ToLowerInvariant()
    @(
        "assert-detekt:   $detektRoot/src/h.kt:4:7 - UnusedParameter - Function parameter ``p`` is unused."
        "assert-detekt:   $detektRoot/src/c.kt:3:1 - MaxLineLength - Line detected, which is longer than the defined maximum."
    ) | Set-Content -LiteralPath $detektLog -Encoding utf8

    Add-Content -LiteralPath (Join-Path $root 'src/D.kt') -Value '// edited by a sibling session'
    $analyze = Invoke-Audit $root @('-Verb', 'Analyze', '-Logs', "$log,$detektLog")
    Assert-That 'N2 Analyze exits 0' ($analyze.Code -eq 0) $analyze.Output

    $apply = Invoke-Audit $root @('-Verb', 'Apply')
    Assert-That 'P1 Apply exits 0' ($apply.Code -eq 0) $apply.Output

    $outA = Read-Fixture $root 'A.kt'
    Assert-That 'P2 live annotation kept' ($outA.Contains("    @Suppress(`"DEPRECATION`")`n    fun live()")) $outA
    Assert-That 'P3 stale name dropped, detekt name kept' ($outA.Contains("    @Suppress(`"MagicNumber`")`n    fun stale()")) $outA
    Assert-That 'P4 multi-name annotation loses only the stale name' ($outA.Contains("    @Suppress(`"UNCHECKED_CAST`")`n    fun half(")) $outA

    $outB = Read-Fixture $root 'B.kt'
    Assert-That 'P5 stale @file:Suppress line deleted' ($outB -eq "`npackage b`n`nfun f() = Unit`n") $outB

    $outC = Read-Fixture $root 'C.kt'
    Assert-That 'P6 stale inline parameter annotation removed' ($outC.Contains('fun g(p: Int) = Unit')) $outC
    Assert-That 'P7 unclassified warning keeps the annotation' ($outC.Contains('@Suppress("SENSELESS_COMPARISON") // kept')) $outC

    $outD = Read-Fixture $root 'D.kt'
    Assert-That 'P8 file edited after Mask keeps every name' ($outD.Contains('@Suppress("DEPRECATION")')) $outD

    $outE = Read-Fixture $root 'E.kt'
    Assert-That 'P9 stacked annotation with indented arguments reaches the body' ($outE.Contains('@Suppress("DEPRECATION")')) $outE

    $outF = Read-Fixture $root 'F.kt'
    Assert-That 'P10 innermost annotation owns the warning' ($outF.Contains("    @Suppress(`"DEPRECATION`")`n    fun n()")) $outF
    Assert-That 'P11 outer annotation shadowed by the inner one is removed' ($outF.StartsWith("package f`n`nclass F {")) $outF

    $outH = Read-Fixture $root 'H.kt'
    Assert-That 'P14 a detekt alias finding keeps the compiler name' ($outH.Contains('@Suppress("UNUSED_PARAMETER")')) $outH

    $leftover = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Filter '*.kt' | Where-Object { (Get-Content -LiteralPath $_.FullName -Raw).Contains('AUDIT_MASKED_') })
    Assert-That 'P12 no marker left' ($leftover.Count -eq 0) ($leftover -join ', ')

    $applied = @(Get-Content -LiteralPath (Join-Path $root 'out/applied-files.txt'))
    Assert-That 'P13 applied-files lists exactly the files that lost a name' ((@($applied | Sort-Object) -join ',') -eq 'src/A.kt,src/B.kt,src/C.kt,src/F.kt') ($applied -join ',')
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host 'audit-stale-suppressions: Restore is byte-exact' -ForegroundColor Yellow

$g = "package g`r`n`r`n@Suppress(`"DEPRECATION`", `"UNUSED_PARAMETER`")`r`nfun q(p: Int) = Unit`r`n"
$root = New-Fixture @{ 'G.kt' = '' }
try {
    $gPath = Join-Path $root 'src/G.kt'
    [System.IO.File]::WriteAllText($gPath, $g, (New-Object System.Text.UTF8Encoding($true)))
    $original = [System.IO.File]::ReadAllBytes($gPath)

    $mask = Invoke-Audit $root @('-Verb', 'Mask', '-Roots', 'src')
    Assert-That 'R1 Mask exits 0' ($mask.Code -eq 0) $mask.Output
    $restore = Invoke-Audit $root @('-Verb', 'Restore')
    Assert-That 'R2 Restore exits 0' ($restore.Code -eq 0) $restore.Output
    $restored = [System.IO.File]::ReadAllBytes($gPath)
    Assert-That 'R3 Restore returns the original bytes (BOM + CRLF)' ([System.Linq.Enumerable]::SequenceEqual($original, $restored)) 'bytes differ'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "audit-stale-suppressions tests: $script:pass passed, $script:fail failed"
if ($script:fail -gt 0) { exit 1 }
exit 0
