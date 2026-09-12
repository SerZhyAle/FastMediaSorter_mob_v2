<#
.SYNOPSIS
    Fail when a Kotlin primary constructor approaches the 255 argument-slot ceiling.

.DESCRIPTION
    S1470. AppSettings grew one property per ticket until its synthetic all-defaults constructor
    reached 256 argument slots against the hard 255 of JVMS 4.3.3 and dex. Nothing caught it:
    kotlinc and D8 both emit the class happily, and only the runtime verifier rejects it - so the
    build was green, every static gate passed, and the app died at Hilt injection on startup with
    a VerifyError. On the JVM side the same class made mockk fail with ClassFormatError, which
    killed whole test workers and silently truncated the unit suite.

    The count that matters is not the number of properties. For a data class Kotlin emits two
    synthetic members that carry every parameter at once:
      - <init>$default  = params + wide extras + ceil(params/32) mask ints + DefaultConstructorMarker
      - copy$default    = the same, plus the instance argument
    A long or double occupies two slots, and an instance method counts `this`. So a class can pass
    review at 239 readable properties and still be two slots over the line.

    This gate does that arithmetic on the source, before the build, and reports the remaining
    headroom in whole properties - the number a reviewer actually needs when adding a field.

    Parsing is deliberately narrow: only a primary constructor whose parameter list opens at the end
    of the class line and closes on a line starting with `)`. That is the shape every large model in
    this repository uses, and a shape it cannot parse is skipped rather than guessed at, because a
    wrong slot count would be worse than no count.

.PARAMETER Gate
    Exit non-zero when a class is over the ceiling. Without it the script reports and exits 0, so it
    can be run for information without failing a caller.

.PARAMETER WarnAt
    Headroom, in properties, below which a class is reported as approaching the ceiling. Default 20 -
    roughly a quarter's worth of growth at this repository's observed rate of one field per ticket.

.PARAMETER ChangedFiles
    S2537. The changed set, as ONE comma-joined argument (`pwsh -File` binds a [string[]] parameter
    to its first element only, which is why every consumer in this repo comma-splits - S1184). A
    constructor can only cross the ceiling through a changed Kotlin file, so a scoped run measures
    exactly those and skips the rest of the tree. An unscoped run keeps the strict project-wide
    verdict, which is what assert-fast-gates.ps1 and a release run get.

    Why it exists: measured over the gate journal for 2026-08-24..2026-09-04, this gate cost a 5.1 s
    median on each of 344 closures and found nothing in any of them, because it walked both source
    trees every time. Its sibling assert-listener-symmetry.ps1 costs 12.4 s over the tree and 0.69 s
    over a changed set - the same twentyfold difference this parameter buys here.

.PARAMETER Quiet
    Suppress per-run progress lines; the verdict line and any offending rows still print. This is
    what assert-fast-gates.ps1 passes when it runs the gate as part of the batch.

.EXIT CODES
    0 - every parsed constructor is under the ceiling (or over it without -Gate).
    1 - at least one constructor exceeds the ceiling, and -Gate was passed.
    2 - could not verify: no source root was readable, so nothing was measured.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-ctor-arg-slots.ps1
    pwsh -NoProfile -File scripts/quality/assert-ctor-arg-slots.ps1 -Gate
#>
param(
    [switch]$Gate,
    [int]$WarnAt = 20,
    [switch]$Quiet,
    [string]$ChangedFiles
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# JVMS 4.3.3 and the dex invoke-range encoding share this limit, so one number covers both.
$SlotCeiling = 255
$MaskWidth = 32
$WideTypes = @('Long', 'Double')

# Only classes big enough to be anywhere near the ceiling are worth measuring; below this a class
# cannot reach it even with every parameter wide, so parsing it would only add noise and runtime.
$MinParamsToMeasure = 60

function Measure-PrimaryConstructor {
    <#
        Returns the worst-case synthetic slot count for one class, or $null when the shape is not
        the one this gate can measure. Worst case is copy$default for a data class - it carries the
        instance argument on top of everything <init>$default carries.
    #>
    param(
        # Not Mandatory on purpose: a file that is one blank line binds as an empty string, and the
        # mandatory check rejects that outright instead of letting the body skip it.
        [AllowEmptyCollection()][string[]]$Lines = @(),
        [Parameter(Mandatory)][int]$DeclarationIndex,
        [Parameter(Mandatory)][bool]$IsDataClass
    )

    $params = 0
    $wide = 0
    $closed = $false
    for ($i = $DeclarationIndex + 1; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match '^\)') { $closed = $true; break }
        if ($Lines[$i] -match '^\s+(?:@\w+(?:\([^)]*\))?\s+)*(?:val|var)\s+\w+\s*:\s*([A-Za-z0-9_.<>? ]+)') {
            $params++
            $type = $Matches[1].Trim().TrimEnd('?')
            if ($WideTypes -contains $type) { $wide++ }
        }
    }
    if (-not $closed -or $params -lt $MinParamsToMeasure) { return $null }

    $valueSlots = $params + $wide
    $masks = [math]::Ceiling($params / $MaskWidth)
    # +1 DefaultConstructorMarker, +1 for the instance argument (this on <init>$default, the copied
    # receiver on copy$default) - both synthetics pay it, so the arithmetic is the same either way.
    $worst = $valueSlots + $masks + 2
    return [pscustomobject]@{
        Params = $params
        Wide = $wide
        WorstSlots = $worst
        Headroom = $SlotCeiling - $worst
        Kind = if ($IsDataClass) { 'data class' } else { 'class' }
    }
}

