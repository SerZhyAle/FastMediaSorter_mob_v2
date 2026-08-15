# house-text-style.ps1 - shared house text style normalizer (S1544).
#
# Dot-source it; it declares functions and never exits, so it has no exit-code contract.
# The rule set lives here and nowhere else: before S1544 five separate fixers each carried a
# partial copy, which is why none of them knew about the long dash.
#
# Scope follows the canon: documentation prose and user-visible UI text, in any language.
# Code, specs, commands and logs are out of scope and the -Area switch is what keeps them out.

Set-StrictMode -Version Latest

# Lowercase forms only - Convert-HouseStyleText derives the Title and UPPER variants.
# Deliberately absent, because the substitution is not decidable without reading the sentence:
#   все/всё, всего, чем, нет (particle vs verb), узнает (present vs future).
# Also absent because they carry no yo at all, though fix-yo.ps1 claimed they did:
#   тяжело (adverb), темнее (comparative).
# Built on first use by Convert-HouseStyleYo and reused for the life of the process. Declared here
# because StrictMode refuses to read a variable that was never assigned.
$script:HouseStyleYoRegex = $null

$script:HouseStyleYoMap = [ordered]@{
    'берет' = 'берёт'; 'берем' = 'берём'; 'берете' = 'берёте'; 'берешь' = 'берёшь'
    'ведет' = 'ведёт'; 'ведем' = 'ведём'; 'ведете' = 'ведёте'; 'ведешь' = 'ведёшь'
    'везет' = 'везёт'; 'вернет' = 'вернёт'; 'войдет' = 'войдёт'
    'даем' = 'даём'; 'дает' = 'даёт'; 'даете' = 'даёте'; 'даешь' = 'даёшь'
    'идет' = 'идёт'; 'идем' = 'идём'; 'идете' = 'идёте'; 'идешь' = 'идёшь'
    'ждет' = 'ждёт'; 'жжет' = 'жжёт'; 'живет' = 'живёт'
    'зайдет' = 'зайдёт'; 'найдет' = 'найдёт'; 'придет' = 'придёт'
    'несет' = 'несёт'; 'принесет' = 'принесёт'; 'бережет' = 'бережёт'
    'отдает' = 'отдаёт'; 'передает' = 'передаёт'; 'продает' = 'продаёт'
    'задает' = 'задаёт'; 'выдает' = 'выдаёт'; 'сдает' = 'сдаёт'; 'создает' = 'создаёт'
    'поет' = 'поёт'; 'прочтет' = 'прочтёт'; 'разберет' = 'разберёт'
    'нес' = 'нёс'; 'вел' = 'вёл'
    'пошел' = 'пошёл'; 'нашел' = 'нашёл'; 'зашел' = 'зашёл'; 'пришел' = 'пришёл'
    'перешел' = 'перешёл'; 'подошел' = 'подошёл'; 'отошел' = 'отошёл'
    'дошел' = 'дошёл'; 'прошел' = 'прошёл'; 'вошел' = 'вошёл'; 'ушел' = 'ушёл'
    'еж' = 'ёж'; 'елка' = 'ёлка'; 'еще' = 'ещё'
    'емкость' = 'ёмкость'; 'емкостей' = 'ёмкостей'; 'емкостью' = 'ёмкостью'
    'желтый' = 'жёлтый'; 'клен' = 'клён'; 'пленка' = 'плёнка'; 'путем' = 'путём'
    'жесткий' = 'жёсткий'; 'жесткая' = 'жёсткая'; 'жесткое' = 'жёсткое'
    'жесткие' = 'жёсткие'; 'жесткого' = 'жёсткого'; 'жесткому' = 'жёсткому'
    'жестком' = 'жёстком'; 'жесткой' = 'жёсткой'; 'жестких' = 'жёстких'
    'жесткими' = 'жёсткими'; 'жестко' = 'жёстко'
    'жесткость' = 'жёсткость'; 'жесткости' = 'жёсткости'; 'жесткостью' = 'жёсткостью'
    'зеленый' = 'зелёный'; 'зеленая' = 'зелёная'; 'зеленые' = 'зелёные'
    'объем' = 'объём'; 'объема' = 'объёма'; 'объему' = 'объёму'; 'объемом' = 'объёмом'
    'объемный' = 'объёмный'; 'объемная' = 'объёмная'; 'объемное' = 'объёмное'
    'объемные' = 'объёмные'; 'объемных' = 'объёмных'; 'подъем' = 'подъём'
    'прием' = 'приём'; 'приема' = 'приёма'; 'приему' = 'приёму'; 'приемом' = 'приёмом'
    'приемы' = 'приёмы'; 'приемник' = 'приёмник'; 'приемника' = 'приёмника'
    'разъем' = 'разъём'; 'разъема' = 'разъёма'
    'тверд' = 'твёрд'; 'твердый' = 'твёрдый'; 'твердая' = 'твёрдая'; 'твердое' = 'твёрдое'
    'твердые' = 'твёрдые'; 'твердо' = 'твёрдо'; 'твердость' = 'твёрдость'
    'темный' = 'тёмный'; 'темная' = 'тёмная'; 'темное' = 'тёмное'; 'темные' = 'тёмные'
    'темного' = 'тёмного'; 'темному' = 'тёмному'; 'темном' = 'тёмном'
    'темной' = 'тёмной'; 'темных' = 'тёмных'; 'темными' = 'тёмными'
    'темно' = 'тёмно'; 'темнота' = 'тёмнота'
    'тяжелый' = 'тяжёлый'; 'тяжелая' = 'тяжёлая'; 'тяжелое' = 'тяжёлое'
    'тяжелые' = 'тяжёлые'; 'тяжелого' = 'тяжёлого'; 'тяжелому' = 'тяжёлому'
    'тяжелом' = 'тяжёлом'; 'тяжелой' = 'тяжёлой'; 'тяжелых' = 'тяжёлых'
    'черный' = 'чёрный'; 'черная' = 'чёрная'; 'черное' = 'чёрное'; 'черные' = 'чёрные'
    'черного' = 'чёрного'; 'черному' = 'чёрному'; 'черном' = 'чёрном'
    'черной' = 'чёрной'; 'черных' = 'чёрных'; 'черными' = 'чёрными'; 'черно' = 'чёрно'
    'ее' = 'её'; 'мое' = 'моё'; 'свое' = 'своё'; 'своем' = 'своём'
    'твое' = 'твоё'; 'твоем' = 'твоём'
}

