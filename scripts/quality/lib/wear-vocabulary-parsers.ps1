<#
.SYNOPSIS
    Extracts the mirrored phone/watch wire vocabularies out of Kotlin sources.

.DESCRIPTION
    The `app_v2` and `wear` modules compile separately and share no artifact, so every vocabulary that
    crosses the Data Layer as text exists as two hand-written declarations. Two gates compare those
    declarations - `assert-wear-settings-parity.ps1` for the settings channel (S2620) and
    `assert-wear-wire-vocabulary-parity.ps1` for the rest (S2642) - and both must decide "what is
    declared in this file" with the SAME code.

    That is the S1621 rule, and here it is not hypothetical. Before this library existed the settings
    gate's own member reader returned ONE member of four for the single-line form
    `enum class X { A, B, C }`, because it scanned the enum body line by line and stopped at the first
    match. A false divergence is the mild half of that defect; the dangerous half is two single-line
    sides comparing as one-against-one and passing GREEN while members two onward drift unchecked -
    exactly the silent PASS both gates exist to prevent. With one copy of the parse per gate, fixing
    that in one leaves the other wrong and the two gates disagree in silence.

    Every function returns an empty collection when its target is absent. A caller must treat an empty
    result as "the parse found nothing", never as "the vocabulary is empty", and fail rather than pass.

.NOTES
    Dot-sourced, not invoked. Exposes no exit code of its own; callers own their verdicts.
#>

# The text of an enum's body, from the class header's opening brace to the end of the source. Callers
# narrow it to the member list themselves; returns $null when the type is not declared here.
function Get-KotlinEnumBody {
    param([string]$Source, [string]$TypeName)

    $header = [regex]::Match($Source, "enum\s+class\s+$([regex]::Escape($TypeName))\s*(?:\([^)]*\))?\s*\{")
    if (-not $header.Success) { return $null }
    return $Source.Substring($header.Index + $header.Length)
}

# The member list alone - comments stripped, cut at the ';' or '}' that ends it. Everything past that
# point is the companion object, whose DEFAULT and fromNameOrDefault must never read as members.
function Get-KotlinEnumMemberList {
    param([string]$Source, [string]$TypeName)

    $body = Get-KotlinEnumBody -Source $Source -TypeName $TypeName
    if ($null -eq $body) { return $null }

    $clean = $body -replace '(?s)/\*.*?\*/', '' -replace '(?m)//.*$', ''
    $end = $clean.Length
    foreach ($stop in @(';', '}')) {
        $i = $clean.IndexOf($stop)
        if ($i -ge 0 -and $i -lt $end) { $end = $i }
    }
    return $clean.Substring(0, $end)
}

# Member names of one `enum class`, in declaration order.
#
# Scanned by token rather than by line, which is what makes the single-line form
# `enum class X { A, B, C }` read as four members instead of one - see the header for why that
# distinction is a correctness matter and not a tidiness one.
function Get-KotlinEnumMember {
    param([string]$Source, [string]$TypeName)

    $list = Get-KotlinEnumMemberList -Source $Source -TypeName $TypeName
    if ($null -eq $list) { return @() }

    $members = @()
    foreach ($m in [regex]::Matches($list, '(?m)(?:^|,)\s*(?:@\w+\s*(?:\([^)]*\))?\s*)*(?<name>[A-Z][A-Z0-9_]*)\s*(?=\(|,|$)')) {
        $members += $m.Groups['name'].Value
    }
    return $members
}

# Each member's `@SerializedName` value, falling back to the member name where a member carries none.
#
# The two sides of three families are written differently on purpose (S2642 ADR-6): the phone pins its
# wire names with the annotation, the watch relies on the member name. Comparing member names alone
# would pass a changed `@SerializedName` on the phone - no member name moves - while the wire breaks.
function Get-KotlinEnumSerializedName {
    param([string]$Source, [string]$TypeName)

    $list = Get-KotlinEnumMemberList -Source $Source -TypeName $TypeName
    if ($null -eq $list) { return @() }

    $names = @()
    foreach ($m in [regex]::Matches($list, '(?m)(?:^|,)\s*(?:@SerializedName\s*\(\s*"(?<wire>[^"]+)"\s*\)\s*)?(?<name>[A-Z][A-Z0-9_]*)\s*(?=\(|,|$)')) {
        $wire = $m.Groups['wire'].Value
        $names += if ([string]::IsNullOrEmpty($wire)) { $m.Groups['name'].Value } else { $wire }
    }
    return $names
}

