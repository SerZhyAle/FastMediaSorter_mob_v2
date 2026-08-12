# android-string-liveness.Tests.ps1 (S1568) - regression tests for the shared liveness library.
#
# Proves the properties the audit, the removal tool and the ratchet gate all rest on:
#   - a name referenced only from a FLAVOR source set is alive (the S1571 blindness, 222 names deep);
#   - a name referenced only from src/main is alive;
#   - a name referenced only from ANOTHER module is dead (strategic ADR-1, module scope);
#   - <plurals> and <string-array> are reachable through R.plurals. / @array/ (they had no check at all);
#   - a name that is a strict prefix of a referenced name is dead (no `open` kept alive by `open_with`);
#   - a <string-array> declaration reports kind 'string-array', never 'string' - the alternation-order
#     trap, where `<string\b` claims `<string-array` because g/- is a word boundary.
#
# Hermetic / dirty-tree-safe: builds a repo-shaped sandbox under temp/S1568/ and reads nothing tracked.
# Not Pester - the repository's script tests are hand-rolled and run directly, one file at a time.
# Exit 0 = all cases pass, 1 = at least one failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/android-string-liveness.ps1')

$sandbox = Join-Path $repoRoot 'temp/S1568/liveness-tests'
if (Test-Path $sandbox) { Remove-Item -Recurse -Force $sandbox }

$script:pass = 0
$script:fail = 0

function Assert-Equal {
    param([Parameter(Mandatory)][string]$Name, $Expected, $Actual)
    if ($Expected -eq $Actual) {
        Write-Host ("PASS | {0} | {1}" -f $Name, $Actual)
        $script:pass++
    }
    else {
        Write-Host ("FAIL | {0} | expected: {1} | actual: {2}" -f $Name, $Expected, $Actual)
        $script:fail++
    }
}

function New-SandboxFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Content)
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
}

# --- fixture: one module with a main, a flavor and a test source set, plus a second module ---------
$modA = Join-Path $sandbox 'app_v2'
$modB = Join-Path $sandbox 'wear'
$valuesA = Join-Path $modA 'src/main/res/values'

New-SandboxFile (Join-Path $valuesA 'strings.xml') @'
<resources>
    <string name="alive_via_flavor">Flavor</string>
    <string name="alive_via_main">Main</string>
    <string name="dead_but_used_in_other_module">Other</string>
    <string name="open">Open</string>
    <string name="open_with_title">Open with</string>
    <string name="truly_dead">Dead</string>
    <plurals name="alive_plurals">
        <item quantity="one">%d file</item>
        <item quantity="other">%d files</item>
    </plurals>
    <plurals name="dead_plurals">
        <item quantity="other">%d gone</item>
    </plurals>
    <string-array name="alive_array">
        <item>One</item>
    </string-array>
</resources>
'@

# Referenced ONLY from a flavor source set - the exact class a src/main-only scan calls dead.
New-SandboxFile (Join-Path $modA 'src/launcherEnabled/java/Gadget.kt') 'val t = R.string.alive_via_flavor'
New-SandboxFile (Join-Path $modA 'src/main/java/Screen.kt') 'val m = R.string.alive_via_main'
New-SandboxFile (Join-Path $modA 'src/main/res/layout/row.xml') '<TextView android:hint="@string/open_with_title" />'
New-SandboxFile (Join-Path $modA 'src/main/java/Counts.kt') 'val p = R.plurals.alive_plurals'
New-SandboxFile (Join-Path $modA 'src/main/res/layout/spinner.xml') '<Spinner android:entries="@array/alive_array" />'

# The other module references a name declared by app_v2. Separate namespaces: it must NOT keep it alive.
New-SandboxFile (Join-Path $modB 'src/main/java/WearScreen.kt') 'val w = R.string.dead_but_used_in_other_module'

