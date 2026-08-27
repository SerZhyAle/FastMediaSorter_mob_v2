#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for the .ps1 token resolution ladder behind assert-script-references (S2124).

.DESCRIPTION
    The ladder decides which file a token names. Rule 5 - an ambiguous bare name is evidence about
    nothing - is the point of S2124, and rules 1-3 are the price of it: without them the switch
    from a name key to a path key invented three orphans that are called every day. Both failure
    directions are silent in production, which is why they are pinned here: a broken rule 1-4
    reports a live script as dead, and a broken rule 5 restores the blindness that let one comment
    vouch for 37 files.

    The fixtures are path strings, not a temporary tree - the resolver never touches the disk.

    EVERY FIXTURE NAME IS INVENTED, AND THAT IS LOAD-BEARING. The gate reads this file as ordinary
    corpus, so a fixture spelling a real script's path would credit that script with a reference it
    does not have - the exact blindness this suite is here to pin down. The names below carry the
    shapes of the real cases (two homonym pairs, one large homonym class with a mixed-case member,
    one unique name) and none of their spellings; the real paths that motivated each case are named
    in prose, where no path token can form.

.NOTES
    Exit codes:
      0 - every case passed
      1 - at least one case failed
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\script-reference-resolution.ps1')

$fixture = @(
    'root-launcher.ps1',
    'tree/alpha/fx-shared.ps1',     # homonym pair with tree/beta, reached as a bare sibling name
    'tree/alpha/fx-caller.ps1',
    'tree/beta/fx-shared.ps1',
    'tree/beta/fx-judge.ps1',       # homonym pair with tree/gamma
    'tree/gamma/fx-judge.ps1',
    'tree/tools/fx-solo.ps1',       # the only carrier of its name
    'tree/probe/fx-driver.ps1',
    'tree/alpha.suite/Fx-Suite.ps1', # the Run-Tests.ps1 analogue: one name, three carriers,
    'tree/beta.suite/Fx-Suite.ps1',  # one of them spelled in another case
    'tree/gamma.suite/fx-suite.ps1'
)
$index = New-ScriptPathIndex -RelativePaths $fixture

$script:Failures = 0
$script:Cases = 0

function Assert-Resolves {
    param(
        [string] $Name,
        [string] $Token,
        [string] $From,
        [string[]] $Expected,
        [bool] $ExpectAmbiguous = $false
    )
    $script:Cases++
    $r = Resolve-ScriptTokenPaths -Token $Token -MentionDirectory $From -Index $index
    $actual = @($r.Paths) -join ', '
    $want = @($Expected) -join ', '
    if ($actual -ne $want) {
        Write-Host "FAIL $Name : expected [$want], got [$actual]" -ForegroundColor Red
        $script:Failures++
        return
    }
    if ($r.Ambiguous -ne $ExpectAmbiguous) {
        Write-Host "FAIL $Name : expected Ambiguous=$ExpectAmbiguous, got $($r.Ambiguous)" -ForegroundColor Red
        $script:Failures++
        return
    }
    Write-Host "  ok  $Name" -ForegroundColor DarkGray
}

# --- rule 1: the token is anchored on the mentioning file's own directory ---------------------
# Real shape: the five builders that dot-source the device-ABI helper write it as a $PSScriptRoot
# path climbing one level. The token regex cannot match a dollar sign, so the variable arrives as
# a plain first segment and only this rule can turn it back into a path.
Assert-Resolves -Name 'rule 1: an anchored token with .. climbs to a sibling directory' `
    -Token 'PSScriptRoot\..\tools\fx-solo.ps1' -From 'tree/probe' `
    -Expected @('tree/tools/fx-solo.ps1')

Assert-Resolves -Name 'rule 1: forward slashes resolve the same as backslashes' `
    -Token 'PSScriptRoot/../tools/fx-solo.ps1' -From 'tree/alpha' `
    -Expected @('tree/tools/fx-solo.ps1')

# A miss must fall through rather than answer "nothing": the token may still name a script by a
# suffix the anchored walk could not reach.
Assert-Resolves -Name 'rule 1: an anchored miss falls through to the suffix rules' `
    -Token 'PSScriptRoot/../../tree/probe/fx-driver.ps1' -From 'tree/alpha' `
    -Expected @('tree/probe/fx-driver.ps1')

