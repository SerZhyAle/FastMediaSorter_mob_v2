<#
.SYNOPSIS
    Fail when prose or a script's help text claims a flavor count, or a complete flavor list, that
    the generated flavor matrix disagrees with.

.DESCRIPTION
    S2445. assert-flavor-matrix-docs.ps1 compares glyph TABLE cells and, by its own manifest
    (scripts/quality/flavor-matrix-docs.psd1), deliberately does not touch free text. So the
    sentences that state how many flavors exist were watched by nothing, and when S0403 added the
    seventh flavor `foss` they all stayed at six: the public README in three locales, the quick
    start in three locales, the UI communication policy in three locales, four developer documents
    and the help text of a.ps1 - which additionally hid a working `-Flavor Foss` value behind a
    six-name list. This is the second run of the same drift; S1392 recorded the first.

    Two finding classes, both lexical and both resolved against docs/flavors/flavor-matrix.json,
    which is generated from the productFlavors block. The script stores no number of its own, so
    declaring an eighth flavor moves the expectation with it.

      count        a numeral standing next to a flavor noun under an ALL-quantifier ("all six
                   flavors", "во всех шести флейворах", "Seven in total") whose value is not the
                   declared count.
      enumeration  a list that claims to be the complete set - a `-Flavor A|B|C` value list, or a
                   parenthesised name list right after an all-quantifier - that omits or invents a
                   flavor name.

    Only quantified statements are judged. "standard/legacy/noLegal/vr - HLS, DASH VOD" names a
    SUBSET on purpose, and a gate that reported it would be switched off within a week; the whole
    reason the two classes carry a quantifier requirement is that a subset claim is legitimate and
    indistinguishable from a stale complete one without it.

    Deliberately NOT scanned:
      - dev/CHANGELOG.md    a historical journal; every row is true for the date it carries.
      - PLAN/               a spec quotes the wrong text it was written to correct.
      - dev/archive/        read-only zone (CLAUDE.md Rule 4).

.PARAMETER Gate
    Exit non-zero on a finding. Without it the script reports and exits 0, which is the read-only
    mode used while correcting documents.

.PARAMETER Quiet
    Suppress the per-run progress lines; the verdict line and any finding rows still print. This is
    what assert-fast-gates.ps1 passes when it runs the gate as part of the batch.

.EXIT CODES
    0 - every quantified flavor claim matches the matrix (or findings exist without -Gate).
    1 - at least one claim contradicts the matrix, and -Gate was passed.
    2 - could not verify: docs/flavors/flavor-matrix.json is absent or declares no flavors, so
        nothing was compared.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-flavor-count-prose.ps1
    pwsh -NoProfile -File scripts/quality/assert-flavor-count-prose.ps1 -Gate -Quiet
#>
param([switch]$Gate, [switch]$Quiet)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$matrixPath = Join-Path $repoRoot 'docs/flavors/flavor-matrix.json'
if (-not (Test-Path -LiteralPath $matrixPath)) {
    Write-Error "assert-flavor-count-prose: docs/flavors/flavor-matrix.json is absent - nothing was compared. Run scripts/docs/generate-flavor-matrix.ps1." -ErrorAction Continue
    exit 2
}

$declaredFlavors = @((Get-Content -LiteralPath $matrixPath -Raw | ConvertFrom-Json).flavors)
if ($declaredFlavors.Count -eq 0) {
    Write-Error "assert-flavor-count-prose: flavor-matrix.json declares no flavors - nothing was compared." -ErrorAction Continue
    exit 2
}
$expectedCount = $declaredFlavors.Count
$declaredLookup = [System.Collections.Generic.HashSet[string]]::new(
    [string[]]$declaredFlavors, [System.StringComparer]::OrdinalIgnoreCase)

# Numerals as they are actually written in the three authored locales. Only the range a flavor set
# can plausibly reach is listed: a number outside it is not a flavor count, it is a coincidence.
$numerals = @{}
$english = @('two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten')
for ($i = 0; $i -lt $english.Count; $i++) { $numerals[$english[$i]] = $i + 2 }
$slavic = @{
    'двух' = 2; 'две' = 2; 'два' = 2; 'двох' = 2; 'дві' = 2
    'трёх' = 3; 'трех' = 3; 'три' = 3; 'трьох' = 3
    'четырёх' = 4; 'четырех' = 4; 'четыре' = 4; 'чотирьох' = 4; 'чотири' = 4
    'пяти' = 5; 'пять' = 5; "п'яти" = 5; "п'ять" = 5
    'шести' = 6; 'шесть' = 6; 'шість' = 6
    'семи' = 7; 'семь' = 7; 'сім' = 7
    'восьми' = 8; 'восемь' = 8; 'вісім' = 8; 'вісьмох' = 8
    'девяти' = 9; 'девять' = 9; "дев'ять" = 9
}
foreach ($k in $slavic.Keys) { $numerals[$k] = $slavic[$k] }
for ($i = 2; $i -le 10; $i++) { $numerals["$i"] = $i }

$numeralAlternatives = ($numerals.Keys | Sort-Object -Property Length -Descending |
    ForEach-Object { [regex]::Escape($_) }) -join '|'
$flavorNoun = "flavou?rs?|флейвор\w*|верси\w*|версі\w*|збірк\w*|сборк\w*"
$allQuantifier = "all|every|всех|всех|все|усіх|усі|в усіх|во всех"
$totalMarker = "in total|всего|усього|разом"

# A numeral under an all-quantifier: "all six flavors", "во всех шести флейворах", "в семи версиях".
$countUnderQuantifier = "(?i)\b(?:$allQuantifier)\b[^.;:]{0,20}?\b($numeralAlternatives)\b\s*(?:$flavorNoun)"
# A numeral followed by a total marker: "Six in total", "six flavors in total", "Усього сім". The
# noun is optional between the two because both orders occur in the authored text.
$countBeforeTotal = "(?i)\b($numeralAlternatives)\b(?:\s+(?:$flavorNoun))?\s+(?:$totalMarker)\b"
$countAfterTotal = "(?i)\b(?:$totalMarker)\b\s+($numeralAlternatives)\b"

# A -Flavor value list. Three or more alternatives means it is presented as the whole accepted set;
# `-Flavor Lite` on its own names one value and claims nothing.
$flavorValueList = "(?i)-Flavor\s+([A-Za-z]+(?:\s*\|\s*[A-Za-z]+){2,})"
# A parenthesised name list introduced by an all-quantifier plus a flavor noun on the same line.
$enumeratedUnderQuantifier = "(?i)\b(?:$allQuantifier)\b[^.;:(]{0,40}?(?:$flavorNoun)[^(]{0,20}\(([^)]{0,300})\)"

. (Join-Path $PSScriptRoot 'lib/nested-worktrees.ps1')
$nestedWorktrees = Get-NestedWorktreeRelativePath -RepoRoot $repoRoot
$skippedWorktreeFiles = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)

