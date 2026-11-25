#requires -Version 7.0
<#
.SYNOPSIS
    Refuse a release whose Play artifacts target an API level below the current Play floor of
    their form factor (S4668, contract GOOGLE-PLAY rule 8).

.DESCRIPTION
    Play raises the target-API floor once a year, and on its enforcement date an "App must target
    Android <n>" row turns from a warning into "App updates with these issues will be rejected".
    Rule 8 asks a product to re-read the floors before each release and state them with the date
    read. The floors live in play-target-api-floors.json beside this script, with the date they
    were read; this check prints every floor with that date and judges the targetSdk of each
    artifact shipped to Play against the floor of its form factor:

      phone app   app_v2/build.gradle.kts, flavor standard   -> phone floor
      watch app   wear/build.gradle.kts, flavor standard     -> wear floor
      watch face  watchface/build.gradle.kts, no flavors     -> wear floor

    The vr flavor targets 34 for Meta's band and ships to no Play track, so it is reported and not
    judged; the same holds for every other flavor of a judged module.

    A floor is judged whatever its enforcement date: the floors file records the floor in force
    when it was read, and an artifact uploaded below it carries the warning row onto every track.

    The Wear OS 64-bit native-code requirement is printed as information and never gated: a module's
    ABI set is the union of its dependencies' native libraries, which no Gradle file lists, so only a
    built bundle answers it - not this read of the build files.

    The Gradle files are read, never evaluated, and the parser is narrow on purpose. Comments and
    string-literal contents are blanked first, then:
      - the module must hold exactly one top-level `android { }` block and, inside it, exactly one
        `defaultConfig { }`; productFlavors is optional, its flavors are `create("<name>") { }`;
      - every remaining case-insensitive `targetsdk` token must sit directly inside that
        defaultConfig or directly inside one flavor's create block, as `targetSdk = <integer>`,
        at most once per block;
      - the effective value is the judged flavor's override, else the defaultConfig value.
    Anything else fails closed (exit 2) naming the line: a targetSdk set in a buildType, a variant
    API, a nested block, from a variable or a version catalog; a module with no declaration (where
    it inherits from is not established); a missing judged flavor; unbalanced braces.

.PARAMETER RepoRoot
    The tree whose build files are read. Default: the repository this script lives in.

.PARAMETER FloorsFile
    The floors data file. Default: play-target-api-floors.json beside this script.

.PARAMETER Today
    The date the floors' age is measured against, as yyyy-MM-dd. Default: today. Exists so the
    suite can judge staleness without waiting for it.

.PARAMETER MaxAgeDays
    Floors read more than this many days before -Today are stale. Default: 30.

.PARAMETER Help
    Print this help and exit 0.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-play-target-api-floor.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-play-target-api-floor.ps1 -RepoRoot temp/S4668/tree -Today 2026-09-22

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: every Play artifact meets its floor and the floors were read within -MaxAgeDays.
      1 - fail: a Play artifact's targetSdk is below its form factor's floor - raise it before the
          campaign continues. Wins over 2 and 3, because a found violation is certain.
      2 - could not verify: the floors file or a build file is absent or unreadable, or a targetSdk
          value cannot be established by the parse rules above.
      3 - advisory: every artifact meets its floor, but the floors were read more than -MaxAgeDays
          ago - re-read Play's target API level requirement page and update readOn (and any
          floor that moved) in the floors file.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot,
    [string] $FloorsFile,
    [string] $Today,
    [int] $MaxAgeDays = 30,
    [switch] $Help
)

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
if (-not $FloorsFile) { $FloorsFile = Join-Path $PSScriptRoot 'play-target-api-floors.json' }
$invariant = [System.Globalization.CultureInfo]::InvariantCulture

function ConvertTo-IsoDate {
    param([object] $Value)
    $parsed = [datetime]::MinValue
    if ($Value -is [datetime]) { return $Value.Date }
    if ([datetime]::TryParseExact([string]$Value, 'yyyy-MM-dd', $invariant, [System.Globalization.DateTimeStyles]::None, [ref]$parsed)) {
        return $parsed
    }
    return $null
}

