#requires -Version 7.0
<#
.SYNOPSIS
    S1282: a values-land / values-w600dp resource that no device can ever resolve.

.DESCRIPTION
    Android resolves configuration qualifiers in a fixed precedence order. smallestWidth (4th)
    outranks available width (5th), which outranks orientation (12th). So a key declared in both
    values-swNNNdp and values-land loses in values-land on every device at or above that threshold -
    silently, with no build error and no lint warning. S1282 shipped exactly that: eleven of the
    twenty-four keys in values-land/dimens.xml had never applied anywhere, and a byte-identical copy
    of that file under values-w600dp was handing the same values to tablets in portrait instead.

    The remedy is a combined bucket (values-swNNNdp-land), which ties the smallestWidth score and
    then wins on orientation.

    What this gate fails on, and why the test is narrow:

      values-sw320dp matches EVERY device, so a key declared there - or in its combined sibling
      values-sw320dp-land - outranks the values-land / values-w600dp copy on every configuration
      that exists. That copy is dead, full stop, and that is the S1282 defect.

      A key shadowed only at a HIGHER threshold (480, 600, 720) is a different thing: the landscape
      value still wins below that threshold, and the sw value wins above it. That is a deliberate
      phone-versus-tablet split - exactly what S1282 strategic section 6 chose - so failing on it
      would fail on the intended design. Known limitation: a value that dies only above 480dp is
      therefore not caught. Judging that case needs a human, which is why it is not a gate.

    Accepted entries live in qualifier-shadowing-baseline.txt (one "<file>|<bucket>|<key>" per line,
    # comments allowed), the same ratchet shape the other gates in this folder use.

.OUTPUTS
    Exit 0 - no unbaselined dead declaration.
    Exit 1 - at least one dead declaration outside the baseline (listed per bucket and file).
    Exit 2 - the gate cannot run (resource root missing).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-qualifier-shadowing.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$resRoot = Join-Path $repoRoot 'app_v2/src/main/res'

if (-not (Test-Path $resRoot)) {
    Write-Error "assert-qualifier-shadowing: cannot run - resource root missing: $resRoot" -ErrorAction Continue
    exit 2
}

# Buckets that rank BELOW smallestWidth and therefore lose to it.
$shadowableBuckets = @('values-land', 'values-w600dp')

# The universal threshold: every device matches it, so a declaration here beats the buckets above
# on every configuration. Its combined sibling wins the same comparison and then takes orientation.
$universalBuckets = @('values-sw320dp', 'values-sw320dp-land')

$baselinePath = Join-Path $PSScriptRoot 'qualifier-shadowing-baseline.txt'
$baseline = @()
if (Test-Path $baselinePath) {
    $baseline = @(Get-Content $baselinePath | ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith('#') })
}

function Get-ResourceNames {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return @() }
    [xml]$doc = Get-Content -LiteralPath $Path -Encoding utf8
    # Element nodes only - an XML comment also exposes a Name ("#comment") and would be counted.
    return @($doc.resources.ChildNodes |
        Where-Object { $_.NodeType -eq 'Element' -and $_.name } |
        ForEach-Object { $_.name })
}

$violations = [System.Collections.Generic.List[string]]::new()
$checked = 0

foreach ($bucket in $shadowableBuckets) {
    $bucketDir = Join-Path $resRoot $bucket
    if (-not (Test-Path $bucketDir)) { continue }

    foreach ($file in Get-ChildItem $bucketDir -Filter *.xml) {
        foreach ($key in (Get-ResourceNames $file.FullName)) {
            $checked++
            $shadowedBy = @($universalBuckets | Where-Object {
                (Get-ResourceNames (Join-Path $resRoot "$_/$($file.Name)")) -contains $key
            })
            if ($shadowedBy.Count -eq 0) { continue }

            $entry = "$($file.Name)|$bucket|$key"
            if ($baseline -contains $entry) { continue }

            $verb = ($shadowedBy.Count -eq 1) ? 'declares' : 'declare'
            $violations.Add("$entry - dead: $($shadowedBy -join ' and ') $verb it and match every device")
        }
    }
}

if ($violations.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-qualifier-shadowing: PASS ($checked declaration(s) checked, $($baseline.Count) baselined)"
    }
    exit 0
}

Write-Host 'assert-qualifier-shadowing: FAIL - declarations no device can resolve:'
foreach ($v in $violations) { Write-Host "  $v" }
Write-Host '  smallestWidth outranks orientation, and values-sw320dp matches every device.'
Write-Host '  Fix: move the value into the matching values-swNNNdp-land bucket, or drop the declaration.'
exit 1
