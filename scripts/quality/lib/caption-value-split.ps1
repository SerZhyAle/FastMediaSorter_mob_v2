#requires -Version 7.0

# S3816: extracted verbatim from source-matchers.ps1 to keep it under the 2000-line script ceiling.
# Dot-sourced by source-matchers.ps1, so these predicates share its script scope.

# S2328: the caption/value split - a label that takes the row's free width while its value sits at
# the far edge. Structural, not lexical, and deliberately so: the reference settings row carries the
# SAME attributes as the defect (a weight, an end gravity) and differs only in WHERE they sit, so a
# regex cannot separate them. The discriminator is order - in the reference the weighted spacer comes
# AFTER the value, so the slack falls at the row's end instead of between the pair.
$script:CaptionValueControlRx = [regex]'(?:^|\.)(?:Switch|MaterialSwitch|SwitchCompat|SwitchMaterial|Button|MaterialButton|CheckBox|MaterialCheckBox|AppCompatCheckBox|Slider|SeekBar|RangeSlider|ImageButton|EditText|TextInputEditText|RadioButton|Spinner)$'
$script:CaptionValueTextRx = [regex]'(?:^|\.)(?:TextView|MaterialTextView|AppCompatTextView|Chronometer)$'

function Get-CaptionValueSimpleName([System.Xml.Linq.XElement]$Element) {
    $n = $Element.Name.LocalName
    $i = $n.LastIndexOf('.')
    if ($i -ge 0) { $n = $n.Substring($i + 1) }
    return $n
}

# Namespace-agnostic on purpose: `layout_constraint*` arrives in the res-auto namespace and
# `layout_weight` in the android one, and no layout attribute shares a local name across the two.
function Get-CaptionValueAttr([System.Xml.Linq.XElement]$Element, [string]$LocalName) {
    foreach ($a in $Element.Attributes()) {
        if ($a.Name.LocalName -eq $LocalName) { return $a.Value }
    }
    return $null
}

function Test-CaptionValueGravityEnd([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return $false }
    foreach ($part in ($Value -split '\|')) {
        if ($part.Trim() -in @('end', 'right')) { return $true }
    }
    return $false
}

function Test-CaptionValueIsText([System.Xml.Linq.XElement]$Element) {
    $script:CaptionValueTextRx.IsMatch((Get-CaptionValueSimpleName $Element))
}

function Test-CaptionValueIsControl([System.Xml.Linq.XElement]$Element) {
    $script:CaptionValueControlRx.IsMatch((Get-CaptionValueSimpleName $Element))
}

# A value is "text-like" when it is a TextView, or a wrapper carrying text and no control. The
# wrapper case is what makes a primary+secondary value column count; the control case is what keeps
# the reference settings row - caption, then a switch or a chevron at the end - passing.
function Test-CaptionValueTextLike([System.Xml.Linq.XElement]$Element) {
    if (Test-CaptionValueIsControl $Element) { return $false }
    if (Test-CaptionValueIsText $Element) { return $true }
    $desc = @($Element.Descendants())
    if ($desc.Count -eq 0) { return $false }
    foreach ($d in $desc) { if (Test-CaptionValueIsControl $d) { return $false } }
    foreach ($d in $desc) { if (Test-CaptionValueIsText $d) { return $true } }
    return $false
}

function Test-CaptionValueHorizontalRow([System.Xml.Linq.XElement]$Element) {
    if ((Get-CaptionValueSimpleName $Element) -ne 'LinearLayout') { return $false }
    $o = Get-CaptionValueAttr $Element 'orientation'
    return ([string]::IsNullOrWhiteSpace($o) -or $o -eq 'horizontal')
}

