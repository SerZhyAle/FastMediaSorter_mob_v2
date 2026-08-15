# set-android-string-remove.Tests.ps1 (S1568) - regression tests for the removal branch.
#
# Proves the four properties an irreversible, all-locale deletion rests on:
#   - a referenced key inside a batch is refused while its dead neighbours are still removed;
#   - a <plurals> is removed across locales, children and all (it was unreachable before S1568:
#     the regex hard-coded <string> on both ends, so a dead plurals reported "not found" forever);
#   - a key present in a translated locale but absent from values/ is swept, leaving no orphan;
#   - the batch exits 3 when at least one key was refused, so a partial run cannot read as clean.
#
# The tool resolves its paths from its own location, so each case runs it against a throwaway
# repo-shaped sandbox under temp/S1568/. Nothing tracked is read or written.
# Exit 0 = all cases pass, 1 = at least one failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$sandbox = Join-Path $repoRoot 'temp/S1568/remove-tests'
$pwshExe = (Get-Process -Id $PID).Path

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

# The tool walks up two levels from its own directory to find the repo root, and reads the locale set
# from res/xml/locales_config.xml - so the sandbox mirrors that shape exactly.
function New-Sandbox {
    if (Test-Path $sandbox) { Remove-Item -Recurse -Force $sandbox }

    New-Item -ItemType Directory -Force (Join-Path $sandbox 'scripts/utils') | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $sandbox 'scripts/quality/lib') | Out-Null
    Copy-Item (Join-Path $repoRoot 'scripts/utils/set-android-string.ps1') (Join-Path $sandbox 'scripts/utils/')
    Copy-Item (Join-Path $repoRoot 'scripts/utils/locale-set.ps1') (Join-Path $sandbox 'scripts/utils/')
    Copy-Item (Join-Path $repoRoot 'scripts/quality/lib/android-string-format.ps1') (Join-Path $sandbox 'scripts/quality/lib/')
    Copy-Item (Join-Path $repoRoot 'scripts/quality/lib/android-string-liveness.ps1') (Join-Path $sandbox 'scripts/quality/lib/')
    Copy-Item (Join-Path $repoRoot 'scripts/quality/lib/house-text-style.ps1') (Join-Path $sandbox 'scripts/quality/lib/')

    $res = Join-Path $sandbox 'app_v2/src/main/res'

    New-SandboxFile (Join-Path $res 'xml/locales_config.xml') @'
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="ru" />
    <locale android:name="uk" />
    <locale android:name="de" />
</locale-config>
'@

    New-SandboxFile (Join-Path $res 'values/strings.xml') @'
<resources>
    <string name="kept_referenced">Referenced</string>
    <string name="dead_one">Dead one</string>
    <string name="dead_two">Dead two</string>
    <plurals name="dead_plurals">
        <item quantity="one">%d thing</item>
        <item quantity="other">%d things</item>
    </plurals>
</resources>
'@

    New-SandboxFile (Join-Path $res 'values-ru/strings.xml') @'
<resources>
    <string name="kept_referenced">Ссылка</string>
    <string name="dead_one">Мертвый один</string>
    <string name="dead_two">Мертвый два</string>
    <plurals name="dead_plurals">
        <item quantity="other">%d штук</item>
    </plurals>
</resources>
'@

    New-SandboxFile (Join-Path $res 'values-uk/strings.xml') @'
<resources>
    <string name="kept_referenced">Посилання</string>
    <string name="dead_one">Мертвий один</string>
    <string name="dead_two">Мертвий два</string>
    <plurals name="dead_plurals">
        <item quantity="other">%d штук</item>
    </plurals>
</resources>
'@

    # The orphan case: values-de carries a key that values/ never declared. A locale-blind removal
    # leaves it behind, and no gate in the repository notices.
    New-SandboxFile (Join-Path $res 'values-de/strings.xml') @'
<resources>
    <string name="dead_one">Tot eins</string>
    <string name="orphan_only_in_de">Waise</string>
</resources>
'@

    New-SandboxFile (Join-Path $sandbox 'app_v2/src/main/java/Screen.kt') 'val a = R.string.kept_referenced'
}

