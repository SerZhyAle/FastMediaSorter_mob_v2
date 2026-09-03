# Run-Tests.ps1 (S2364) - regression suite for the enum persistence gate's name derivation.
#
# The gate built a full name as `package + simple class name` and credited a `.name` receiver by
# matching its identifier against enum simple names case-insensitively. Both halves were wrong in a
# way no green run could show:
#
#   - a nested enum's real R8 name is `Outer$Inner`, so the rule the gate demanded named a class
#     that does not exist and R8 ignored it in silence - a rule that reads as satisfied while
#     protecting nothing;
#   - a variable called `mode` was credited to an enum called `Mode`, which produced a demand for an
#     enum nobody persists and, in the same file, hid `GameMode`, the enum actually written to
#     DataStore there.
#
# None of these shapes reproduces against the real tree once app_v2/proguard-rules.pro is corrected,
# so a run against app_v2 proves neither direction. Every case here therefore builds a synthetic
# repository under temp/scratch, hands it to the gate through -RepoRoot, and removes it in a finally
# block. Nothing here writes into app_v2/src or app_v2/proguard-rules.pro.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-enum-persistence-contract.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateScript = Join-Path $repoRoot 'scripts/quality/assert-enum-persistence-contract.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else { 'pwsh' }

$script:pass = 0
$script:fail = 0
$script:roots = [System.Collections.Generic.List[string]]::new()

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# Each fixture is a whole miniature repository: the gate resolves both its source root and its
# ProGuard file from -RepoRoot, so handing it one directory is enough to isolate a case completely.
function New-Fixture {
    param([string]$Suffix, [hashtable]$Files, [string]$Rules)

    $root = Join-Path $repoRoot "temp/scratch/s2364-fixture-$PID-$Suffix"
    foreach ($relative in $Files.Keys) {
        $target = Join-Path $root "app_v2/src/main/java/com/fixture/s2364/$relative"
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Set-Content -Path $target -Value $Files[$relative] -Encoding utf8
    }
    Set-Content -Path (Join-Path $root 'app_v2/proguard-rules.pro') -Value $Rules -Encoding utf8
    $script:roots.Add($root)
    return $root
}

function Invoke-Gate {
    param([string]$Root)

    $stderr = Join-Path $Root 'gate-stderr.txt'
    & $pwshExe -NoProfile -File $gateScript -RepoRoot $Root -Quiet 2> $stderr | Out-Null
    $code = $LASTEXITCODE
    $text = if (Test-Path -LiteralPath $stderr) { (Get-Content -LiteralPath $stderr -Raw) } else { '' }
    if ($null -eq $text) { $text = '' }
    return [pscustomobject]@{ Code = $code; Text = $text }
}

function New-KeepRule([string]$Fqn) {
    return "-keepclassmembernames enum $Fqn {`n    <fields>;`n}"
}

$pkg = 'com.fixture.s2364'

try {
    Write-Host 'assert-enum-persistence-contract.tests'

    # --- 1. A top-level enum is named with a dot; a nested one with a dollar. ------------------
    $nestingSource = @"
package $pkg

import android.content.SharedPreferences

enum class TopLevelMode { ALPHA, BETA }

class Holder {
    enum class InnerMode { ONE, TWO }

    fun store(prefs: SharedPreferences, top: TopLevelMode, inner: InnerMode) {
        prefs.edit().putString("top", top.name).putString("inner", inner.name).apply()
    }
}
"@
    $root = New-Fixture -Suffix 'nesting' -Files @{ 'Nesting.kt' = $nestingSource } -Rules ''
    $result = Invoke-Gate -Root $root
    Assert-That 'top-level enum is demanded with a dotted name' `
        ($result.Text -match [regex]::Escape("rule for $pkg.TopLevelMode.")) $result.Text
    Assert-That 'nested enum is demanded with a dollar name' `
        ($result.Text -match [regex]::Escape("rule for $pkg.Holder`$InnerMode.")) $result.Text
    Assert-That 'nested enum is never demanded under its bare package name' `
        ($result.Text -notmatch [regex]::Escape("rule for $pkg.InnerMode.")) $result.Text

    # Satisfying exactly those two names must turn the same tree green, which is what proves the
    # demanded spelling is the one a rule can actually carry.
    $root = New-Fixture -Suffix 'nesting-pinned' -Files @{ 'Nesting.kt' = $nestingSource } `
        -Rules ((New-KeepRule "$pkg.TopLevelMode") + "`n" + (New-KeepRule "$pkg.Holder`$InnerMode"))
    $result = Invoke-Gate -Root $root
    Assert-That 'both spellings satisfy the gate' ($result.Code -eq 0) "exit $($result.Code): $($result.Text)"

    # --- 2. A `.name` receiver is credited by its declared type, not by its spelling. ----------
    $typeSource = @"
package $pkg

import androidx.datastore.preferences.core.edit

enum class GameSkin { CLASSIC, CONTRAST }

class Unrelated { val name: String = "" }

class Store(private val dataStore: DataStore<Preferences>) {
    suspend fun save(mode: GameSkin, skin: Unrelated) {
        dataStore.edit { prefs ->
            prefs[KEY] = mode.name
            prefs[OTHER] = skin.name
        }
    }
}
"@
    $root = New-Fixture -Suffix 'receiver-type' -Files @{ 'Store.kt' = $typeSource } -Rules ''
    $result = Invoke-Gate -Root $root
    Assert-That 'an enum-typed receiver is credited to its own enum' `
        ($result.Text -match [regex]::Escape("rule for $pkg.GameSkin.")) $result.Text
    Assert-That 'a receiver whose type is not an enum is credited to nothing' `
        ($result.Text -notmatch 'Unrelated') $result.Text

    # A receiver spelled like an enum but typed as something else must not be credited by spelling
    # alone - this is the exact shape that produced S2364's false red and false green together.
    $spellingSource = @"
package $pkg

import android.content.SharedPreferences

enum class Mode { ALPHA }

enum class Persisted { ONE }

class Writer {
    fun write(prefs: SharedPreferences, mode: Persisted) {
        prefs.edit().putString("k", mode.name).apply()
    }
}
"@
    $root = New-Fixture -Suffix 'spelling' -Files @{ 'Writer.kt' = $spellingSource } `
        -Rules (New-KeepRule "$pkg.Persisted")
    $result = Invoke-Gate -Root $root
    Assert-That 'the enum whose name the variable merely echoes is not demanded' `
        ($result.Code -eq 0) "exit $($result.Code): $($result.Text)"

    # --- 3. A name assembled inside a string template is still observed. -----------------------
    $templateSource = @"
package $pkg

import androidx.datastore.preferences.core.edit

enum class Facing { PORTRAIT, LANDSCAPE }

class Keys(private val dataStore: DataStore<Preferences>) {
    suspend fun write(facing: Facing) {
        dataStore.edit { prefs -> prefs[keyFor("screen_`${facing.name}__")] = true }
    }
}
"@
    $root = New-Fixture -Suffix 'template' -Files @{ 'Keys.kt' = $templateSource } -Rules ''
    $result = Invoke-Gate -Root $root
    Assert-That 'a `.name` inside a string template is observed' `
        ($result.Text -match [regex]::Escape("rule for $pkg.Facing.")) $result.Text

    # --- 4. A simple name declared twice resolves by import, and closes over both when it cannot.
    $collisionShared = @{
        'first/Kind.kt'  = "package $pkg.first`n`nenum class Kind { A, B }`n"
        'second/Kind.kt' = "package $pkg.second`n`nenum class Kind { C, D }`n"
    }
    $importedSource = @"
