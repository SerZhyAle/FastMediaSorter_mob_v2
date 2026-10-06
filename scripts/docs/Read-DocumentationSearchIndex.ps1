<#
.SYNOPSIS
    Read and validate a complete v1 or sharded v2 documentation search index.
.DESCRIPTION
    Dot-sourced quality-gate helper; it returns page records or throws on invalid input and never exits.
#>
# Both quality gates consume the same v1/v2 index contract as the browser.
function Read-DocumentationSearchIndex {
    param([Parameter(Mandatory)][string]$Path)
    $payload = Get-Content -LiteralPath $Path -Raw -Encoding utf8 | ConvertFrom-Json
    if ($payload.PSObject.Properties['pages']) { return @($payload.pages) }
    if (-not $payload.PSObject.Properties['chunks']) { throw 'Search index has neither pages nor chunks.' }
    $pages = [System.Collections.Generic.List[object]]::new()
    $directory = Split-Path $Path -Parent
    foreach ($chunk in $payload.chunks) {
        if ($chunk.file -notmatch '^search-pages-(en|ru|uk)-\d+\.json$') { throw "Invalid search shard name: $($chunk.file)" }
        $part = Get-Content -LiteralPath (Join-Path $directory $chunk.file) -Raw -Encoding utf8 | ConvertFrom-Json
        if (-not $part.PSObject.Properties['pages']) { throw "Search shard has no pages: $($chunk.file)" }
        foreach ($page in $part.pages) { $pages.Add($page) }
    }
    if ($pages.Count -ne $payload.total_pages) { throw "Search count mismatch: expected $($payload.total_pages), got $($pages.Count)." }
    return $pages.ToArray()
}
