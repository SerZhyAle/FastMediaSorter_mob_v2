#requires -Version 7.0
<#
.SYNOPSIS
    S4256 - record the address and the section anchors of every page a release publishes into
    docs/site-released-addresses.jsonl.

.DESCRIPTION
    Contract SITE-STRUCTURE 0.2 section 8 item D: from the release that first carries a page, its address
    and its anchors do not change. This writer takes the snapshot that scripts/quality/assert-released-addresses.ps1
    later holds the tree to. Run by /skill-release Step 5d before the docs commit, so the record is committed
    in the release it describes. A page is a published Markdown or HTML address that the tree's sitemap.xml
    announces; the contributor files Jekyll also serves (docs/DEV_OPS.md and the like) are not offered to any
    outside surface and stay free to change.

    The record only grows. A page already in it keeps its first release and gains any anchor it did not have;
    a new page is added with -Release; a page that left the site keeps its line, because a released address
    stays released after its page retires (the gate then wants a retirement forwarder for it).

.PARAMETER Release
    Version name of the release being cut, written into the line of every page it carries for the first time.

.PARAMETER SiteRoot
    Tree to snapshot. Defaults to this repository; the release worktree is the one to name when seeding the
    record from a release already tagged.

.PARAMETER RecordRoot
    Repository whose docs/site-released-addresses.jsonl is written. Defaults to this repository; overridable
    so the tests can judge a synthetic site.

.PARAMETER Check
    Compute the merge and write nothing. Exit 1 when the record would change.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/record-released-addresses.ps1 -Release 2.62.0150.100

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/record-released-addresses.ps1 -Release 2.61.0141.503 -SiteRoot P:/ANDROID/FastMediaSorter_release

.NOTES
    Exit codes:
      0  the record was written (or, with -Check, is already current)
      1  -Check: the record would gain a page or an anchor
      2  invalid input: the release is empty, a tree is missing or the record is unreadable
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Release,
    [string] $SiteRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [string] $RecordRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [switch] $Check
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/site-addresses.ps1')
. (Join-Path $PSScriptRoot 'lib/released-addresses.ps1')

if ([string]::IsNullOrWhiteSpace($Release)) {
    Write-Host 'record-released-addresses: -Release is empty.' -ForegroundColor Red
    exit 2
}
foreach ($dir in $SiteRoot, $RecordRoot) {
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
        Write-Host "record-released-addresses: tree not found: $dir" -ForegroundColor Red
        exit 2
    }
}

$recordPath = Join-Path $RecordRoot $script:ReleasedRecordPath
$existing = [ordered]@{}
try {
    foreach ($record in (Read-JsonlRecords -Path $recordPath)) {
        $existing[[string]$record.address] = [pscustomobject]@{ Release = [string]$record.release; Anchors = @($record.anchors) }
    }
}
catch {
    Write-Host "record-released-addresses: $($script:ReleasedRecordPath) is unreadable - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

$snapshot = Get-ReleasedPageSnapshot -RepoRoot $SiteRoot
if ($null -eq $snapshot) {
    Write-Host "record-released-addresses: $SiteRoot has no sitemap.xml - the page set cannot be read." -ForegroundColor Red
    exit 2
}
$addedPages = 0
$addedAnchors = 0
foreach ($address in $snapshot.Keys) {
    $anchors = @($snapshot[$address].Anchors)
    if (-not $existing.Contains($address)) {
        $existing[$address] = [pscustomobject]@{ Release = $Release; Anchors = $anchors }
        $addedPages++
        continue
    }
    $known = @($existing[$address].Anchors)
    $new = @($anchors | Where-Object { $_ -cnotin $known })
    if ($new.Count -gt 0) {
        $existing[$address].Anchors = @($known + $new)
        $addedAnchors += $new.Count
    }
}

$lines = foreach ($address in (ConvertTo-OrdinalSortedArray ([string[]]@($existing.Keys)))) {
    ConvertTo-ReleasedRecordLine -Address $address -Release $existing[$address].Release -Anchors $existing[$address].Anchors
}
$text = (($lines -join "`n") + "`n")
$current = if (Test-Path -LiteralPath $recordPath) { [IO.File]::ReadAllText($recordPath) } else { '' }

if ($Check) {
    if ($text -ceq $current) {
        Write-Host "record-released-addresses: current ($($existing.Count) page(s))."
        exit 0
    }
    Write-Host "record-released-addresses: the record would gain $addedPages page(s) and $addedAnchors anchor(s)." -ForegroundColor Yellow
    exit 1
}

if ($text -cne $current) { [IO.File]::WriteAllText($recordPath, $text, [Text.UTF8Encoding]::new($false)) }
Write-Host "record-released-addresses: $($existing.Count) page(s); added $addedPages page(s) and $addedAnchors anchor(s) for $Release."
exit 0