function Get-HouseStyleRules {
    <#
    .SYNOPSIS
        The house text style rule set, as data. One record per transformation.
    #>
    return @(
        [pscustomobject]@{
            Name        = 'ellipsis'
            Description = 'Three or more dots, and the Unicode ellipsis, collapse to two dots.'
            Kind        = 'Regex'
            # A run collapses to one '..' - a Chinese '……' is two ellipsis characters, and rewriting
            # each one separately would yield '....' instead of the house form.
            Pattern     = '(?:\.{3,}|…+)'
            Replacement = '..'
        },
        [pscustomobject]@{
            Name        = 'long-dash'
            Description = 'En dash, em dash and horizontal bar become a plain hyphen.'
            Kind        = 'Regex'
            # A run collapses to one hyphen. Chinese sets its dash as a doubled em dash, and
            # rewriting each character separately would leave '--' where the house form is '-'.
            Pattern     = '[–—―]+'
            Replacement = '-'
        },
        [pscustomobject]@{
            Name        = 'yo'
            Description = 'Restores the Russian letter yo where it is unambiguously required.'
            Kind        = 'Dictionary'
            Pattern     = $null
            Replacement = $null
        }
    )
}

function Convert-HouseStyleText {
    <#
    .SYNOPSIS
        Applies the house text style to one piece of text and reports which rules fired.
    .PARAMETER Area
        Prose - markdown; fenced blocks, indented blocks and backtick spans are left alone.
        ResourceValue - one string resource value; a value that is wholly a URL, a path or a
        format placeholder is left alone.
    .PARAMETER Rules
        Rule names to apply. Omitted means every rule. An unknown name throws rather than being
        ignored, so a typo cannot silently disable a rule.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text,
        [ValidateSet('Prose', 'ResourceValue')][string]$Area = 'Prose',
        [string[]]$Rules
    )

    $all = Get-HouseStyleRules
    $selected = $all
    if ($Rules) {
        $unknown = $Rules | Where-Object { $_ -notin $all.Name }
        if ($unknown) { throw "Convert-HouseStyleText: unknown rule name(s): $($unknown -join ', ')" }
        $selected = $all | Where-Object { $_.Name -in $Rules }
    }

    $fired = [System.Collections.Generic.List[string]]::new()
    if ([string]::IsNullOrEmpty($Text)) {
        return [pscustomobject]@{ Text = $Text; RulesFired = @(); Changed = $false }
    }

    $converted = switch ($Area) {
        'ResourceValue' { Convert-HouseStyleResourceValue -Text $Text -Rules $selected -Fired $fired }
        default { Convert-HouseStyleProse -Text $Text -Rules $selected -Fired $fired }
    }

    return [pscustomobject]@{
        Text       = $converted
        RulesFired = @($fired)
        Changed    = ($converted -ne $Text)
    }
}