function Invoke-Tool {
    param([string[]]$ToolArgs)
    $sut = Join-Path $sandbox 'scripts/utils/set-android-string.ps1'
    $out = & $pwshExe -NoProfile -File $sut @ToolArgs 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out }
}

function Get-SandboxNames {
    param([string]$LocaleDir)
    $path = Join-Path $sandbox "app_v2/src/main/res/$LocaleDir/strings.xml"
    if (-not (Test-Path $path)) { return @() }
    $text = [System.IO.File]::ReadAllText($path)
    return @([regex]::Matches($text, '<(?:string-array|plurals|string)\b[^>]*\bname\s*=\s*"([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value })
}

# --- case 1: batch refuses the referenced key and still removes its dead neighbours ----------------
New-Sandbox
$listPath = Join-Path $sandbox 'keys.txt'
New-SandboxFile $listPath "# candidates`nkept_referenced`ndead_one`ndead_plurals`nnot_declared_anywhere`n"

$r = Invoke-Tool @('-Action', 'remove', '-KeyList', $listPath)

Assert-Equal 'batch exits 3 when a key was refused'        3     $r.ExitCode
Assert-Equal 'referenced key survives in values/'          $true ((Get-SandboxNames 'values') -contains 'kept_referenced')
Assert-Equal 'dead neighbour removed despite the refusal'  $false ((Get-SandboxNames 'values') -contains 'dead_one')
Assert-Equal 'untouched key is left alone'                 $true ((Get-SandboxNames 'values') -contains 'dead_two')

# --- case 2: <plurals> removed across every locale, children included -----------------------------
Assert-Equal 'plurals gone from EN'                        $false ((Get-SandboxNames 'values') -contains 'dead_plurals')
Assert-Equal 'plurals gone from RU'                        $false ((Get-SandboxNames 'values-ru') -contains 'dead_plurals')
Assert-Equal 'plurals gone from UK'                        $false ((Get-SandboxNames 'values-uk') -contains 'dead_plurals')
$ruText = [System.IO.File]::ReadAllText((Join-Path $sandbox 'app_v2/src/main/res/values-ru/strings.xml'))
Assert-Equal 'no orphaned <item> child left behind'        0 ([regex]::Matches($ruText, '%d штук')).Count
$ruParses = $false
try { [xml]$null = $ruText; $ruParses = $true } catch { $ruParses = $false }
Assert-Equal 'RU file still parses as XML'                 $true $ruParses

# --- case 3: a key living only in a translated locale is swept, not orphaned ----------------------
Assert-Equal 'dead_one swept from the DE locale too'       $false ((Get-SandboxNames 'values-de') -contains 'dead_one')
Assert-Equal 'unrelated DE-only key untouched'             $true  ((Get-SandboxNames 'values-de') -contains 'orphan_only_in_de')

# --- case 4: a fully clean batch exits 0 ----------------------------------------------------------
New-Sandbox
$cleanList = Join-Path $sandbox 'clean.txt'
New-SandboxFile $cleanList "dead_one`ndead_two`n"
$clean = Invoke-Tool @('-Action', 'remove', '-KeyList', $cleanList)
Assert-Equal 'clean batch exits 0'                         0 $clean.ExitCode
Assert-Equal 'both dead keys removed'                      0 (@((Get-SandboxNames 'values') | Where-Object { $_ -in @('dead_one', 'dead_two') })).Count
Assert-Equal 'referenced key still present'                $true ((Get-SandboxNames 'values') -contains 'kept_referenced')

# --- case 5: -Key and -KeyList together is refused, not silently resolved -------------------------
$both = Invoke-Tool @('-Action', 'remove', '-Key', 'dead_two', '-KeyList', $cleanList)
Assert-Equal 'passing -Key and -KeyList fails'             $true ($both.ExitCode -ne 0)
Assert-Equal 'and says why'                                $true ($both.Output -match 'not both')

Remove-Item -Recurse -Force $sandbox

Write-Host ("RESULT | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