$roots = @('app_v2/src', 'wear/src') | ForEach-Object { Join-Path $repoRoot $_ } |
    Where-Object { Test-Path -LiteralPath $_ }
if ($roots.Count -eq 0) {
    Write-Error "assert-ctor-arg-slots: no Kotlin source root found under $repoRoot" -ErrorAction Continue
    exit 2
}

# S2537: resolve the named files directly rather than walking both trees to discard all but a few.
$changedSet = @()
foreach ($entry in @($ChangedFiles)) {
    $changedSet += @($entry -split ',' | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })
}
$scoped = $changedSet.Count -gt 0
$filesToMeasure = if ($scoped) {
    @($changedSet |
        Where-Object { $_ -like '*.kt' } |
        ForEach-Object { Join-Path $repoRoot $_ } |
        Where-Object { Test-Path -LiteralPath $_ } |
        ForEach-Object { Get-Item -LiteralPath $_ })
}
else {
    @(foreach ($root in $roots) { Get-ChildItem -LiteralPath $root -Recurse -Filter '*.kt' -File })
}

if ($scoped -and $filesToMeasure.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-ctor-arg-slots: PASS (scoped to the changed set - no Kotlin file in it)." -ForegroundColor Green
    }
    exit 0
}

$measured = @()
foreach ($file in $filesToMeasure) {
    # @() matters: Get-Content returns a bare string for a one-line file and $null for an empty
    # one, and both break the [string[]] parameter below.
    $lines = @(Get-Content -LiteralPath $file.FullName)
    if ($lines.Count -eq 0) { continue }
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -notmatch '^(?:internal |private |public |abstract |sealed |open )*(data )?class\s+(\w+)[^(]*\($') {
            continue
        }
        $isData = [bool]$Matches[1]
        $className = $Matches[2]
        $result = Measure-PrimaryConstructor -Lines $lines -DeclarationIndex $i -IsDataClass $isData
        if ($null -eq $result) { continue }
        $measured += [pscustomobject]@{
            Class = $className
            File = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
            Params = $result.Params
            WorstSlots = $result.WorstSlots
            Headroom = $result.Headroom
            Kind = $result.Kind
        }
    }
}

if ($measured.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-ctor-arg-slots: PASS (no constructor with $MinParamsToMeasure+ parameters)." -ForegroundColor Green
    }
    exit 0
}

$over = @($measured | Where-Object { $_.Headroom -lt 0 })
$near = @($measured | Where-Object { $_.Headroom -ge 0 -and $_.Headroom -lt $WarnAt })

if (-not $Quiet) {
    foreach ($m in $measured | Sort-Object Headroom) {
        Write-Host ("  {0,-28} {1,4} params -> {2,4} slots, headroom {3,4} field(s)  {4}" -f
            $m.Class, $m.Params, $m.WorstSlots, $m.Headroom, $m.File)
    }
}

foreach ($m in $near) {
    Write-Host ("assert-ctor-arg-slots: WARN {0} is {1} field(s) from the {2}-slot ceiling ({3})." -f
        $m.Class, $m.Headroom, $SlotCeiling, $m.File) -ForegroundColor Yellow
}

if ($over.Count -gt 0) {
    foreach ($m in $over) {
        Write-Host ("assert-ctor-arg-slots: FAIL {0} needs {1} slots, {2} over the ceiling ({3})." -f
            $m.Class, $m.WorstSlots, [math]::Abs($m.Headroom), $m.File) -ForegroundColor Red
    }
    Write-Host "  Fold a coherent family of properties into a nested data class - see S1470." -ForegroundColor Yellow
    if ($Gate) {
        Write-Error "assert-ctor-arg-slots: $($over.Count) constructor(s) over the $SlotCeiling-slot ceiling." -ErrorAction Continue
        exit 1
    }
    exit 0
}

Write-Host ("assert-ctor-arg-slots: PASS ({0} large constructor(s) measured, smallest headroom {1} field(s))." -f
    $measured.Count, ($measured | Measure-Object -Property Headroom -Minimum).Minimum) -ForegroundColor Green
exit 0
