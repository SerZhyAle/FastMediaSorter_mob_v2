<#
.SYNOPSIS
    Moves every published docs/ address with a legacy locale suffix to the one hyphen form and repoints
    every reference to it (S4101).

.DESCRIPTION
    SITE-STRUCTURE 0.1 rule 8 asks for one form of locale sibling inside a page group and a forwarder at
    every address that moves. The published address of a docs/ page is its `permalink:` front matter, and
    S1211 renamed the files to `<name>-ru.md` while freezing the permalinks because no forwarder existed.
    Forwarders exist now (scripts/docs/generate-site-redirects.ps1), so this tool finishes the job:

      1. every docs/**/*.md `permalink:` under /docs/ whose file name ends `_RU`, `_UK`, `.ru` or `.uk`
         before `.html` becomes `-ru` / `-uk`, and one ending `_EN` loses the suffix;
      2. the `permalink:` lines are rewritten byte-preserving (line endings and BOM kept);
      3. one record per moved address is appended to docs/site-redirects.jsonl, skipping existing ones;
      4. every reference to an old file name - matched as a whole token - is rewritten over docs/ (except
         the redirect manifest), documentation/, the root pages, scripts/ (not the *.tests fixtures), the
         app and watch sources, _data/, _includes/, _layouts/, listings and READMEs.

    Run scripts/docs/generate-site-redirects.ps1 afterwards to write the forwarder pages, then the
    generators that emit moved addresses (render-*.ps1, generate-glossary.ps1, generate-subject-index.ps1),
    and scripts/document_registry/generate.ps1 for sitemap.xml. The tool is idempotent: a second run finds
    nothing to do.

.PARAMETER WhatIf
    Print the planned moves and the number of files that would change; write nothing.

.PARAMETER RepoRoot
    Repository root; overridable so a copy can be migrated.

.PARAMETER Today
    Date written into the new redirect records (yyyy-MM-dd); defaults to the current date.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/migrate-locale-addresses.ps1 -WhatIf

.NOTES
    Exit codes:
      0  the moves were applied, or there was nothing to do (-WhatIf prints the plan and exits 0)
      2  could not proceed: the root is not a checkout, a new address collides with a published page or with
         another move, or a permalink could not be rewritten
