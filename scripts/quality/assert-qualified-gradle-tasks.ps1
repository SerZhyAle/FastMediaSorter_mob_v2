#requires -Version 7.0
<#
.SYNOPSIS
    Rejects a Gradle variant task name written without its module segment in a repository script or
    a GitHub Actions workflow.

.DESCRIPTION
    S2172. An unqualified task name is not a shorter spelling of a qualified one - Gradle expands it
    across every project in the build that declares it. So `assembleStandardDebug` means "app_v2 and
    wear" the moment both modules declare a `standard` flavor, and it changes meaning without a single
    edit to the script that passes it.

    S2175 extended the scan to `.github/workflows/*.yml`/`*.yaml`: the same regression reached CI
    workflow YAML, and a `.ps1`-only scanner could not see it. The YAML root judges one extra verb,
    `test` - excluded from the `.ps1` root because it collides with directory-path strings there
    (`testStandardDebugUnitTest` as a report directory), a collision that does not reproduce in the
    two live workflow files.

    That is exactly what happened. S2090 gave the watch its own `standard` / `noLegal` dimension, and
    eighteen phone entry points silently began building the watch too: measured 2026-08-27,
    `gradlew assembleStandardDebug --dry-run` schedules 48 `:wear:` tasks beside 53 `:app_v2:` ones,
    `:app_v2:assembleStandardDebug` schedules 0. The consequences are not cosmetic - the entry point
    takes only the `Build.Phone` lock (Rule 23) while writing into `wear/build/**`, so a sibling
    session's watch build dies on a locked `R.jar` with a message that reads as broken code, and a
    watch artifact built by a phone task carries the phone's `versionCode`.

    The rule is therefore about meaning, not style: `:<module>:<task>` says what it does and survives
    a new module declaring the same variant.

    Two forms are judged, because a builder writes a task name in two ways:
      literal        - assembleStandardRelease, bundleNoLegalRelease, testStandardDebugUnitTest
      composed       - "assemble$flavor Release", "assemble" + $Flavor + $BuildType

    A name already carrying any module segment is accepted - the gate checks the SHAPE of the name,
    never which module is correct, because only the caller knows that.

    Prose is not a call. A task named in a comment, a comment-based help block, a Write-* message, a
    lock -Reason string, a thrown message or a regex literal is skipped: those describe a task rather
    than pass one to Gradle, and failing them would push authors to stop naming tasks in messages.

.PARAMETER Gate
    Exit 1 on findings. Without it the findings are reported and the exit stays 0, matching every
    sibling in assert-fast-gates.ps1, which supplies -Gate itself.

.PARAMETER ChangedFiles
    Judge only these repo-relative paths. Used by post-change.ps1 -ScopeToFile so another session's
    in-flight work cannot fail this ticket's closure.

.PARAMETER Quiet
    Print only the verdict line, not the per-file scan detail.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-qualified-gradle-tasks.ps1 -Gate

.NOTES
    Exit codes:
      0  PASS, or findings reported without -Gate.
      1  FAIL - at least one unqualified task name, and -Gate was supplied.
      2  Cannot verify - the scan root does not exist.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [string[]]$ChangedFiles,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptsRoot = Join-Path $repoRoot 'scripts'
$workflowsRoot = Join-Path $repoRoot '.github/workflows'

if (-not (Test-Path -LiteralPath $scriptsRoot)) {
    Write-Host "assert-qualified-gradle-tasks: CANNOT VERIFY - scan root not found: $scriptsRoot" -ForegroundColor Yellow
    exit 2
}

# The AGP task verbs whose names carry a variant segment. Four are deliberately absent from the
# .ps1 list, all for one reason: `process`, `compile`, `merge` and `test` name a build OUTPUT
# DIRECTORY as often as a task, so judging them reports paths. `testStandardDebugUnitTest` is the
# report directory every unit-suite consumer reads, and `testStandard` is the source-set name AGP
# derives - measured 2026-08-27, the `test` verb alone produced 9 findings in scripts/ and every one
# of them was a directory. No script entry point passes any of the four to Gradle unqualified, so
# their absence costs no coverage there.
$psVerbs = 'assemble|bundle|install|uninstall|lint|connected'

# S2175: the YAML root judges `test` too. The collision that excludes it from .ps1 scanning does not
# reproduce here - a survey of both live workflow files found zero directory-path strings of that
# shape, and one real unqualified `testStandardDebugUnitTest` call, which is exactly the regression
# this extension exists to catch.
$yamlVerbs = 'assemble|bundle|install|uninstall|lint|connected|test'

# A literal name: verb, a capitalised variant segment, then a build type. `(?<![:\w])` is what accepts
# every already-qualified name - :app_v2:assembleStandardDebug and :wear:bundleStandardRelease alike -
# without the gate having to know which module any given script ought to name.
function New-LiteralPattern([string]$verbs) { "(?<![:\w])($verbs)[A-Z][A-Za-z0-9]*(Debug|Release)" }