# --- The floors --------------------------------------------------------------------------------
$todayDate = if ($Today) { ConvertTo-IsoDate $Today } else { (Get-Date).Date }
if ($null -eq $todayDate) {
    Write-Host "assert-play-target-api-floor: COULD NOT VERIFY (-Today '$Today' is not a yyyy-MM-dd date)" -ForegroundColor Red
    exit 2
}

$floorsDoc = $null
if (Test-Path -LiteralPath $FloorsFile -PathType Leaf) {
    # -DateKind String keeps readOn as text, so the yyyy-MM-dd check below sees what the file says.
    try { $floorsDoc = [System.IO.File]::ReadAllText($FloorsFile) | ConvertFrom-Json -DateKind String } catch { $floorsDoc = $null }
}
$floorsProblem = ''
if ($null -eq $floorsDoc) { $floorsProblem = "floors file absent or not JSON: $FloorsFile" }
else {
    $names = @($floorsDoc.PSObject.Properties.Name)
    if ($names -notcontains 'readOn' -or $null -eq (ConvertTo-IsoDate $floorsDoc.readOn)) { $floorsProblem = "floors file has no yyyy-MM-dd readOn: $FloorsFile" }
    elseif ($names -notcontains 'floors') { $floorsProblem = "floors file has no floors array: $FloorsFile" }
}
$floorByFactor = @{}
if (-not $floorsProblem) {
    foreach ($f in @($floorsDoc.floors)) {
        $fNames = @($f.PSObject.Properties.Name)
        $complete = ($fNames -contains 'formFactor') -and ($fNames -contains 'targetSdk') -and ($fNames -contains 'enforcedFrom')
        if (-not $complete -or [string]$f.targetSdk -notmatch '^\d+$' -or $null -eq (ConvertTo-IsoDate $f.enforcedFrom)) {
            $floorsProblem = "a floor lacks formFactor, an integer targetSdk or a yyyy-MM-dd enforcedFrom: $FloorsFile"
            break
        }
        $floorByFactor[[string]$f.formFactor] = $f
    }
}
if (-not $floorsProblem) {
    foreach ($needed in @('phone', 'wear')) {
        if (-not $floorByFactor.ContainsKey($needed)) { $floorsProblem = "floors file has no '$needed' floor: $FloorsFile"; break }
    }
}
if ($floorsProblem) {
    Write-Host "assert-play-target-api-floor: COULD NOT VERIFY ($floorsProblem)" -ForegroundColor Red
    exit 2
}

$readOn = ConvertTo-IsoDate $floorsDoc.readOn
$ageDays = [int]($todayDate - $readOn).TotalDays
if ($ageDays -lt 0) {
    Write-Host "assert-play-target-api-floor: COULD NOT VERIFY (readOn $($readOn.ToString('yyyy-MM-dd')) is after $($todayDate.ToString('yyyy-MM-dd')): $FloorsFile)" -ForegroundColor Red
    exit 2
}

$source = if (@($floorsDoc.PSObject.Properties.Name) -contains 'source') { [string]$floorsDoc.source } else { '(no source recorded)' }
Write-Host "play-target-api: floors read $($readOn.ToString('yyyy-MM-dd')) ($ageDays day(s) ago) - $source ($FloorsFile)"
foreach ($f in @($floorsDoc.floors)) {
    $label = if (@($f.PSObject.Properties.Name) -contains 'label') { $f.label } else { $f.formFactor }
    $applies = if (@($f.PSObject.Properties.Name) -contains 'appliesTo') { " for $($f.appliesTo)" } else { '' }
    Write-Host "play-target-api: floor $label targetSdk $($f.targetSdk)$applies, enforced from $((ConvertTo-IsoDate $f.enforcedFrom).ToString('yyyy-MM-dd'))"
}
if (@($floorsDoc.PSObject.Properties.Name) -contains 'information') {
    foreach ($info in @($floorsDoc.information)) {
        $iNames = @($info.PSObject.Properties.Name)
        $label = if ($iNames -contains 'label') { $info.label } else { $info.formFactor }
        $requirement = if ($iNames -contains 'requirement') { $info.requirement } else { '(unnamed requirement)' }
        $from = if ($iNames -contains 'enforcedFrom') { ", enforced from $($info.enforcedFrom)" } else { '' }
        Write-Host "play-target-api: info $label $requirement$from - not gated here: native ABIs come from dependencies, which the build files do not list; only a built bundle answers it"
    }
}

