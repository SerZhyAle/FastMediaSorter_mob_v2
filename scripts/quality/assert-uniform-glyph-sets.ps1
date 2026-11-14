#requires -Version 7.0
<#
.SYNOPSIS
    S4570 gate for the ICON-RENDER sibling-set rule: inside one set of sibling items a glyph is on every
    member or on none.

.DESCRIPTION
    Judges the XML of app_v2 - every source set - for three kinds of sibling set:
      menu    - the items of one <menu> level, a <group>'s items included; a submenu's own <item> is a
                member of the parent level. Items shown on the toolbar (showAsAction always / ifRoom /
                withText / collapseActionView) and items in the overflow are two sets.
      header  - the CollapsibleSectionHeader elements of one parent ViewGroup.
      rows    - the settings row widgets whose nearest ancestor carrying an android:id is the same view:
                a group's rows usually sit in id-less horizontal wrappers, so the direct parent would
                split one visible group into many sets.
    A set is mixed when some members carry their icon attribute and others carry none. Which element is a
    member and which attribute is its glyph is the $Members table below - a new row widget is one line.
    A menu whose icons the platform never draws is in $UndrawnMenus with its reason; a mixed set there is
    invisible and not judged.

    Finding key: <kind>|<file>|<set>|<glyph-less ids>.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; the contract suite passes a fixture tree.

.PARAMETER ChangedFiles
    The caller's changed set (comma-joined accepted). A finding is charged only when its file is in the
    set; absent, every finding is charged.

.PARAMETER Gate
    Accepted for the closure's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the findings and the verdict line.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-uniform-glyph-sets.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-uniform-glyph-sets.ps1 -ChangedFiles "app_v2/src/main/res/menu/x.xml"

.NOTES
    Exit codes:
      0 - no mixed set is charged to this run.
      1 - at least one charged set is mixed.
      2 - could not verify: the source root app_v2/src is missing.
      3 - advisory: mixed sets exist, but none is in -ChangedFiles.
#>
[CmdletBinding()]
param(
    [string] $Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $ChangedFiles = '',
    [switch] $Gate,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$AndroidNs = 'http://schemas.android.com/apk/res/android'
$AppNs = 'http://schemas.android.com/apk/res-auto'

# Element local name (last dotted segment) -> set kind and the res-auto attribute that carries its glyph.
$Members = @{
    'CollapsibleSectionHeader' = @{ Kind = 'header'; Attr = 'csh_icon' }
    'SettingsToggleRow'        = @{ Kind = 'rows'; Attr = 'str_icon' }
    'SettingsSelectionRow'     = @{ Kind = 'rows'; Attr = 'ssr_icon' }
    'SettingsDropdownRow'      = @{ Kind = 'rows'; Attr = 'sdr_icon' }
    'SettingsInputRow'         = @{ Kind = 'rows'; Attr = 'sir_icon' }
}

# Menu file name -> why its icons are never drawn (no setForceShowIcon on any host).
$UndrawnMenus = @{
    'overflow_menu_player.xml' = 'never inflated: it declares the R.id values the code-built player overflow (CommandPanelController) uses'
    'menu_resource_ops.xml' ='ResourceOpsMenuManager inflates it into a PopupMenu without setForceShowIcon, so no item icon is drawn'
}

$srcRoot = Join-Path $Root 'app_v2/src'
if (-not (Test-Path -LiteralPath $srcRoot)) {
    Write-Output "assert-uniform-glyph-sets: could not verify - $srcRoot is missing"
    exit 2
}

$changed = @($ChangedFiles.Split(',') | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })

. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{
        module = 'app_v2'
        scope  = $(if ($changed.Count -gt 0) { 'changed-set' } else { 'tree' })
    })

