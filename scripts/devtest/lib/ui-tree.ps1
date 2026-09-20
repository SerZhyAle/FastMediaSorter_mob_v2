<#
.SYNOPSIS
  uiautomator node-tree parsing and display-shape geometry, shared by adb.ps1 and its test suite.

.DESCRIPTION
  Pure functions: no adb call, no device, no writes, no dependency on adb.ps1's own Fail/Invoke-Adb.
  That is the point - the clip-check classification is the non-obvious part of S1847, and it is
  calibrated against recorded dumps by scripts/devtest/adb-clip-check.tests/Run-Tests.ps1, which can
  only dot-source these if they live outside a script that runs verbs on load. Same reason
  lib/find-adb.ps1 exists (S1341) and lib/adb-log-filter.ps1 exists (S1332).

  The resource-id match rule used by `tap-id` is pinned the same way, by
  scripts/devtest/adb-tap-id.tests/Run-Tests.ps1 (S1879).

  Sourced, never executed directly, so it declares no exit codes of its own.
#>
function Add-UiNodes {
    param($Parent, [bool]$AncestorScrollable, $Acc)
    foreach ($child in $Parent.ChildNodes) {
        if ($child.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
        $bounds = $child.GetAttribute('bounds')
        $m = [regex]::Match($bounds, '^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$')
        if ($m.Success) {
            $nodeText = $child.GetAttribute('text')
            $nodeDesc = $child.GetAttribute('content-desc')
            # A node named ONLY by its resource-id is collected too (S1879). It is invisible to
            # tap-label by construction, and it is exactly what a switch or an icon looks like.
            $nodeId   = $child.GetAttribute('resource-id')
            if ($nodeText -or $nodeDesc -or $nodeId) {
                $x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
                $x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
                $idSep = $nodeId.IndexOf(':id/')
                $Acc.Add([ordered]@{
                    label  = if ($nodeText) { $nodeText } elseif ($nodeDesc) { $nodeDesc } else { '' }
                    source = if ($nodeText) { 'text' } elseif ($nodeDesc) { 'desc' } else { 'id' }
                    # Whether the node carries a HUMAN-READABLE name. clip-check judges only these,
                    # so widening the collection above cannot move its calibrated verdicts.
                    labelled = [bool]($nodeText -or $nodeDesc)
                    text   = $nodeText
                    desc   = $nodeDesc
                    resId  = $nodeId
                    # The part a layout actually writes. The full value carries the package, and the
                    # debug build's package ends in .debug - a call pinned to it breaks on release.
                    resIdShort = if ($idSep -ge 0) { $nodeId.Substring($idSep + 4) } else { '' }
                    class  = $child.GetAttribute('class')
                    # A node with element children is a container: its box is the group's extent,
                    # not the extent of anything the user can see. clip-check judges leaves only.
                    leaf   = ($child.SelectNodes('node').Count -eq 0)
                    x1 = $x1; y1 = $y1; x2 = $x2; y2 = $y2
                    tapX = [int](($x1 + $x2) / 2); tapY = [int](($y1 + $y2) / 2)
                    scrollAncestor = $AncestorScrollable
                }) | Out-Null
            }
        }
        Add-UiNodes $child ($AncestorScrollable -or ($child.GetAttribute('scrollable') -eq 'true')) $Acc
    }
}

function Get-UiNodes {
    param($Tree)
    $acc = New-Object System.Collections.Generic.List[object]
    Add-UiNodes $Tree.DocumentElement $false $acc
    return $acc
}

# Nodes whose resource-id matches, in document order (S1879).
#
# Two forms are accepted because neither alone is usable. The full value is package-qualified, and
# the debug package ends in .debug, so a call written against one build misses on the other; the
# short form is what the layout writes and travels between builds. Default matching is a
# case-insensitive substring - `rowExport` therefore also reaches `rowExportAll`, and -Exact is how
# the caller says the two must not be confused. That confusion IS the failure tap-label exists to
# prevent, so it is the caller's decision to make, not a default to guess at.
#
# Like Get-UiNodes above, the caller wraps the result in @() before reading .Count: a single match
# returned bare is one hashtable, whose .Count is its field count, not 1.
function Select-UiNodesById {
    param($Nodes, [string]$Wanted, [switch]$Exact)
    $hits = New-Object System.Collections.Generic.List[object]
    foreach ($n in $Nodes) {
        if (-not $n.resId) { continue }
        $match = if ($Exact) {
            $n.resId -eq $Wanted -or $n.resIdShort -eq $Wanted
        } else {
            $n.resId.IndexOf($Wanted, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
        }
        if ($match) { $hits.Add($n) | Out-Null }
    }
    return $hits
}

# Decide which corner radius the geometry below may be measured against (S3357). Pure: the caller
# owns every adb call and hands the readings in.
#
# A corner radius wider than half the shorter side describes no shape at all - the four corner arcs
# would have to overlap, and the quadrant boundaries in Get-CornerOverflow cross over, so EVERY
# point on the display lands in all four quadrants and is measured against an arc centre that is
# nowhere near it. Measured 2026-09-20 on the Wear_OS_Small_Round AVD, which reports radius=240 on a
# 384x384 round display (240 is half of 480, the size a different watch has): the whole
# /spec-prerelease-wear walk came back with five OFF-GLASS screens, and every one of them sits
# inside the real glass - the mini-game board is the exact inscribed square. That is the mirror
# image of the S2273 failure: manufacturing release blockers is as bad as missing them.
#
# An impossible reading carries no information, so a square watch display falls back to the same
# assumption the no-data branch already makes - the inscribed circle is the only round shape a
# square glass can have. Anywhere else the reading is refused as untrusted, because there is no
# second source to correct it from and a guessed outline would judge the app against an invented
# screen.
function Resolve-DisplayRadius {
    param([int]$Width, [int]$Height, [int[]]$Radii, [bool]$IsRoundWatch)

    $shorter = [math]::Min($Width, $Height)
    $inscribed = [int][math]::Floor($shorter / 2)

    $reported = 0
    # Equal on every real device seen so far; the maximum is the conservative reading when they
    # differ, because it is the one that shrinks the safe area rather than growing it.
    if ($null -ne $Radii -and $Radii.Count -ge 4) { $reported = ($Radii | Measure-Object -Maximum).Maximum }

    if ($reported -gt 0 -and $reported * 2 -le $shorter) {
        return [ordered]@{
            radius = $reported; trusted = $true; reason = ''
            source = 'dumpsys window displays (mRoundedCorners)'
        }
    }

    if ($reported * 2 -gt $shorter) {
        $note = "the device reports corner radius $reported on a ${Width}x${Height} display, where the widest arc that can exist is $inscribed"
        if ($IsRoundWatch) {
            return [ordered]@{
                radius = $inscribed; trusted = $true; reason = $note
                source = "impossible radius $reported rejected - square watch display, inscribed circle assumed"
            }
        }
        return [ordered]@{ radius = 0; trusted = $false; reason = $note; source = 'unusable rounded-corner data' }
    }

    if ($IsRoundWatch) {
        return [ordered]@{
            radius = $inscribed; trusted = $true; reason = ''
            source = 'watch characteristic + square display - assumed round'
        }
    }
    return [ordered]@{
        radius = 0; trusted = $true; reason = ''
        source = 'no rounded-corner data - treated as a plain rectangle'
    }
}

# How far outside its nearest corner circle a point sits. 0 means the point is not in any corner
# quadrant at all, i.e. it is in the straight-edged middle of the screen and cannot be off-glass.
function Get-CornerOverflow {
    param([double]$Px, [double]$Py, $Shape)
    $r = [double]$Shape.radius
    if ($r -le 0) { return 0.0 }
    $w = [double]$Shape.width
    $h = [double]$Shape.height
    $quadrants = @(
        @{ cx = $r;      cy = $r;      left = $true;  top = $true  },
        @{ cx = $w - $r; cy = $r;      left = $false; top = $true  },
        @{ cx = $w - $r; cy = $h - $r; left = $false; top = $false },
        @{ cx = $r;      cy = $h - $r; left = $true;  top = $false }
    )
    $worst = 0.0
    foreach ($q in $quadrants) {
        $inX = if ($q.left) { $Px -lt $q.cx } else { $Px -gt $q.cx }
        $inY = if ($q.top)  { $Py -lt $q.cy } else { $Py -gt $q.cy }
        if (-not ($inX -and $inY)) { continue }
        $d = [math]::Sqrt([math]::Pow($Px - $q.cx, 2) + [math]::Pow($Py - $q.cy, 2))
        if ($d -gt $worst) { $worst = $d }
    }
    return $worst
}

function Get-BoxOverflow {
    param([double]$X1, [double]$Y1, [double]$X2, [double]$Y2, $Shape)
    $worst = 0.0
    foreach ($p in @(@($X1, $Y1), @($X2, $Y1), @($X1, $Y2), @($X2, $Y2))) {
        $d = Get-CornerOverflow $p[0] $p[1] $Shape
        if ($d -gt $worst) { $worst = $d }
    }
    return $worst
}

# EDGE / CLIPPED / OFF-GLASS - the three classes explained in the header. Returns $null when the
# node sits entirely on the glass, so the caller reports only what actually left it.
function Get-ClipVerdict {
    param($Node, $Shape)
    $r = [double]$Shape.radius
    if ($r -le 0) { return $null }
    $worst = Get-BoxOverflow $Node.x1 $Node.y1 $Node.x2 $Node.y2 $Shape
    if ($worst -le $r) { return $null }
    if ($Node.x1 -le 0 -or $Node.y1 -le 0 -or $Node.x2 -ge $Shape.width -or $Node.y2 -ge $Shape.height) {
        return @{ kind = 'EDGE'; overflow = $worst }
    }
    # Slide the box vertically to the middle of the screen, where the glass is at its widest, and
    # ask whether it fits THERE. If it does, scrolling can reveal it and this frame proves nothing.
    $boxHeight = [double]($Node.y2 - $Node.y1)
    $midTop    = ($Shape.height / 2.0) - ($boxHeight / 2.0)
    $scrolled  = Get-BoxOverflow $Node.x1 $midTop $Node.x2 ($midTop + $boxHeight) $Shape
    if ($Node.scrollAncestor -and $scrolled -le $r) { return @{ kind = 'CLIPPED'; overflow = $worst } }
    return @{ kind = 'OFF-GLASS'; overflow = $worst }
}