# Scope is the set of surfaces the ticket cleaned, declared here rather than in a side manifest:
# the gate has no baseline, so a file listed here is a file that is clean right now.
$scanRoots = @('docs', 'dev')
$scanExtensions = @('.md')
$scanRootFiles = @('README.md', 'a.ps1')
$excludedPaths = @('dev/CHANGELOG.md', 'dev/archive')

function Test-Excluded {
    param([Parameter(Mandatory)][string]$RelativePath)
    $normalized = $RelativePath.Replace('\', '/')
    # Counted rather than merely skipped: the verdict line has to be able to say the walk dropped a
    # subtree, or "0 findings" reads the same whether it looked or not.
    if (Test-InNestedWorktree -RelativePath $normalized -Prefixes $nestedWorktrees) {
        [void]$skippedWorktreeFiles.Add($normalized)
        return $true
    }
    foreach ($ex in $excludedPaths) {
        if ($normalized -eq $ex -or $normalized.StartsWith("$ex/")) { return $true }
    }
    return $false
}

function Get-ScanFile {
    $files = @()
    foreach ($root in $scanRoots) {
        $rootPath = Join-Path $repoRoot $root
        if (-not (Test-Path -LiteralPath $rootPath)) { continue }
        $files += @(Get-ChildItem -LiteralPath $rootPath -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $scanExtensions -contains $_.Extension })
    }
    foreach ($name in $scanRootFiles) {
        $path = Join-Path $repoRoot $name
        if (Test-Path -LiteralPath $path) { $files += Get-Item -LiteralPath $path }
    }
    return $files
}