function Convert-HouseStyleSegment {
    # Applies the selected rules to one plain-text segment already known to be in scope.
    param(
        [AllowEmptyString()][string]$Text,
        [object[]]$Rules,
        [System.Collections.Generic.List[string]]$Fired
    )
    $out = $Text
    foreach ($rule in $Rules) {
        $before = $out
        if ($rule.Kind -eq 'Dictionary') {
            $out = Convert-HouseStyleYo -Text $out
        } else {
            $out = [regex]::Replace($out, $rule.Pattern, $rule.Replacement)
        }
        if ($out -ne $before -and -not $Fired.Contains($rule.Name)) { [void]$Fired.Add($rule.Name) }
    }
    return $out
}

function Convert-HouseStyleYo {
    # Whole-word only, in all three case forms. The lookarounds beat \b, which is unreliable
    # against Cyrillic in some .NET culture configurations.
    #
    # One compiled alternation, built once, rather than a pass per dictionary entry. The per-entry
    # form cost three regex sweeps per word and this function runs once per string resource value,
    # which on a 4469-key locale meant well over a million sweeps and minutes per file.
    param([AllowEmptyString()][string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return $Text }
    if (-not $script:HouseStyleYoRegex) {
        # Longest first, so a word that is a prefix of another cannot claim the match.
        $alternatives = ($script:HouseStyleYoMap.Keys |
            Sort-Object -Property Length -Descending |
            ForEach-Object { [regex]::Escape($_) }) -join '|'
        $script:HouseStyleYoRegex = [regex]::new(
            "(?<!\p{L})($alternatives)(?!\p{L})",
            [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor
            [System.Text.RegularExpressions.RegexOptions]::Compiled)
    }
    return $script:HouseStyleYoRegex.Replace($Text, {
            param($m)
            $hit = $m.Groups[1].Value
            $fixed = $script:HouseStyleYoMap[$hit.ToLowerInvariant()]
            if (-not $fixed) { return $hit }
            # Carry the original casing across, so ЕЩЕ, Еще and еще each keep their own form.
            if ($hit -ceq $hit.ToUpperInvariant()) { return $fixed.ToUpperInvariant() }
            if ([char]::IsUpper($hit[0])) { return $fixed.Substring(0, 1).ToUpperInvariant() + $fixed.Substring(1) }
            return $fixed
        })
}

function Convert-HouseStyleResourceValue {
    param(
        [AllowEmptyString()][string]$Text,
        [object[]]$Rules,
        [System.Collections.Generic.List[string]]$Fired
    )
    $trimmed = $Text.Trim()
    # A value that is wholly machine-readable is not prose: a literal '...' inside a path segment
    # or a URL is part of the address, and collapsing it would break the link.
    if ($trimmed -match '^[a-z][a-z0-9+.-]*://\S+$') { return $Text }
    # The path test additionally demands printable ASCII end to end. Chinese and Japanese set no
    # spaces between words, so "no whitespace and contains a slash" alone matched whole CJK
    # sentences - 'keyboard / mouse reference' in Chinese read as a file path and kept its ellipsis.
    if ($trimmed -notmatch '\s' -and $trimmed -match '[\\/]' -and $trimmed -match '^[\x20-\x7E]+$') { return $Text }
    if ($trimmed -match '^(?:%(?:\d+\$)?[a-zA-Z]|\s)+$') { return $Text }
    return Convert-HouseStyleSegment -Text $Text -Rules $Rules -Fired $Fired
}

function Convert-HouseStyleProse {
    param(
        [AllowEmptyString()][string]$Text,
        [object[]]$Rules,
        [System.Collections.Generic.List[string]]$Fired
    )
    $lines = $Text -split "`n"
    $inFence = $false
    $out = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $lines) {
        if ($line -match '^\s*```') { $inFence = -not $inFence; $out.Add($line); continue }
        if ($inFence) { $out.Add($line); continue }
        # An indented block is markdown's other code form, and the canon excludes code either way.
        if ($line -match '^(?:\s{4}|\t)') { $out.Add($line); continue }
        # Odd segments are the backtick spans themselves and are passed through untouched.
        $segments = $line -split '(`[^`]*`)'
        $rebuilt = for ($i = 0; $i -lt $segments.Length; $i++) {
            if ($i % 2 -eq 0) { Convert-HouseStyleSegment -Text $segments[$i] -Rules $Rules -Fired $Fired }
            else { $segments[$i] }
        }
        $out.Add(($rebuilt -join ''))
    }
    return ($out -join "`n")
}
