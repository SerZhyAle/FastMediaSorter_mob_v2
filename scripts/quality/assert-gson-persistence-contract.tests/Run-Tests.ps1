# Run-Tests.ps1 (S2341) - regression suite for the Gson persistence contract gate's type parsing.
#
# The gate read a declared type with the character class [^=,)]+, which cannot tell a comma separating
# type arguments from a comma separating declarations. `Map<String, WearStreamUsage>` was therefore read
# as `Map<String`, and that truncation failed in two opposite directions:
#
#   - at a serialization point it named no model, so a fully pinned model was reported as a point with an
#     unresolvable type - a loud false red;
#   - at a durable model's property it dropped the value model from the transitive walk, so an unpinned
#     model behind `Map<K, Model>` was never judged at all - a silent false green, which is the exact
#     failure the gate exists to prevent.
#
# Neither shape exists in the repository right now: S2146 worked its `Map<String, WearStreamUsage>` around
# by storing a list instead, so a run against the real tree proves nothing either way. Every case here
# therefore runs against a synthetic repository built under temp/scratch and removed in a finally block,
# handed to the gate through -RepoRoot. Nothing here writes into app_v2/src or wear/src.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-gson-persistence-contract.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateScript = Join-Path $repoRoot 'scripts/quality/assert-gson-persistence-contract.ps1'
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

# The package is unique to this suite so no fixture name can collide with a key in the real exemption
# registry, which the gate reads from beside itself rather than from the root under test.
function New-Fixture {
    param([string]$Suffix, [string]$Source)

    $root = Join-Path $repoRoot "temp/scratch/s2341-fixture-$PID-$Suffix"
    $packageDir = Join-Path $root 'app_v2/src/main/java/com/fixture/s2341'
    New-Item -ItemType Directory -Path $packageDir -Force | Out-Null
    Set-Content -Path (Join-Path $packageDir 'Fixture.kt') -Value $Source -Encoding utf8
    $script:roots.Add($root)
    return $root
}

function Invoke-Gate {
    param([string]$Root)

    # -Module app_v2 rather than the default 'all': a single-module run skips the stale-exemption pass,
    # which against a fixture root would legitimately declare every real registry entry unused.
    $output = & $pwshExe -NoProfile -File $gateScript -Module app_v2 -Gate -RepoRoot $Root 2>&1
    return [pscustomobject]@{
        code = $LASTEXITCODE
        text = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    }
}

# A durable sink has to be identifiable in every fixture, otherwise the point is durable for the wrong
# reason ('unidentified') and the case would still pass after the parser regressed.
$pinnedMap = @'
package com.fixture.s2341

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class PinnedUsage(
    @SerializedName("identity") val identity: String,
    @SerializedName("count") val count: Int
)

class PinnedMapStore(private val gson: Gson, private val context: Context) {
    fun store(updated: Map<String, PinnedUsage>) {
        val prefs = context.getSharedPreferences("fixture", Context.MODE_PRIVATE)
        prefs.edit().putString("usage", gson.toJson(updated)).apply()
    }
}
'@

$unpinnedMap = @'
package com.fixture.s2341

import android.content.Context
import com.google.gson.Gson

data class BareUsage(
    val identity: String,
    val count: Int
)

class BareMapStore(private val gson: Gson, private val context: Context) {
    fun store(updated: Map<String, BareUsage>) {
        val prefs = context.getSharedPreferences("fixture", Context.MODE_PRIVATE)
        prefs.edit().putString("usage", gson.toJson(updated)).apply()
    }
}
'@

$mapProperty = @'
package com.fixture.s2341

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class Envelope(
    @SerializedName("id") val id: String,
    @SerializedName("byKey") val byKey: Map<String, Detail>
)

data class Detail(
    val label: String,
    val weight: Int
)

class EnvelopeStore(private val gson: Gson, private val context: Context) {
    fun store(envelope: Envelope) {
        val prefs = context.getSharedPreferences("fixture", Context.MODE_PRIVATE)
        prefs.edit().putString("envelope", gson.toJson(envelope)).apply()
    }
}
'@

