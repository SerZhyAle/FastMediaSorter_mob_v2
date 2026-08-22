<#
.SYNOPSIS
    Validate docs/DOCUMENT_REGISTRY.jsonl and its registered files.

.DESCRIPTION
    Exit codes: 0 = valid, 1 = validation errors, 2 = invalid invocation or unreadable registry.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Get-Matches {
    param([string] $Pattern)
    $fullPattern = Join-Path $RepoRoot ($Pattern -replace '/', [IO.Path]::DirectorySeparatorChar)
    @(Get-ChildItem -Path $fullPattern -File -ErrorAction SilentlyContinue)
}

function Get-PagePermalink {
    param([string] $Path)
    $head = @(Get-Content -LiteralPath $Path -TotalCount 12 -Encoding utf8 -ErrorAction SilentlyContinue)
    if ($head.Count -eq 0) { return $null }
    foreach ($line in $head) {
        if ($line -match '^\s*permalink:\s*(\S+)\s*$') { return $Matches[1].Trim("'", '"') }
    }
    return $null
}

function Get-DeclaredAddresses {
    <#
        S1803: every address a record announces by hand - its entry point and its translated
        siblings. The expanded per-page addresses are not here: those come from the pages themselves
        and cannot point at a file that does not exist.
    #>
    param([object] $Record)
    $addresses = [System.Collections.Generic.List[string]]::new()
    if ($Record.url) { [void]$addresses.Add([string]$Record.url) }
    if ($Record.localized_urls) {
        foreach ($property in $Record.localized_urls.PSObject.Properties) { [void]$addresses.Add([string]$property.Value) }
    }
    return $addresses
}

try {
    $registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
    if (-not (Test-Path -LiteralPath $registryPath)) { throw "Registry not found: $registryPath" }
    $errors = [System.Collections.Generic.List[string]]::new()
    $records = [System.Collections.Generic.List[object]]::new()
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $registryPath -Encoding utf8) {
        $lineNumber++
        if (-not $line.Trim()) { continue }
        try { $records.Add(($line | ConvertFrom-Json)) } catch { $errors.Add("L${lineNumber}: invalid JSON") }
    }
    $required = @('id', 'title', 'category', 'audience', 'paths', 'published', 'indexable', 'product_areas', 'update_triggers', 'generated')
    $seen = @{}
    foreach ($record in $records) {
        foreach ($field in $required) {
            if (-not ($record.PSObject.Properties.Name -contains $field)) { $errors.Add("$($record.id): missing $field") }
        }
        if ($record.id -notmatch '^[a-z0-9]+(?:-[a-z0-9]+)*$') { $errors.Add("$($record.id): invalid id") }
        if ($seen.ContainsKey($record.id)) { $errors.Add("$($record.id): duplicate id") } else { $seen[$record.id] = $true }
        if ($record.indexable -and (-not $record.published -or -not $record.url)) { $errors.Add("$($record.id): indexable record needs published=true and url") }
        foreach ($relativePath in @($record.paths)) {
            if ($relativePath -match '(^|/|\\)\.\.($|/|\\)' -or [IO.Path]::IsPathRooted($relativePath)) {
                $errors.Add("$($record.id): path escapes repo: $relativePath")
                continue
            }
            if ((Get-Matches -Pattern $relativePath).Count -eq 0) { $errors.Add("$($record.id): no file matches $relativePath") }
        }
        # S1803: a page withheld from the sitemap has to say why, in words a reader can re-judge in a
        # year - an exclusion list whose entries read "internal" becomes a place things disappear into.
        foreach ($exclusion in @($record.sitemap_exclude)) {
            if (-not $exclusion) { continue }
            if (-not $exclusion.path) {
                $errors.Add("$($record.id): sitemap_exclude entry without a path")
                continue
            }
            if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $exclusion.path))) {
                $errors.Add("$($record.id): sitemap_exclude names a missing file: $($exclusion.path)")
            }
            $words = @(($exclusion.reason -split '\s+') | Where-Object { $_ })
            if ($words.Count -lt 4) {
                $errors.Add("$($record.id): sitemap_exclude reason too thin for $($exclusion.path)")
            }
        }
        $excludedPaths = @(@($record.sitemap_exclude) | Where-Object { $_ -and $_.path } |
            ForEach-Object { ([string]$_.path).Replace('\', '/') })
        # S1803: an address written by hand in the record can point at a page that was renamed or never
        # existed, and a sitemap entry that answers with an error is worse than an unannounced page.
        # Expanded addresses are exempt by construction - they are read off the page they belong to.
        if ($record.published -and $record.indexable) {
            $pages = @{}
            foreach ($relativePath in @($record.paths)) {
                foreach ($file in (Get-Matches -Pattern $relativePath)) {
                    $permalink = Get-PagePermalink -Path $file.FullName
                    if ($permalink) { $pages[$permalink] = $file.FullName }
                }
            }
            foreach ($address in (Get-DeclaredAddresses -Record $record)) {
                if ($pages.Count -eq 0) { continue }
                if (-not $pages.ContainsKey($address)) {
                    $errors.Add("$($record.id): declared address resolves to no page: $address")
                }
            }

            # S1803: the other half of the same contract. Adding a page to an existing group must not
            # require a registry edit for it to be announced - so a page that must NOT be announced can
            # only be caught here. A file under an indexable record has to be one of three things: it
            # declares its own address, it is named in sitemap_exclude with a reason, or it is the
            # source of an address the record itself declares. Anything else is a page nobody decided
            # about, which is how an internal note reaches a search engine.
            $recordAddresses = @(Get-DeclaredAddresses -Record $record)
            foreach ($relativePath in @($record.paths)) {
                foreach ($file in (Get-Matches -Pattern $relativePath)) {
                    $relative = $file.FullName.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')
                    if ($excludedPaths -contains $relative) { continue }
                    if (Get-PagePermalink -Path $file.FullName) { continue }
                    # A record-level address has no front matter to read: its source is the file whose
                    # name the address ends in, and the site root is backed by index.html / README.md.
                    $backsRecordAddress = $false
                    foreach ($address in $recordAddresses) {
                        if ($address -eq "/$($file.Name)") { $backsRecordAddress = $true; break }
                        if ($address -eq '/' -and $file.Name -in @('index.html', 'README.md')) {
                            $backsRecordAddress = $true; break
                        }
                    }
                    if ($backsRecordAddress) { continue }
                    $errors.Add("$($record.id): $relative is neither announced nor excluded - give it a " +
                        "permalink, or add it to sitemap_exclude with a reason saying what it is and who it is for")
                }
            }
        }
    }
    if ($errors.Count -gt 0) {
        Write-Host "Document registry FAILED: $($errors.Count) error(s)" -ForegroundColor Red
        $errors | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        exit 1
    }
    Write-Host "Document registry PASS: $($records.Count) record(s)" -ForegroundColor Green
    exit 0
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 2
}