# A composed name: a quoted string that opens with the verb and then interpolates or concatenates the
# variant. Its qualified form opens with `:` instead, so this pattern stops matching once fixed.
function New-ComposedPattern([string]$verbs) { "[`"']($verbs)(\`$|[`"']\s*\+)" }

# Each call is parenthesised deliberately - `@(Fn1 $x, Fn2 $x)` is not "two calls, comma-separated";
# PowerShell reads the comma as building one array ARGUMENT for Fn1, silently swallowing Fn2's call
# and folding its name into Fn1's stringified input. Measured while extending this gate (S2175): that
# unparenthesised form produced a single garbled pattern instead of two, and it under-reported real
# findings without erroring.
$psPatterns = @((New-LiteralPattern $psVerbs), (New-ComposedPattern $psVerbs))
$yamlPatterns = @((New-LiteralPattern $yamlVerbs), (New-ComposedPattern $yamlVerbs))

# Contexts that name a task rather than pass one. Matched against the text BEFORE the finding on its
# own line, so a task named inside a message is skipped while a real argument on the same line is not.
# PowerShell-specific (Write-Host, throw, ..) but harmless against YAML - none of these constructs
# appear in workflow files, so the filter simply never fires there.
$prosePattern = '(Write-(Host|Error|Warning|Verbose|Output|Debug|Information)|throw|Add-Stage|Set-Content|-Reason|-Command|-Message|-Pattern|-match|-notmatch|-replace|-cmatch|Select-String)'

$files = if ($ChangedFiles) {
    $ChangedFiles |
        Where-Object { $_ -match '\.ps1$' -or ($_ -match '\.ya?ml$' -and $_ -match '^\.github/workflows/') } |
        ForEach-Object { Join-Path $repoRoot $_ } |
        Where-Object { Test-Path -LiteralPath $_ } |
        ForEach-Object { Get-Item -LiteralPath $_ }
}
else {
    @(Get-ChildItem -LiteralPath $scriptsRoot -Recurse -Include '*.ps1' -File) +
    @(if (Test-Path -LiteralPath $workflowsRoot) {
            Get-ChildItem -LiteralPath $workflowsRoot -Include '*.yml', '*.yaml' -File
        })
}

$findings = @()
$scanned = 0

foreach ($file in $files) {
    # This gate states the forbidden shapes in order to find them, so scanning itself reports itself.
    if ($file.FullName -eq $PSCommandPath) { continue }
    $scanned++

    $isYaml = $file.Extension -in '.yml', '.yaml'
    $patterns = if ($isYaml) { $yamlPatterns } else { $psPatterns }

    $inBlockComment = $false
    $lineNumber = 0

    foreach ($line in (Get-Content -LiteralPath $file.FullName)) {
        $lineNumber++

        # Comment-based help and block comments describe tasks by name constantly. YAML has no <# #>
        # block-comment syntax, so $inBlockComment simply never latches for a .yml/.yaml file.
        if ($line -match '<#') { $inBlockComment = $true }
        if ($inBlockComment) {
            if ($line -match '#>') { $inBlockComment = $false }
            continue
        }
        if ($line.TrimStart().StartsWith('#')) { continue }

        # S2175: a single-invocation CI step can pass several task names on one line
        # (`./gradlew lintStandardDebug testStandardDebugUnitTest assembleStandardDebug ..`), so every
        # non-overlapping match is judged - not just the line's first - via [regex]::Matches rather
        # than the single-match `[regex]::Match` the .ps1-only scanner used before this line could
        # carry more than one task name.
        foreach ($pattern in $patterns) {
            foreach ($match in [regex]::Matches($line, $pattern)) {
                # A trailing `#` comment on a live line, and the prose contexts above, both sit to the
                # LEFT of a finding that is only being described.
                $before = $line.Substring(0, $match.Index)
                if ($before -match '#') { continue }
                if ($before -match $prosePattern) { continue }

                $relative = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
                $findings += [pscustomobject]@{
                    Location = "${relative}:$lineNumber"
                    Task     = $match.Value
                    Line     = $line.Trim()
                }
            }
        }
    }
}

if (-not $Quiet) {
    Write-Host "assert-qualified-gradle-tasks: scanned $scanned file(s)."
}

if ($findings.Count -eq 0) {
    Write-Host 'assert-qualified-gradle-tasks: PASS - every Gradle task name carries its module segment.'
    exit 0
}

$detail = $findings | ForEach-Object { "  $($_.Location)  ->  $($_.Task)`n      $($_.Line)" }
$message = (
    "assert-qualified-gradle-tasks: FAIL - $($findings.Count) unqualified Gradle task name(s).`n" +
    ($detail -join "`n") +
    "`n`n  An unqualified name runs in EVERY project that declares it - :wear declares standard and" +
    "`n  noLegal too (S2090), so a phone task builds the watch and writes outside the Build.Phone" +
    "`n  lock it took. Prefix the module: :app_v2:assembleStandardDebug."
)

# Built above rather than passed inline: assert-exit-contract looks back only 4 lines for the reason,
# so a multi-line Write-Error argument reads as an exit with nothing printed.
Write-Error $message -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
