<#
.SYNOPSIS
    Helpers that read the addresses of the site held outside it - app sources, listings and READMEs (S4101).

.DESCRIPTION
    Dot-sourced by scripts/quality/assert-site-addresses.ps1 (dimension held-addresses) and its tests. The
    list of held addresses is docs/site-held-addresses.jsonl; this file finds what the holders actually carry
    so the gate can compare the two. Returns values and never exits.

    An address is the part of a https://serzhyale.github.io/FastMediaSorter_mob_v2/ URL after the base,
    fragment and query removed; the site root is written '/'. A URL of another serzhyale.github.io site is a
    sibling address and is kept whole.
#>
Set-StrictMode -Version Latest

$script:HeldOrigin = 'https://serzhyale.github.io/'
$script:HeldSiteBase = 'https://serzhyale.github.io/FastMediaSorter_mob_v2'
$script:HeldUrlRegex = 'https://serzhyale\.github\.io/[A-Za-z0-9_.~%/#?=&:+-]*'

function ConvertTo-GlobRegex {
    param([Parameter(Mandatory)][string]$Glob)
    $escaped = [regex]::Escape($Glob.Replace('\', '/'))
    $escaped = $escaped.Replace('\*\*/', '(?:.*/)?').Replace('\*\*', '.*').Replace('\*', '[^/]*').Replace('\?', '[^/]')
    return '^' + $escaped + '$'
}

# Repo-relative paths of every file a holder glob matches. The search starts at the longest directory
# prefix of the glob that holds no wildcard, so a narrow glob never walks the whole repository.
function Resolve-HolderFiles {
    param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string[]]$Globs)
    $root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    $found = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
    foreach ($glob in $Globs) {
        $normalized = $glob.Replace('\', '/')
        $segments = $normalized -split '/'
        $prefix = [System.Collections.Generic.List[string]]::new()
        for ($i = 0; $i -lt $segments.Count - 1; $i++) {
            if ($segments[$i] -match '[*?]') { break }
            if ($segments[$i]) { $prefix.Add($segments[$i]) }
        }
        $startDir = if ($prefix.Count -gt 0) { Join-Path $root ($prefix -join '/') } else { $root }
        if (-not (Test-Path -LiteralPath $startDir -PathType Container)) { continue }
        $regex = [regex]::new((ConvertTo-GlobRegex -Glob $normalized))
        $deep = $normalized.Contains('**') -or ($segments.Count - 1 - $prefix.Count) -gt 0
        $option = if ($deep) { [IO.SearchOption]::AllDirectories } else { [IO.SearchOption]::TopDirectoryOnly }
        foreach ($path in [IO.Directory]::EnumerateFiles($startDir, '*', $option)) {
            $rel = $path.Substring($root.Length + 1).Replace('\', '/')
            if ($regex.IsMatch($rel)) { [void]$found.Add($rel) }
        }
    }
    return , @($found)
}

function ConvertTo-HeldAddress {
    param([Parameter(Mandatory)][string]$Url)
    $clean = ($Url -split '[#?]')[0].TrimEnd('.', ',', ';', ')', ']', '>')
    if ($clean -eq $script:HeldSiteBase -or $clean -eq "$script:HeldSiteBase/") { return @{ Kind = 'site'; Address = '/' } }
    if ($clean.StartsWith("$script:HeldSiteBase/")) {
        return @{ Kind = 'site'; Address = $clean.Substring($script:HeldSiteBase.Length + 1) }
    }
    return @{ Kind = 'sibling'; Address = $clean }
}

# Literals the holder files carry, as @{ Site = address -> files; Sibling = url -> files }. A Kotlin
# `val NAME = "https://serzhyale.github.io/.."` that the same file interpolates as $NAME or ${NAME} is a
# prefix, not an address: its uses are expanded and its own definition line is not counted.
function Get-HeldAddressLiterals {
    param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Files)
    $root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    $site = @{}
    $sibling = @{}
    foreach ($rel in $Files) {
        $text = [IO.File]::ReadAllText((Join-Path $root $rel))
        if (-not $text.Contains('serzhyale.github.io')) { continue }
        $consts = @{}
        foreach ($m in [regex]::Matches($text, '(?m)\bval\s+(\w+)\s*=\s*"(https://serzhyale\.github\.io/[^"]*)"')) {
            $consts[$m.Groups[1].Value] = $m.Groups[2].Value
        }
        $prefixes = @{}
        foreach ($name in $consts.Keys) {
            if ($text -match ('\$\{?' + [regex]::Escape($name) + '\b')) { $prefixes[$name] = $true }
        }
        foreach ($line in ($text -split "`n")) {
            $isPrefixDefinition = $false
            foreach ($name in $prefixes.Keys) {
                if ($line -match ('\bval\s+' + [regex]::Escape($name) + '\s*=')) { $isPrefixDefinition = $true }
            }
            if ($isPrefixDefinition) { continue }
            $expanded = [regex]::Replace($line, '\$\{?(\w+)\}?', {
                    param($m)
                    if ($consts.ContainsKey($m.Groups[1].Value)) { $consts[$m.Groups[1].Value] } else { $m.Value }
                })
            foreach ($m in [regex]::Matches($expanded, $script:HeldUrlRegex)) {
                $held = ConvertTo-HeldAddress -Url $m.Value
                $target = if ($held.Kind -eq 'site') { $site } else { $sibling }
                if (-not $target.ContainsKey($held.Address)) {
                    $target[$held.Address] = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
                }
                [void]$target[$held.Address].Add($rel)
            }
        }
    }
    return @{ Site = $site; Sibling = $sibling }
}

# What the help-link resolver declares: the (page_id, slug) pairs it builds and its translated languages.
function Get-ResolverFacts {
    param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$HolderPath)
    $text = [IO.File]::ReadAllText((Join-Path $RepoRoot $HolderPath))
    $pages = [System.Collections.Generic.List[object]]::new()
    foreach ($m in [regex]::Matches($text, 'page\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')) {
        $pages.Add([pscustomobject]@{ PageId = $m.Groups[1].Value; Slug = $m.Groups[2].Value })
    }
    $languages = $null
    $declared = [regex]::Match($text, 'val\s+TRANSLATED_LANGUAGES[^=]*=\s*setOf\(([^)]*)\)')
    if ($declared.Success) {
        $languages = @([regex]::Matches($declared.Groups[1].Value, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
    }
    return @{ Pages = $pages; Languages = $languages }
}