$findings = @()
$scanned = 0

function Add-Finding {
    param([string]$File, [int]$Line, [string]$Class, [string]$Claimed, [string]$Text)
    $script:findings += [pscustomobject]@{
        File = $File; Line = $Line; Class = $Class; Claimed = $Claimed; Text = $Text
    }
}

function Test-NameList {
    <#
        Splits a candidate list on the separators the repository actually uses and returns the
        declared flavor names it carries. Returns $null when fewer than three names are present,
        which is the point below which a list is a pair or a phrase, not a claim about the set.
    #>
    param([Parameter(Mandatory)][string]$Text)
    $tokens = @($Text -split '[|,/]+' | ForEach-Object { $_.Trim().Trim('`', '*', ' ', '.') } |
        Where-Object { $_ -match '^[A-Za-z]+$' })
    $known = @($tokens | Where-Object { $declaredLookup.Contains($_) })
    if ($known.Count -lt 3) { return $null }
    return $known
}

foreach ($file in Get-ScanFile) {
    $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/').Replace('\', '/')
    if (Test-Excluded -RelativePath $rel) { continue }
    $scanned++
    $lineNumber = 0
    foreach ($line in (Get-Content -LiteralPath $file.FullName -ErrorAction SilentlyContinue)) {
        $lineNumber++
        foreach ($pattern in @($countUnderQuantifier, $countBeforeTotal, $countAfterTotal)) {
            foreach ($m in [regex]::Matches($line, $pattern)) {
                $claimed = $numerals[$m.Groups[1].Value.ToLowerInvariant()]
                if ($claimed -eq $expectedCount) { continue }
                Add-Finding -File $rel -Line $lineNumber -Class 'count' `
                    -Claimed "$claimed" -Text $line.Trim()
            }
        }
        foreach ($pattern in @($flavorValueList, $enumeratedUnderQuantifier)) {
            foreach ($m in [regex]::Matches($line, $pattern)) {
                $named = Test-NameList -Text $m.Groups[1].Value
                if ($null -eq $named) { continue }
                $missing = @($declaredFlavors | Where-Object { $named -notcontains $_ })
                if ($missing.Count -eq 0) { continue }
                Add-Finding -File $rel -Line $lineNumber -Class 'enumeration' `
                    -Claimed ("omits " + ($missing -join ', ')) -Text $line.Trim()
            }
        }
    }
}

$skipNotice = Get-NestedWorktreeSkipNotice -SkippedFileCount $skippedWorktreeFiles.Count -Prefixes $nestedWorktrees
if ($skipNotice) { Write-Host "assert-flavor-count-prose: $skipNotice" }

if (-not $Quiet) {
    Write-Host ("assert-flavor-count-prose: the matrix declares {0} flavors ({1})" -f $expectedCount, ($declaredFlavors -join ', '))
}
Write-Host ("assert-flavor-count-prose: expected: 0 | actual: {0} stale claim(s) across {1} scanned file(s)" -f $findings.Count, $scanned)

if ($findings.Count -eq 0) {
    Write-Host "assert-flavor-count-prose: PASS - every quantified flavor claim matches the matrix." -ForegroundColor Green
    exit 0
}

foreach ($f in $findings) {
    Write-Host ("  {0}:{1}  [{2}] {3} (matrix declares {4})" -f $f.File, $f.Line, $f.Class, $f.Claimed, $expectedCount) -ForegroundColor Red
    Write-Host ("      {0}" -f $f.Text) -ForegroundColor DarkGray
}

if (-not $Gate) {
    Write-Host "assert-flavor-count-prose: reporting only (-Gate not passed)." -ForegroundColor Yellow
    exit 0
}

Write-Error "assert-flavor-count-prose: FAIL - $($findings.Count) stale flavor claim(s). Correct the text, or drop the number and defer to docs/FLAVOR_MATRIX.md; the build file is the source." -ErrorAction Continue
exit 1