function Get-RelPath([string] $full) {
    [IO.Path]::GetRelativePath($Root, $full).Replace('\', '/')
}

function Get-Id([System.Xml.XmlElement] $e) {
    $id = $e.GetAttribute('id', $AndroidNs)
    if ($id) { return ($id -replace '^@\+?id/', '') }
    $title = $e.GetAttribute('title', $AndroidNs)
    if ($title) { return ($title -replace '^@string/', '') }
    return "<$($e.LocalName)>"
}

function Test-ToolbarItem([System.Xml.XmlElement] $item) {
    $show = $item.GetAttribute('showAsAction', $AppNs)
    if (-not $show) { $show = $item.GetAttribute('showAsAction', $AndroidNs) }
    return [bool]($show -match 'always|ifRoom|withText|collapseActionView')
}

$findings = [System.Collections.Generic.List[pscustomobject]]::new()

function Add-Finding([string] $kind, [string] $file, [string] $set, [string[]] $without) {
    $findings.Add([pscustomobject]@{ Kind = $kind; File = $file; Set = $set; Missing = ($without -join ',') })
}

function Measure-MenuLevel([System.Xml.XmlElement] $menu, [string] $file, [string] $path) {
    $items = [System.Collections.Generic.List[System.Xml.XmlElement]]::new()
    foreach ($child in $menu.ChildNodes) {
        if ($child -isnot [System.Xml.XmlElement]) { continue }
        if ($child.LocalName -eq 'item') { $items.Add($child) }
        elseif ($child.LocalName -eq 'group') {
            foreach ($g in $child.ChildNodes) { if ($g -is [System.Xml.XmlElement] -and $g.LocalName -eq 'item') { $items.Add($g) } }
        }
    }
    foreach ($toolbar in @($true, $false)) {
        $set = @($items | Where-Object { (Test-ToolbarItem $_) -eq $toolbar })
        $with = @($set | Where-Object { $_.GetAttribute('icon', $AndroidNs) })
        $without = @($set | Where-Object { -not $_.GetAttribute('icon', $AndroidNs) })
        if ($with.Count -gt 0 -and $without.Count -gt 0) {
            $name = if ($toolbar) { "$path toolbar" } else { $path }
            Add-Finding 'menu' $file $name @($without | ForEach-Object { Get-Id $_ })
        }
    }
    foreach ($item in $items) {
        foreach ($sub in $item.ChildNodes) {
            if ($sub -is [System.Xml.XmlElement] -and $sub.LocalName -eq 'menu') {
                Measure-MenuLevel $sub $file "$path > $(Get-Id $item)"
            }
        }
    }
}

$menuFiles = Get-ChildItem -Path $srcRoot -Recurse -Filter '*.xml' -File |
    Where-Object { $_.Directory.Name -match '^menu(-|$)' }
foreach ($mf in $menuFiles) {
    if ($UndrawnMenus.ContainsKey($mf.Name)) { continue }
    [xml] $doc = Get-Content -Raw -LiteralPath $mf.FullName
    if ($doc.DocumentElement.LocalName -ne 'menu') { continue }
    Measure-MenuLevel $doc.DocumentElement (Get-RelPath $mf.FullName) 'menu'
}

function Get-SetOwner([System.Xml.XmlElement] $e, [string] $kind) {
    $p = $e.ParentNode
    if ($kind -eq 'header') { return $p }
    while ($p -is [System.Xml.XmlElement]) {
        if ($p.GetAttribute('id', $AndroidNs)) { return $p }
        $p = $p.ParentNode
    }
    return $e.OwnerDocument.DocumentElement
}

$layoutFiles = Get-ChildItem -Path $srcRoot -Recurse -Filter '*.xml' -File |
    Where-Object { $_.Directory.Name -match '^layout(-|$)' }
foreach ($lf in $layoutFiles) {
    $raw = Get-Content -Raw -LiteralPath $lf.FullName
    if ($raw -notmatch 'CollapsibleSectionHeader|SettingsToggleRow|SettingsSelectionRow|SettingsDropdownRow|SettingsInputRow') { continue }
    [xml] $doc = $raw
    $sets = [ordered]@{}
    foreach ($e in $doc.SelectNodes('//*')) {
        $local = $e.LocalName.Split('.')[-1]
        if (-not $Members.ContainsKey($local)) { continue }
        $m = $Members[$local]
        $owner = Get-SetOwner $e $m.Kind
        $key = "$($m.Kind)|$([System.Runtime.CompilerServices.RuntimeHelpers]::GetHashCode($owner))"
        if (-not $sets.Contains($key)) { $sets[$key] = @{ Kind = $m.Kind; Owner = $owner; With = @(); Without = @() } }
        if ($e.GetAttribute($m.Attr, $AppNs)) { $sets[$key].With += (Get-Id $e) } else { $sets[$key].Without += (Get-Id $e) }
    }
    foreach ($s in $sets.Values) {
        if ($s.With.Count -gt 0 -and $s.Without.Count -gt 0) {
            Add-Finding $s.Kind (Get-RelPath $lf.FullName) (Get-Id $s.Owner) $s.Without
        }
    }
}

$charged = @($findings | Where-Object { $changed.Count -eq 0 -or $changed -contains $_.File })
foreach ($f in $findings) {
    $mark = if ($charged -contains $f) { 'MIXED' } else { 'other' }
    Write-Output "  $mark  $($f.Kind)|$($f.File)|$($f.Set)|$($f.Missing)"
}
if ($findings.Count -gt 0 -and -not $Quiet) {
    Write-Output '  fix: give every member of the set a glyph from the ICON-SET vocabulary (ICON-RENDER sibling-set rule), or remove the glyph from all of them.'
}
if ($charged.Count -gt 0) {
    Write-Output "assert-uniform-glyph-sets: FAIL ($($charged.Count) mixed set(s))"
    exit 1
}
if ($findings.Count -gt 0) {
    Write-Output "assert-uniform-glyph-sets: PASS WITH ADVISORIES ($($findings.Count) mixed set(s), none in the changed set)"
    exit 3
}
Write-Output 'assert-uniform-glyph-sets: PASS (no mixed sibling set)'
exit 0