# The one definition of the violation. Measure- and Find- both read it, so the count the gate
# enforces and the lines `-List` prints can never disagree (S1621).
function Get-CaptionValueSplitHits([string]$Text) {
    $hits = @()
    if ([string]::IsNullOrEmpty($Text)) { return $hits }
    # Cheap text gate before the parse: most layout files carry none of this vocabulary, and the
    # XML parse is the expensive half of the rule.
    if ($Text -notmatch 'layout_weight|layout_constraintEnd_toEndOf|gravity') { return $hits }

    $doc = $null
    try {
        $doc = [System.Xml.Linq.XDocument]::Parse($Text, [System.Xml.Linq.LoadOptions]::SetLineInfo)
    }
    catch {
        # A malformed file is the XML parser's finding, not this rule's - turning it into a
        # violation count would blame the wrong gate for the wrong defect.
        return $hits
    }
    if ($null -eq $doc -or $null -eq $doc.Root) { return $hits }

    foreach ($el in $doc.Descendants()) {
        $name = Get-CaptionValueSimpleName $el
        $line = ([System.Xml.IXmlLineInfo]$el).LineNumber

        # Form 1 - weighted caption in a horizontal row with the value after it.
        if (Test-CaptionValueHorizontalRow $el) {
            $kids = @($el.Elements())
            for ($i = 0; $i -lt $kids.Count; $i++) {
                $kid = $kids[$i]
                if (-not (Test-CaptionValueIsText $kid)) { continue }
                $wv = 0.0
                if (-not [double]::TryParse((Get-CaptionValueAttr $kid 'layout_weight'), [ref]$wv)) { continue }
                if ($wv -le 0) { continue }
                for ($j = $i + 1; $j -lt $kids.Count; $j++) {
                    if (Test-CaptionValueTextLike $kids[$j]) {
                        $hits += [pscustomobject]@{ Line = ([System.Xml.IXmlLineInfo]$kid).LineNumber; Form = 'weighted-caption' }
                        break
                    }
                }
            }
        }

        # Form 2 - the value pushed to the row's far end by its own gravity.
        if ((Test-CaptionValueIsText $el) -and $null -ne $el.Parent -and (Test-CaptionValueHorizontalRow $el.Parent)) {
            $g = Get-CaptionValueAttr $el 'gravity'
            $lg = Get-CaptionValueAttr $el 'layout_gravity'
            $ta = Get-CaptionValueAttr $el 'textAlignment'
            if ((Test-CaptionValueGravityEnd $g) -or (Test-CaptionValueGravityEnd $lg) -or ($ta -eq 'viewEnd')) {
                $prior = $false
                foreach ($sib in $el.ElementsBeforeSelf()) { if (Test-CaptionValueIsText $sib) { $prior = $true } }
                if ($prior) { $hits += [pscustomobject]@{ Line = $line; Form = 'end-aligned-value' } }
            }
        }

        # Form 3 - the split declared in a style, which hands it to every consumer at once. This is
        # the form that reached seven network monitor screens from two style blocks.
        if ($name -eq 'style') {
            $hasWeight = $false
            $endGravity = $false
            foreach ($item in $el.Elements()) {
                if ((Get-CaptionValueSimpleName $item) -ne 'item') { continue }
                $itemName = Get-CaptionValueAttr $item 'name'
                if ($itemName -eq 'android:layout_weight') { $hasWeight = $true }
                if ($itemName -eq 'android:gravity' -and (Test-CaptionValueGravityEnd $item.Value)) { $endGravity = $true }
            }
            if ($hasWeight -and $endGravity) { $hits += [pscustomobject]@{ Line = $line; Form = 'style-declared-split' } }
        }

        # Form 4 - the constraint spelling: value pinned to the parent's end and anchored to a
        # sibling's top, with nothing tying its start to the caption, so the gap is the screen.
        if (Test-CaptionValueIsText $el) {
            if ((Get-CaptionValueAttr $el 'layout_constraintEnd_toEndOf') -eq 'parent') {
                $hasStart = $false
                foreach ($a in $el.Attributes()) {
                    if ($a.Name.LocalName -like 'layout_constraintStart_*') { $hasStart = $true }
                }
                $topTo = Get-CaptionValueAttr $el 'layout_constraintTop_toTopOf'
                if (-not $hasStart -and -not [string]::IsNullOrWhiteSpace($topTo) -and $topTo -ne 'parent') {
                    $hits += [pscustomobject]@{ Line = $line; Form = 'unanchored-end-constraint' }
                }
            }
        }
    }

    return $hits
}

function Measure-CaptionValueSplit([string]$Text) {
    return @(Get-CaptionValueSplitHits $Text).Count
}

# S3249: an id that names a strip of controls rather than a control. The rule below counts a raw
# ImageButton only inside one of these, because an ImageButton elsewhere - a row's trailing action,
# a dialog's single glyph - is not the defect: the defect is two icon-button idioms inside one bar,
# differing in touch target, ripple shape and disabled tint.
$script:BarContainerIdRx = [regex]'(?i)@\+?id/\w*(bar|panel|controls|operations|toolbar|strip)'

function Get-RawImageButtonInBarHits([string]$Text) {
    $hits = @()
    if ([string]::IsNullOrEmpty($Text)) { return $hits }
    # Cheap text gate before the parse - most layouts declare no ImageButton at all.
    if ($Text -notmatch '<ImageButton') { return $hits }

    $doc = $null
    try {
        $doc = [System.Xml.Linq.XDocument]::Parse($Text, [System.Xml.Linq.LoadOptions]::SetLineInfo)
    }
    catch {
        # A malformed file is the XML parser's finding, not this rule's.
        return $hits
    }
    if ($null -eq $doc -or $null -eq $doc.Root) { return $hits }

    foreach ($el in $doc.Descendants()) {
        if ((Get-CaptionValueSimpleName $el) -ne 'ImageButton') { continue }
        $parent = $el.Parent
        while ($null -ne $parent) {
            $id = Get-CaptionValueAttr $parent 'id'
            if ($null -ne $id -and $script:BarContainerIdRx.IsMatch($id)) {
                $hits += [pscustomobject]@{ Line = ([System.Xml.IXmlLineInfo]$el).LineNumber }
                break
            }
            $parent = $parent.Parent
        }
    }
    return $hits
}

function Measure-RawImageButtonInBar([string]$Text) {
    return @(Get-RawImageButtonInBarHits $Text).Count
}

function Find-RawImageButtonInBarLines([string]$Text) {
    return @(Get-RawImageButtonInBarHits $Text | ForEach-Object { $_.Line } | Sort-Object -Unique)
}

function Find-CaptionValueSplitLines([string]$Text) {
    return @(Get-CaptionValueSplitHits $Text | ForEach-Object { $_.Line } | Sort-Object -Unique)
}