$nestedMap = @'
package com.fixture.s2341

import android.content.Context
import com.google.gson.Gson

data class NestedItem(
    val label: String
)

class NestedStore(private val gson: Gson, private val context: Context) {
    fun store(grouped: Map<String, List<NestedItem>>) {
        val prefs = context.getSharedPreferences("fixture", Context.MODE_PRIVATE)
        prefs.edit().putString("grouped", gson.toJson(grouped)).apply()
    }
}
'@

$singleArgument = @'
package com.fixture.s2341

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class SimpleRecord(
    @SerializedName("identity") val identity: String
)

class SimpleListStore(private val gson: Gson, private val context: Context) {
    fun store(records: List<SimpleRecord>) {
        val prefs = context.getSharedPreferences("fixture", Context.MODE_PRIVATE)
        prefs.edit().putString("records", gson.toJson(records)).apply()
    }
}
'@

try {
    Write-Host 'assert-gson-persistence-contract.tests' -ForegroundColor Cyan

    # The case the ticket was opened on. Before the fix this exited 1 with an unresolvable-type line
    # pointing at a model whose every field carried @SerializedName.
    $result = Invoke-Gate -Root (New-Fixture -Suffix 'pinned-map' -Source $pinnedMap)
    Assert-That 'two-argument generic at a serialization point resolves to its value model' `
    ($result.code -eq 0) "expected exit 0, got $($result.code): $($result.text)"
    Assert-That 'a pinned model behind Map<K, V> is not reported as an unresolvable type' `
    ($result.text -notmatch 'unresolved-type') "gate still reported an unresolvable type: $($result.text)"

    # The same shape with the model unpinned has to be reported as the model's own violation, not as a
    # point the gate could not read: those two findings ask the reader for opposite actions.
    $result = Invoke-Gate -Root (New-Fixture -Suffix 'unpinned-map' -Source $unpinnedMap)
    Assert-That 'an unpinned model behind Map<K, V> fails the gate' `
    ($result.code -eq 1) "expected exit 1, got $($result.code): $($result.text)"
    Assert-That 'the unpinned model is named as unannotated, not as an unresolvable type' `
    ($result.text -match 'annotated-none\s+com\.fixture\.s2341\.BareUsage') "expected an annotated-none line for BareUsage: $($result.text)"

    # The silent half. Envelope itself is fully pinned, so before the fix the run was green and Detail was
    # never judged at all - no output line anywhere said a model had been skipped.
    $result = Invoke-Gate -Root (New-Fixture -Suffix 'map-property' -Source $mapProperty)
    Assert-That 'a Map<K, Model> property carries the walk into its value model' `
    ($result.code -eq 1) "expected exit 1, got $($result.code): $($result.text)"
    Assert-That 'the value model reached only through a Map property is named' `
    ($result.text -match 'annotated-none\s+com\.fixture\.s2341\.Detail') "expected an annotated-none line for Detail: $($result.text)"

    # Nesting is handled without recursing into element types: Get-TypeName lifts every identifier out of
    # whatever the parser returns, so depth costs the parser nothing.
    $result = Invoke-Gate -Root (New-Fixture -Suffix 'nested-map' -Source $nestedMap)
    Assert-That 'Map<String, List<Model>> resolves to the innermost model' `
    ($result.text -match 'annotated-none\s+com\.fixture\.s2341\.NestedItem') "expected an annotated-none line for NestedItem: $($result.text)"

    # The control: the single-argument form always worked, and has to keep working. Without it a parser
    # that returned nothing at all would still pass three of the four cases above.
    $result = Invoke-Gate -Root (New-Fixture -Suffix 'single-argument' -Source $singleArgument)
    Assert-That 'the single-argument generic still resolves and stays green' `
    ($result.code -eq 0) "expected exit 0, got $($result.code): $($result.text)"

    Write-Host ''
    Write-Host "  $script:pass passed, $script:fail failed."
}
finally {
    foreach ($root in $script:roots) {
        if (Test-Path -LiteralPath $root) {
            Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

if ($script:fail -gt 0) { exit 1 }
exit 0
