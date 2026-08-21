<#
.SYNOPSIS
  uiautomator node-tree parsing and display-shape geometry, shared by adb.ps1 and its test suite.

.DESCRIPTION
  Pure functions: no adb call, no device, no writes, no dependency on adb.ps1's own Fail/Invoke-Adb.
  That is the point - the clip-check classification is the non-obvious part of S1847, and it is
  calibrated against recorded dumps by scripts/devtest/adb-clip-check.tests/Run-Tests.ps1, which can
  only dot-source these if they live outside a script that runs verbs on load. Same reason
  lib/find-adb.ps1 exists (S1341) and lib/adb-log-filter.ps1 exists (S1332).

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
            if ($nodeText -or $nodeDesc) {
                $x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
                $x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
                $Acc.Add([ordered]@{
                    label  = if ($nodeText) { $nodeText } else { $nodeDesc }
                    source = if ($nodeText) { 'text' } else { 'desc' }
                    text   = $nodeText
                    desc   = $nodeDesc
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