$declared = Get-DeclaredResourceNames -ResDir $valuesA -File 'strings.xml'
$referenced = Get-ReferencedResourceNames -SrcRoot (Join-Path $modA 'src')
$dead = @($declared.Keys | Where-Object { -not $referenced.Contains($_) })

Assert-Equal 'declared count -> 9'                          9 $declared.Count
Assert-Equal 'flavor-only name is alive'                    $false ($dead -contains 'alive_via_flavor')
Assert-Equal 'main-only name is alive'                      $false ($dead -contains 'alive_via_main')
Assert-Equal 'name used only in other module is dead'       $true  ($dead -contains 'dead_but_used_in_other_module')
Assert-Equal 'plurals via R.plurals. is alive'              $false ($dead -contains 'alive_plurals')
Assert-Equal 'unreferenced plurals is dead'                 $true  ($dead -contains 'dead_plurals')
Assert-Equal 'string-array via @array/ is alive'            $false ($dead -contains 'alive_array')
Assert-Equal 'referenced @string/ name is alive'            $false ($dead -contains 'open_with_title')
Assert-Equal 'strict prefix of a referenced name is dead'   $true  ($dead -contains 'open')
Assert-Equal 'plainly unreferenced name is dead'            $true  ($dead -contains 'truly_dead')

# Kind fidelity: `<string\b` matches `<string-array` because g/- is a word boundary, so a naive
# alternation reports every array as a string and the removal tool then deletes the wrong element.
Assert-Equal 'string-array reports its own kind'            'string-array' $declared['alive_array']
Assert-Equal 'plurals reports its own kind'                 'plurals'      $declared['alive_plurals']
Assert-Equal 'string reports its own kind'                  'string'       $declared['open']

# Module scope, stated as the difference it makes rather than as an internal detail.
$referencedB = Get-ReferencedResourceNames -SrcRoot (Join-Path $modB 'src')
Assert-Equal 'other module sees only its own reference'     $true ($referencedB.Contains('dead_but_used_in_other_module'))
Assert-Equal 'other module does not see this module'        $false ($referencedB.Contains('alive_via_flavor'))

# The scanned-file counter is the reported denominator, so it must be real, not a placeholder.
# Six, not five: values/strings.xml itself lives under src and is walked like any other .xml. That is
# deliberate - a declaration is not a reference, but a <string-array> whose <item> is @string/foo IS
# one, and only scanning the values files finds it.
$scanned = 0
$null = Get-ReferencedResourceNames -SrcRoot (Join-Path $modA 'src') -ScannedFileCount ([ref]$scanned)
Assert-Equal 'scanned file count -> 6'                      6 $scanned

# The return must stay a HashSet, not be unrolled into an Object[]. PowerShell enumerates a returned
# collection into the pipeline unless the return is comma-wrapped, and the damage is silent: .Contains
# degrades from O(1) to a linear scan, and an EMPTY result unrolls to $null so .Count throws under
# StrictMode. Found by the removal tests, whose sandbox has no references at all.
$emptyRoot = Join-Path $sandbox 'no-references'
New-SandboxFile (Join-Path $emptyRoot 'src/main/java/Bare.kt') 'val nothing = 1'
$emptySet = Get-ReferencedResourceNames -SrcRoot (Join-Path $emptyRoot 'src')
Assert-Equal 'empty result is still a HashSet'              'HashSet`1' $emptySet.GetType().Name
Assert-Equal 'empty result has a readable Count'            0 $emptySet.Count
Assert-Equal 'populated result is still a HashSet'          'HashSet`1' $referenced.GetType().Name

# A missing strings file is "cannot verify", never "nothing declared and therefore all clean".
$threw = $false
try { $null = Get-UnreferencedResourceNames -Module 'app_v2' -File 'no-such.xml' -RepoRoot $sandbox }
catch { $threw = $true }
Assert-Equal 'missing strings file throws'                  $true $threw

Remove-Item -Recurse -Force $sandbox

Write-Host ("RESULT | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