# --- The Gradle reader -------------------------------------------------------------------------
# Comments and string contents are blanked to spaces (newlines and offsets kept), so a commented
# targetSdk or a brace inside a string cannot move the parse. A string keeps its two quotes, so a
# create("name") block is still found and its name read back from the original text.
$script:tokenPattern = [regex]::new('(?s)//[^\n]*|/\*.*?\*/|"""(?:.*?)"""|"(?:\\.|[^"\\\n])*"|''(?:\\.|[^''\\\n])*''')

function Get-KtsMask {
    param([string] $Text)
    return $script:tokenPattern.Replace($Text, {
            param($m)
            $v = $m.Value
            $blank = { param($s) [regex]::Replace($s, '[^\r\n]', ' ') }
            if ($v.StartsWith('"""')) { return '"""' + (& $blank $v.Substring(3, $v.Length - 6)) + '"""' }
            if ($v.StartsWith('"')) { return '"' + (& $blank $v.Substring(1, $v.Length - 2)) + '"' }
            return (& $blank $v)
        })
}

function Get-LineNumber {
    param([string] $Text, [int] $Offset)
    return ([regex]::Matches($Text.Substring(0, $Offset), "`n")).Count + 1
}

function Get-EffectiveTargetSdk {
    <#
        Returns Value (int or $null), Origin, Problem (non-empty = could not verify) and Notes, per
        the parse rules in the script header.
    #>
    param([string] $Path, [string] $Flavor)
    $result = [pscustomobject]@{ Value = $null; Origin = ''; Problem = ''; Notes = [System.Collections.Generic.List[string]]::new() }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { $result.Problem = "build file not found: $Path"; return $result }
    $text = $null
    try { $text = [System.IO.File]::ReadAllText($Path) } catch { $result.Problem = "build file unreadable: $Path ($($_.Exception.Message))"; return $result }
    $mask = Get-KtsMask $text

    $blocks = [System.Collections.Generic.List[object]]::new()
    $stack = [System.Collections.Generic.Stack[int]]::new()
    $openAt = @{}
    foreach ($b in [regex]::Matches($mask, '[{}]')) {
        if ($b.Value -eq '{') { $stack.Push($b.Index); continue }
        if ($stack.Count -eq 0) { $result.Problem = "unbalanced '}' at line $(Get-LineNumber $text $b.Index) of $Path"; return $result }
        $open = $stack.Pop()
        $block = [pscustomobject]@{ Open = $open; Close = $b.Index; Depth = $stack.Count; Kind = ''; Name = '' }
        $blocks.Add($block)
        $openAt[$open] = $block
    }
    if ($stack.Count -gt 0) { $result.Problem = "unbalanced '{' at line $(Get-LineNumber $text $stack.Peek()) of $Path"; return $result }

    $findChildren = {
        param([object] $Parent, [string] $Pattern)
        $lo = if ($Parent) { $Parent.Open + 1 } else { 0 }
        $hi = if ($Parent) { $Parent.Close } else { $mask.Length }
        $depth = if ($Parent) { $Parent.Depth + 1 } else { 0 }
        $found = @()
        foreach ($m in [regex]::Matches($mask.Substring($lo, $hi - $lo), $Pattern)) {
            $brace = $lo + $m.Index + $m.Length - 1
            if ($openAt.ContainsKey($brace) -and $openAt[$brace].Depth -eq $depth) {
                $found += [pscustomobject]@{ Block = $openAt[$brace]; Match = $m; Base = $lo }
            }
        }
        return , $found
    }

    $android = & $findChildren $null '\bandroid\s*\{'
    if ($android.Count -ne 1) { $result.Problem = "expected one top-level android { } block, found $($android.Count) in $Path"; return $result }
    $androidBlock = $android[0].Block
    $defaults = & $findChildren $androidBlock '\bdefaultConfig\s*\{'
    if ($defaults.Count -ne 1) { $result.Problem = "expected one defaultConfig { } inside android { }, found $($defaults.Count) in $Path"; return $result }
    $defaults[0].Block.Kind = 'defaultConfig'
    $flavorBlocks = @{}
    $productFlavors = & $findChildren $androidBlock '\bproductFlavors\s*\{'
    if ($productFlavors.Count -gt 1) { $result.Problem = "expected at most one productFlavors { } inside android { }, found $($productFlavors.Count) in $Path"; return $result }
    if ($productFlavors.Count -eq 1) {
        foreach ($c in (& $findChildren $productFlavors[0].Block '\bcreate\s*\(\s*"(\s*)"\s*\)\s*\{')) {
            $g = $c.Match.Groups[1]
            $name = $text.Substring($c.Base + $g.Index, $g.Length)
            $c.Block.Kind = 'flavor'
            $c.Block.Name = $name
            $flavorBlocks[$name] = $c.Block
        }
    }
    if ($Flavor -and -not $flavorBlocks.ContainsKey($Flavor)) {
        $result.Problem = "flavor '$Flavor' has no create(`"$Flavor`") { } block under android.productFlavors in $Path"
        return $result
    }

    $assigned = @{}
    foreach ($t in [regex]::Matches($mask, '(?i)targetsdk')) {
        $line = Get-LineNumber $text $t.Index
        $owner = $null
        foreach ($blk in $blocks) {
            if ($blk.Open -lt $t.Index -and $t.Index -lt $blk.Close -and ($null -eq $owner -or $blk.Open -gt $owner.Open)) { $owner = $blk }
        }
        $lineStart = $mask.LastIndexOf("`n", [Math]::Max($t.Index - 1, 0)) + 1
        $lineEnd = $mask.IndexOf("`n", $t.Index)
        if ($lineEnd -lt 0) { $lineEnd = $mask.Length }
        $codeLine = $mask.Substring($lineStart, $lineEnd - $lineStart)
        $origLine = $text.Substring($lineStart, $lineEnd - $lineStart).Trim()
        if ($null -eq $owner -or -not $owner.Kind) {
            $result.Problem = "targetSdk at line $line of $Path is outside defaultConfig and every flavor block, so its effect is not established: $origLine"
            return $result
        }
        $assignment = [regex]::Match($codeLine, '^\s*targetSdk\s*=\s*(\d+)\s*$')
        if (-not $assignment.Success) {
            $result.Problem = "targetSdk at line $line of $Path is not a literal 'targetSdk = <integer>' assignment: $origLine"
            return $result
        }
        $key = if ($owner.Kind -eq 'flavor') { "flavor:$($owner.Name)" } else { 'defaultConfig' }
        if ($assigned.ContainsKey($key)) {
            $result.Problem = "targetSdk is assigned twice in $key of $Path (lines $($assigned[$key].Line) and $line)"
            return $result
        }
        $assigned[$key] = [pscustomobject]@{ Value = [int]$assignment.Groups[1].Value; Line = $line }
    }

    foreach ($key in @($assigned.Keys | Sort-Object)) {
        if ($key -like 'flavor:*' -and $key -ne "flavor:$Flavor") {
            $result.Notes.Add("flavor '$($key.Substring(7))' sets targetSdk $($assigned[$key].Value) (line $($assigned[$key].Line)) - not judged: the Play artifact of this module is $(if ($Flavor) { "flavor $Flavor" } else { 'its defaultConfig' })")
        }
    }
    if ($Flavor -and $assigned.ContainsKey("flavor:$Flavor")) {
        $result.Value = $assigned["flavor:$Flavor"].Value
        $result.Origin = "flavor $Flavor override, line $($assigned["flavor:$Flavor"].Line)"
    }
    elseif ($assigned.ContainsKey('defaultConfig')) {
        $result.Value = $assigned['defaultConfig'].Value
        $result.Origin = "defaultConfig, line $($assigned['defaultConfig'].Line)"
    }
    else {
        $result.Problem = "targetSdk not declared in $Path - where it inherits from is not established"
    }
    return $result
}

