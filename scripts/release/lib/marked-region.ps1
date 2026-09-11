# marked-region.ps1 - replace the text between a begin and an end marker comment. Dot-source it.
#
# Shared by the two writers of measured Play records: refresh-play-publishing-state.ps1 (S2272, blocks
# 1 and 2 of docs/PLAY_PUBLISHING_STATE.md) and watch-play-vitals.ps1 (S2917, block 4 of the same file
# and section 3.1 of dev/PLAY_QUALITY_THRESHOLDS_2027.md). One implementation, so the two cannot drift
# in how they treat the bytes around a block: everything outside the markers is returned untouched,
# and the body is framed by one blank line on each side in the document's own line ending.

function Set-MarkedRegion {
    param(
        [string] $Text,
        [string] $Begin,
        [string] $End,
        [string[]] $Body,
        [string] $Eol
    )
    $beginIndex = $Text.IndexOf($Begin)
    $endIndex = $Text.IndexOf($End)
    # A missing, reversed or duplicated pair returns $null: the caller refuses rather than guessing
    # which copy of a block is the real one.
    if ($beginIndex -lt 0 -or $endIndex -lt 0 -or $endIndex -lt $beginIndex) {
        return $null
    }
    if ($Text.IndexOf($Begin, $beginIndex + $Begin.Length) -ge 0 -or $Text.IndexOf($End, $endIndex + $End.Length) -ge 0) {
        return $null
    }
    $head = $Text.Substring(0, $beginIndex + $Begin.Length)
    $tail = $Text.Substring($endIndex)
    $middle = $Eol + $Eol + ($Body -join $Eol) + $Eol + $Eol
    return $head + $middle + $tail
}