# Member names of a named `setOf(..)` / `listOf(..)` declaration, in declaration order.
#
# S2641: the sending half of a vocabulary is not always an enum. The phone decides what may cross to
# the watch with `ResourceType.WATCH_TRANSFERABLE`, a subset of a larger enum, so comparing the enums
# themselves would report a divergence on every member the channel deliberately never carries.
# Qualifiers are dropped, so `setOf(SMB, ResourceType.FTP)` reads as two members either way.
function Get-KotlinNamedSetMember {
    param([string]$Source, [string]$SetName)

    $decl = [regex]::Match(
        $Source,
        "val\s+$([regex]::Escape($SetName))\s*(?::[^=]+)?=\s*(?:setOf|listOf|setOfNotNull)\s*\((?<body>[^)]*)\)"
    )
    if (-not $decl.Success) { return @() }

    $members = @()
    foreach ($m in [regex]::Matches($decl.Groups['body'].Value, '(?<name>[A-Z][A-Z0-9_]*)\s*(?=,|$)')) {
        $members += $m.Groups['name'].Value
    }
    return $members
}

# The quoted left-hand literals of a `when` inside a named function, in branch order.
#
# S2641: the receiving half of the source contract is a branch list, not a declaration - the watch
# resolves an incoming name by explicit branch precisely so that a type it can open by hand is not
# automatically accepted off the wire. Only literals before a `->` are read, so the `else` branch and
# any string in a branch BODY are never mistaken for accepted names.
function Get-KotlinWhenBranchLiteral {
    param([string]$Source, [string]$FunctionName)

    $decl = [regex]::Match($Source, "fun\s+$([regex]::Escape($FunctionName))\s*\(")
    if (-not $decl.Success) { return @() }

    $rest = $Source.Substring($decl.Index)
    $next = [regex]::Match($rest.Substring(1), '(?m)^\s{0,4}(?:private\s+|internal\s+)?fun\s')
    if ($next.Success) { $rest = $rest.Substring(0, $next.Index + 1) }

    $literals = @()
    foreach ($m in [regex]::Matches($rest, '(?m)^\s*"(?<v>[^"]+)"\s*->')) {
        $literals += $m.Groups['v'].Value
    }
    return $literals
}

# Ordered map of constant name -> string value for every `const val <Prefix>* = ".."` in the source.
#
# The MAP is returned rather than the bare values because swapping two constants' values leaves the
# value set identical while inverting the wire: the sender writes the value now meaning the other
# thing, and the receiver resolves it to the other thing (S2642 ADR-3).
function Get-KotlinConstMap {
    param([string]$Source, [string]$Prefix)

    $map = [ordered]@{}
    foreach ($m in [regex]::Matches($Source, "const\s+val\s+(?<n>$([regex]::Escape($Prefix))\w+)\s*=\s*""(?<v>[^""]+)""")) {
        $map[$m.Groups['n'].Value] = $m.Groups['v'].Value
    }
    return $map
}

# Ordered map of constant name -> string value for every `const val` whose value starts with a
# transport route prefix. Names cannot select this family: `NETWORK_SOURCES_REQUEST` and
# `PHONE_RESOURCE_PAGE` are equally valid wire routes despite different name prefixes.
function Get-KotlinConstMapByValuePrefix {
    param([string]$Source, [string]$ValuePrefix)

    $map = [ordered]@{}
    foreach ($m in [regex]::Matches($Source, 'const\s+val\s+(?<n>\w+)\s*=\s*"(?<v>[^"]+)"')) {
        $value = $m.Groups['v'].Value
        if ($value.StartsWith($ValuePrefix, [System.StringComparison]::Ordinal)) {
            $map[$m.Groups['n'].Value] = $value
        }
    }
    return $map
}

# The same map, but only inside the companion object of a named class.
#
# `WearFileTransferAck` and `WearFileReceiveAck` share one file and one `OUTCOME_` prefix on each
# side. A file-wide read merges them into a single 13-element set in which a constant MOVED from one
# companion to the other is invisible - the merged set does not change - so the scoping is what makes
# those two families separable at all (S2642).
function Get-KotlinCompanionConstMap {
    param([string]$Source, [string]$ClassName, [string]$Prefix)

    $decl = [regex]::Match($Source, "(?:data\s+)?class\s+$([regex]::Escape($ClassName))\b")
    if (-not $decl.Success) { return [ordered]@{} }

    $rest = $Source.Substring($decl.Index)
    # Stop at the next top-level declaration, so a companion belonging to a class further down the
    # file is never adopted by this one.
    $next = [regex]::Match($rest.Substring(1), '(?m)^(?:data\s+)?(?:class|enum\s+class|object|interface)\s')
    if ($next.Success) { $rest = $rest.Substring(0, $next.Index + 1) }

    $companion = [regex]::Match($rest, '(?s)companion\s+object\s*\{(?<body>.*)$')
    if (-not $companion.Success) { return [ordered]@{} }

    return Get-KotlinConstMap -Source $companion.Groups['body'].Value -Prefix $Prefix
}