# Climbing above the repository root names nothing judgeable here.
Assert-Resolves -Name 'rule 1: climbing above the root resolves to nothing' `
    -Token 'PSScriptRoot/../../../elsewhere/fx-absent.ps1' -From 'tree' `
    -Expected @()

# --- rule 2: a bare name naming a sibling ------------------------------------------------------
# Real shape: a Join-Path call whose directory is a separate argument, so only the quoted file name
# reaches the regex. The catalog CLI reaches its shared library and its validator exactly this way,
# and both of those names are carried by more than one file - without this rule the gate would
# report two scripts that run on every ticket as dead.
Assert-Resolves -Name 'rule 2: a bare homonym resolves to the sibling, not the whole class' `
    -Token 'fx-shared.ps1' -From 'tree/alpha' `
    -Expected @('tree/alpha/fx-shared.ps1')

Assert-Resolves -Name 'rule 2: the same bare name in another directory picks that sibling' `
    -Token 'fx-judge.ps1' -From 'tree/gamma' `
    -Expected @('tree/gamma/fx-judge.ps1')

# --- rule 3: longest resolving path suffix -----------------------------------------------------
Assert-Resolves -Name 'rule 3: a two-segment tail selects one member of a homonym class' `
    -Token 'beta.suite/Fx-Suite.ps1' -From 'tree/probe' `
    -Expected @('tree/beta.suite/Fx-Suite.ps1')

Assert-Resolves -Name 'rule 3: a full repository-relative path selects exactly one file' `
    -Token 'tree/alpha.suite/Fx-Suite.ps1' -From 'docs' `
    -Expected @('tree/alpha.suite/Fx-Suite.ps1')

# --- rule 4: a bare name only one script carries -----------------------------------------------
Assert-Resolves -Name 'rule 4: a unique bare name is its only carrier' `
    -Token 'fx-solo.ps1' -From 'docs' `
    -Expected @('tree/tools/fx-solo.ps1')

Assert-Resolves -Name 'rule 4: a root launcher resolves from a document' `
    -Token 'root-launcher.ps1' -From 'docs' -Expected @('root-launcher.ps1')

# --- rule 5: an ambiguous bare name is evidence about nothing ----------------------------------
# This is S2124. Three comments naming the bare word of the test-runner class marked all 37 of its
# carriers referenced; the caller must see Ambiguous and refuse to credit any of them.
Assert-Resolves -Name 'rule 5: a bare homonym with no sibling is flagged ambiguous' `
    -Token 'Fx-Suite.ps1' -From 'tree/probe' `
    -Expected @('tree/alpha.suite/Fx-Suite.ps1',
    'tree/beta.suite/Fx-Suite.ps1',
    'tree/gamma.suite/fx-suite.ps1') -ExpectAmbiguous $true

Assert-Resolves -Name 'rule 5: an ambiguous name mentioned from outside the tree is still ambiguous' `
    -Token 'fx-judge.ps1' -From 'docs' `
    -Expected @('tree/beta/fx-judge.ps1', 'tree/gamma/fx-judge.ps1') `
    -ExpectAmbiguous $true

# --- case folding -------------------------------------------------------------------------------
# PowerShell hash tables are case-insensitive, which is what collapsed the lower-case spelling of
# the runner name into the capitalised one under the old name key. Under a path key the two members
# must still be told apart.
Assert-Resolves -Name 'case: a mixed-case member is selected by its own path' `
    -Token 'gamma.suite/fx-suite.ps1' -From 'tree' `
    -Expected @('tree/gamma.suite/fx-suite.ps1')

Assert-Resolves -Name 'case: a path suffix matches regardless of the token spelling' `
    -Token 'tree/gamma.suite/FX-SUITE.PS1' -From 'docs' `
    -Expected @('tree/gamma.suite/fx-suite.ps1')

# --- names outside the judged set ----------------------------------------------------------------
Assert-Resolves -Name 'a name no script in the set carries resolves to nothing' `
    -Token 'tree/nowhere/fx-imaginary.ps1' -From 'docs' -Expected @()

if ($script:Failures -gt 0) {
    Write-Host "assert-script-references token resolution: FAIL ($script:Failures of $script:Cases case(s))" -ForegroundColor Red
    exit 1
}
Write-Output "assert-script-references token resolution tests: PASS ($script:Cases cases)"
exit 0