package $pkg.user

import android.content.SharedPreferences
import $pkg.second.Kind

class Reader {
    fun read(prefs: SharedPreferences): Kind = Kind.valueOf(prefs.getString("k", "C")!!)
}
"@
    $root = New-Fixture -Suffix 'collision-import' `
        -Files ($collisionShared + @{ 'user/Reader.kt' = $importedSource }) -Rules ''
    $result = Invoke-Gate -Root $root
    Assert-That 'an explicit import picks the colliding declaration' `
        ($result.Text -match [regex]::Escape("rule for $pkg.second.Kind.")) $result.Text
    Assert-That 'the unimported colliding declaration is left alone' `
        ($result.Text -notmatch [regex]::Escape("rule for $pkg.first.Kind.")) $result.Text

    $unresolvedSource = @"
package $pkg.user

import android.content.SharedPreferences

class Reader {
    fun read(prefs: SharedPreferences): String = Kind.valueOf(prefs.getString("k", "A")!!).name
}
"@
    $root = New-Fixture -Suffix 'collision-open' `
        -Files ($collisionShared + @{ 'user/Reader.kt' = $unresolvedSource }) -Rules ''
    $result = Invoke-Gate -Root $root
    Assert-That 'an unresolvable collision demands the first candidate' `
        ($result.Text -match [regex]::Escape("rule for $pkg.first.Kind.")) $result.Text
    Assert-That 'an unresolvable collision demands the second candidate too' `
        ($result.Text -match [regex]::Escape("rule for $pkg.second.Kind.")) $result.Text

    # --- 5. A rule naming no declared class is reported. ---------------------------------------
    $root = New-Fixture -Suffix 'dead-rule' -Files @{ 'Nesting.kt' = $nestingSource } `
        -Rules ((New-KeepRule "$pkg.TopLevelMode") + "`n" + (New-KeepRule "$pkg.Holder`$InnerMode") + "`n" + (New-KeepRule "$pkg.Holder.InnerMode"))
    $result = Invoke-Gate -Root $root
    Assert-That 'a rule matching no class fails the gate' ($result.Code -eq 1) "exit $($result.Code)"
    Assert-That 'the dead rule is named in the failure' `
        ($result.Text -match [regex]::Escape("names no declared class - $pkg.Holder.InnerMode.")) $result.Text

    # --- 6. A body-less declaration does not adopt the next block it happens to precede. -------
    $bodylessSource = @"
package $pkg

import android.content.SharedPreferences

class Header(val id: Int)

fun helper(prefs: SharedPreferences, free: FreeStanding) {
    prefs.edit().putString("k", free.name).apply()
}

enum class FreeStanding { ONE, TWO }
"@
    $root = New-Fixture -Suffix 'bodyless' -Files @{ 'Bodyless.kt' = $bodylessSource } -Rules ''
    $result = Invoke-Gate -Root $root
    Assert-That 'an enum after a body-less class stays top-level' `
        ($result.Text -match [regex]::Escape("rule for $pkg.FreeStanding.")) $result.Text
    Assert-That 'a body-less class does not become a parent' `
        ($result.Text -notmatch [regex]::Escape("$pkg.Header`$FreeStanding")) $result.Text

    Write-Host ''
    Write-Host "assert-enum-persistence-contract.tests: $script:pass passed, $script:fail failed."
}
finally {
    foreach ($root in $script:roots) {
        if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

if ($script:fail -gt 0) { exit 1 }
exit 0