# --- The artifacts Play receives -----------------------------------------------------------------
$artifacts = @(
    [pscustomobject]@{ Name = 'phone app'; Package = 'com.sza.fastmediasorter'; BuildFile = 'app_v2/build.gradle.kts'; Flavor = 'standard'; FormFactor = 'phone' }
    [pscustomobject]@{ Name = 'watch app'; Package = 'com.sza.fastmediasorter'; BuildFile = 'wear/build.gradle.kts'; Flavor = 'standard'; FormFactor = 'wear' }
    [pscustomobject]@{ Name = 'watch face'; Package = 'com.sza.fastmediasorter.watchface'; BuildFile = 'watchface/build.gradle.kts'; Flavor = ''; FormFactor = 'wear' }
)

$below = [System.Collections.Generic.List[string]]::new()
$unverified = [System.Collections.Generic.List[string]]::new()
foreach ($a in $artifacts) {
    $floor = [int]$floorByFactor[$a.FormFactor].targetSdk
    $flavorText = if ($a.Flavor) { ", flavor $($a.Flavor)" } else { '' }
    $subject = "$($a.Name) ($($a.Package), $($a.BuildFile)$flavorText)"
    $read = Get-EffectiveTargetSdk -Path (Join-Path $RepoRoot $a.BuildFile) -Flavor $a.Flavor
    foreach ($note in $read.Notes) { Write-Host "play-target-api: note $($a.BuildFile) $note" }
    if ($read.Problem) {
        $unverified.Add("$($a.Name): $($read.Problem)")
        Write-Host "play-target-api: $subject targetSdk unknown - $($read.Problem)" -ForegroundColor Red
        continue
    }
    if ($read.Value -lt $floor) {
        $below.Add("$($a.Name) targetSdk $($read.Value) < $($a.FormFactor) floor $floor")
        Write-Host "play-target-api: $subject targetSdk $($read.Value) ($($read.Origin)) - BELOW the $($a.FormFactor) floor ${floor}: raise it before the campaign continues" -ForegroundColor Red
        continue
    }
    Write-Host "play-target-api: $subject targetSdk $($read.Value) ($($read.Origin)) - meets the $($a.FormFactor) floor $floor"
}

# --- Verdict ------------------------------------------------------------------------------------
if ($below.Count -gt 0) {
    $also = if ($unverified.Count -gt 0) { "; also unverified: $($unverified -join '; ')" } else { '' }
    Write-Host "assert-play-target-api-floor: FAIL ($($below -join '; ')$also)" -ForegroundColor Red
    exit 1
}
if ($unverified.Count -gt 0) {
    Write-Host "assert-play-target-api-floor: COULD NOT VERIFY ($($unverified -join '; '))" -ForegroundColor Red
    exit 2
}
if ($ageDays -gt $MaxAgeDays) {
    Write-Host "assert-play-target-api-floor: PASS WITH ADVISORIES (floors read $ageDays day(s) ago, over ${MaxAgeDays}: re-read Play's target API level requirement page and update readOn and any moved floor in $FloorsFile)" -ForegroundColor Yellow
    exit 3
}
Write-Host "assert-play-target-api-floor: PASS ($($artifacts.Count) Play artifact(s) at or above their floor; floors read $ageDays day(s) ago)" -ForegroundColor Green
exit 0