#>
[CmdletBinding()]
param(
    [switch] $WhatIf,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [string] $Today = (Get-Date -Format 'yyyy-MM-dd')
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/site-addresses.ps1')

$root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
if (-not (Test-Path -LiteralPath (Join-Path $root 'docs') -PathType Container)) {
    Write-Host "migrate-locale-addresses: '$root' has no docs/ directory." -ForegroundColor Red
    exit 2
}

$reason = 'Locale suffix unified to the hyphen form (SITE-STRUCTURE rule 8)'
$utf8NoBom = [Text.UTF8Encoding]::new($false)

function Get-NewFileName([string]$Name) {
    if ($Name -match '^(?<stem>.+?)(?:_RU|\.ru)\.html$') { return $Matches['stem'] + '-ru.html' }
    if ($Name -match '^(?<stem>.+?)(?:_UK|\.uk)\.html$') { return $Matches['stem'] + '-uk.html' }
    if ($Name -match '^(?<stem>.+)_EN\.html$') { return $Matches['stem'] + '.html' }
    return $null
}

# 1. The moves, read from the permalinks of the published docs pages.
$moves = [System.Collections.Generic.List[object]]::new()
$sources = Get-ChildItem -LiteralPath (Join-Path $root 'docs') -Recurse -File -Filter *.md |
    Where-Object { $_.FullName.Substring($root.Length + 1).Replace('\', '/') -notmatch '^docs/(content|contracts|settings|icons|flavors)/' }
foreach ($file in $sources) {
    $permalink = Read-FrontMatterPermalink -FullName $file.FullName
    if (-not $permalink -or -not $permalink.StartsWith('/docs/')) { continue }
    $newName = Get-NewFileName ($permalink -replace '^.*/', '')
    if (-not $newName) { continue }
    $newPermalink = ($permalink -replace '[^/]*$', '') + $newName
    $moves.Add([pscustomobject]@{
            Source = $file.FullName
            Rel = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
            From = $permalink.TrimStart('/')
            To = $newPermalink.TrimStart('/')
        })
}

$published = Get-PublishedAddresses -RepoRoot $root
$seenTargets = @{}
$collisions = 0
foreach ($move in $moves) {
    if ($published.ContainsKey($move.To) -or $seenTargets.ContainsKey($move.To)) {
        Write-Host "migrate-locale-addresses: '$($move.From)' would move to '$($move.To)', which another page already answers." -ForegroundColor Red
        $collisions++
    }
    $seenTargets[$move.To] = $true
}
if ($collisions -gt 0) {
    Write-Error "migrate-locale-addresses: $collisions collision(s) listed above; nothing was written. Resolve the colliding page and rerun." -ErrorAction Continue
    exit 2
}

# Old file name -> new file name, one pair per distinct name. Moves made by an earlier run are read back from
# the redirect manifest, so a stray reference written after the move is repointed by a rerun.
$nameMap = @{}
foreach ($move in $moves) { $nameMap[($move.From -replace '^.*/', '')] = ($move.To -replace '^.*/', '') }
foreach ($record in (Read-JsonlRecords -Path (Join-Path $root 'docs/site-redirects.jsonl'))) {
    if ($null -eq $record.to_path) { continue }
    $oldName = ([string]$record.from_path) -replace '^.*/', ''
    $newName = Get-NewFileName $oldName
    if ($newName -and $newName -ceq (([string]$record.to_path) -replace '^.*/', '')) { $nameMap[$oldName] = $newName }
}
if ($nameMap.Count -eq 0) {
    Write-Host 'migrate-locale-addresses: nothing to do.'
    exit 0
}

Write-Host "migrate-locale-addresses: $($moves.Count) address(es) to move."
foreach ($move in ($moves | Sort-Object From)) { Write-Host "  $($move.From) -> $($move.To)" }

# 4 (planned first so the plan can report it). Files carrying an old file name as a whole token.
$referenceRoots = @(
    @{ Dir = 'docs'; Filter = '*.md', '*.json', '*.jsonl', '*.html', '*.txt' },
    @{ Dir = 'documentation'; Filter = '*.html', '*.js', '*.json' },
    @{ Dir = 'scripts'; Filter = '*.ps1', '*.psd1', '*.cjs', '*.js', '*.json' },
    @{ Dir = 'app_v2/src/main'; Filter = '*.kt' },
    @{ Dir = 'wear/src/main'; Filter = '*.kt' },
    @{ Dir = '_data'; Filter = '*.json', '*.yml' },
    @{ Dir = '_includes'; Filter = '*.html' },
    @{ Dir = '_layouts'; Filter = '*.html' },
    @{ Dir = 'play'; Filter = '*.txt', '*.md' },
    @{ Dir = 'fastlane'; Filter = '*.txt', '*.md' },
    @{ Dir = 'store_assets'; Filter = '*.md', '*.txt' },
    @{ Dir = 'meta'; Filter = '*.md', '*.txt' },
    @{ Dir = '.github'; Filter = '*.yml', '*.md' })
$skipPath = '(^|/)[^/]+\.tests/|^docs/site-redirects\.jsonl$|^sitemap\.xml$'

$candidates = [System.Collections.Generic.List[string]]::new()
foreach ($entry in $referenceRoots) {
    $dir = Join-Path $root $entry.Dir
    if (-not (Test-Path -LiteralPath $dir)) { continue }
    foreach ($filter in $entry.Filter) {
        foreach ($f in [IO.Directory]::EnumerateFiles($dir, $filter, [IO.SearchOption]::AllDirectories)) {
            $rel = $f.Substring($root.Length + 1).Replace('\', '/')
            if ($rel -notmatch $skipPath) { $candidates.Add($rel) }
        }
    }
}
foreach ($f in [IO.Directory]::EnumerateFiles($root, '*', [IO.SearchOption]::TopDirectoryOnly)) {
    $rel = $f.Substring($root.Length + 1)
    if ($rel -match '\.(html|md)$' -and $rel -notmatch $skipPath) { $candidates.Add($rel) }
}

$names = @($nameMap.Keys | Sort-Object { $_.Length } -Descending | ForEach-Object { [regex]::Escape($_) })
$tokenRegex = [regex]::new('(?<![A-Za-z0-9_])(?:' + ($names -join '|') + ')', [Text.RegularExpressions.RegexOptions]::Compiled)
$evaluator = [Text.RegularExpressions.MatchEvaluator] { param($m) $nameMap[$m.Value] }

$referenceEdits = [System.Collections.Generic.List[object]]::new()
foreach ($rel in ($candidates | Sort-Object -Unique)) {
    $path = Join-Path $root $rel
    $bytes = [IO.File]::ReadAllBytes($path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $text = [Text.UTF8Encoding]::new($false).GetString($bytes, $(if ($hasBom) { 3 } else { 0 }), $bytes.Length - $(if ($hasBom) { 3 } else { 0 }))
    if (-not $tokenRegex.IsMatch($text)) { continue }
    $referenceEdits.Add([pscustomobject]@{ Path = $path; Rel = $rel; HasBom = $hasBom; Text = $tokenRegex.Replace($text, $evaluator) })
}
Write-Host "migrate-locale-addresses: $($referenceEdits.Count) file(s) reference an old address."
foreach ($edit in ($referenceEdits | Where-Object { $_.Rel -notmatch '^documentation/' } | Sort-Object Rel)) { Write-Host "  ~ $($edit.Rel)" }

if ($WhatIf) {
    Write-Host 'migrate-locale-addresses: -WhatIf, nothing written.'
    exit 0
}

# 2. Permalink lines. A source whose own text also names an old address is part of the reference edits and
#    is written once, with both changes.
$editByPath = @{}
foreach ($edit in $referenceEdits) { $editByPath[$edit.Path] = $edit }
foreach ($move in $moves) {
    $path = $move.Source
    $bytes = [IO.File]::ReadAllBytes($path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $text = if ($editByPath.ContainsKey($path)) { $editByPath[$path].Text }
    else { [Text.UTF8Encoding]::new($false).GetString($bytes, $(if ($hasBom) { 3 } else { 0 }), $bytes.Length - $(if ($hasBom) { 3 } else { 0 })) }
    $pattern = '(?m)^(permalink:\s*["'']?)/' + [regex]::Escape($move.From) + '(["'']?)\s*$'
    $new = [regex]::Replace($text, $pattern, { param($m) $m.Groups[1].Value + '/' + $move.To + $m.Groups[2].Value }, 'None')
    if ($new -ceq $text -and -not $editByPath.ContainsKey($path)) {
        Write-Host "migrate-locale-addresses: could not rewrite the permalink of $($move.Rel)." -ForegroundColor Red
        exit 2
    }
    [IO.File]::WriteAllText($path, $new, [Text.UTF8Encoding]::new($hasBom))
    $editByPath.Remove($path)
}

# 4. References.
foreach ($edit in $editByPath.Values) {
    [IO.File]::WriteAllText($edit.Path, $edit.Text, [Text.UTF8Encoding]::new($edit.HasBom))
}

# 3. Redirect records.
$manifest = Join-Path $root 'docs/site-redirects.jsonl'
$existing = @{}
foreach ($record in (Read-JsonlRecords -Path $manifest)) { $existing[[string]$record.from_path] = $true }
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($move in ($moves | Sort-Object From)) {
    if ($existing.ContainsKey($move.From)) { continue }
    $lines.Add(([ordered]@{ from_path = $move.From; to_path = $move.To; reason = $reason; ticket = 'S4101'; date = $Today } | ConvertTo-Json -Compress))
}
if ($lines.Count -gt 0) {
    $prefix = ''
    if ((Test-Path -LiteralPath $manifest) -and (Get-Item -LiteralPath $manifest).Length -gt 0) {
        $tail = [IO.File]::ReadAllText($manifest)
        if (-not $tail.EndsWith("`n")) { $prefix = "`n" }
    }
    [IO.File]::AppendAllText($manifest, $prefix + ($lines -join "`n") + "`n", $utf8NoBom)
}
Write-Host "migrate-locale-addresses: moved $($moves.Count) permalink(s), repointed $($referenceEdits.Count) file(s), added $($lines.Count) redirect record(s)."
Write-Host 'Next: scripts/docs/generate-site-redirects.ps1, the generators that emit moved addresses, scripts/document_registry/generate.ps1.'
exit 0
