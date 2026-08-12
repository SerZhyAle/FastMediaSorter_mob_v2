#requires -Version 7.0
<#
.SYNOPSIS
    Shared extraction and comparison helpers for ticket acceptance probes.

.DESCRIPTION
    Only explicit status-note markers form a contract:
      Probe literal: <static Timber text>
      Probe template: <Timber text with dynamic fields>
      Probe none: <named non-log evidence>

    Historical audit prose is deliberately not parsed. Callers provide the
    application roots explicitly (normally app_v2/src and wear/src).
#>

Set-StrictMode -Version Latest

function Get-SanitizedTimberCall {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Content,
        [Parameter(Mandatory)][int] $PrefixStart,
        [Parameter(Mandatory)][int] $OpenParenIndex
    )

    $buffer = [System.Text.StringBuilder]::new()
    [void]$buffer.Append($Content.Substring($PrefixStart, $OpenParenIndex - $PrefixStart))
    $depth = 0
    $inString = $false
    $inLineComment = $false
    $inBlockComment = $false
    $index = $OpenParenIndex
    $end = -1

    while ($index -lt $Content.Length) {
        $character = $Content[$index]
        if ($inLineComment) {
            if ($character -eq "`n") { $inLineComment = $false; [void]$buffer.Append($character) }
            else { [void]$buffer.Append(' ') }
            $index++
            continue
        }
        if ($inBlockComment) {
            if ($character -eq '*' -and ($index + 1) -lt $Content.Length -and $Content[$index + 1] -eq '/') {
                $inBlockComment = $false; [void]$buffer.Append('  '); $index += 2; continue
            }
            [void]$buffer.Append($(if ($character -eq "`n") { $character } else { ' ' }))
            $index++
            continue
        }
        if ($inString) {
            [void]$buffer.Append($character)
            if ($character -eq '\\' -and ($index + 1) -lt $Content.Length) {
                [void]$buffer.Append($Content[$index + 1]); $index += 2; continue
            }
            if ($character -eq '"') { $inString = $false }
            $index++
            continue
        }
        if ($character -eq '"') { $inString = $true; [void]$buffer.Append($character); $index++; continue }
        if ($character -eq '/' -and ($index + 1) -lt $Content.Length -and $Content[$index + 1] -eq '/') {
            $inLineComment = $true; [void]$buffer.Append('  '); $index += 2; continue
        }
        if ($character -eq '/' -and ($index + 1) -lt $Content.Length -and $Content[$index + 1] -eq '*') {
            $inBlockComment = $true; [void]$buffer.Append('  '); $index += 2; continue
        }
        [void]$buffer.Append($character)
        if ($character -eq '(') { $depth++ }
        if ($character -eq ')') {
            $depth--
            if ($depth -eq 0) { $end = $index; break }
        }
        $index++
    }
    if ($end -lt 0) { $end = [Math]::Min($Content.Length - 1, $OpenParenIndex + 2000) }
    return @{ End = $end; Span = $buffer.ToString() }
}

function ConvertTo-ProbeTemplate {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string] $Value)

    $normalized = $Value.Trim()
    $normalized = [regex]::Replace($normalized, '\\.', '?')
    $normalized = [regex]::Replace($normalized, '%(?:\d+\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z]', '?')
    $normalized = [regex]::Replace($normalized, '\$\{[^}]+\}', '?')
    $normalized = [regex]::Replace($normalized, '\$[A-Za-z_][A-Za-z0-9_]*', '?')
    $normalized = [regex]::Replace($normalized, '<[^>]+>', '?')
    return $normalized
}

function Get-TimberProbeTemplates {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string[]] $SourceRoots)

    $opener = [regex]'Timber\.(?<level>[diwe])\('
    $ticketPrefix = [regex]'^Timber\.d\(\s*"(?<ticket>S\d{4}):\s*(?<message>(?:\\.|[^"\\])*)"'
    $results = [System.Collections.Generic.List[object]]::new()

    foreach ($root in $SourceRoots | Where-Object { Test-Path -LiteralPath $_ }) {
        $files = Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' |
            Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
        foreach ($file in $files) {
            $content = Get-Content -LiteralPath $file.FullName -Raw
            if ([string]::IsNullOrEmpty($content)) { continue }
            foreach ($match in $opener.Matches($content)) {
                $lineStart = $content.LastIndexOf("`n", $match.Index) + 1
                $lineBefore = $content.Substring($lineStart, $match.Index - $lineStart).TrimStart()
                if ($lineBefore.StartsWith('//') -or $lineBefore.StartsWith('*')) { continue }
                $call = Get-SanitizedTimberCall -Content $content -PrefixStart $match.Index `
                    -OpenParenIndex ($match.Index + $match.Length - 1)
                $prefix = $ticketPrefix.Match($call.Span)
                if (-not $prefix.Success) { continue }
                $results.Add([pscustomobject]@{
                    Ticket = $prefix.Groups['ticket'].Value
                    Template = ConvertTo-ProbeTemplate $prefix.Groups['message'].Value
                    File = $file.FullName
                    Line = ($content.Substring(0, $match.Index) -split "`n").Count
                    Level = 'd'
                })
            }
        }
    }
    return $results
}

function Get-TicketAcceptanceProbeContracts {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string] $CatalogPath)

    if (-not (Test-Path -LiteralPath $CatalogPath)) { throw "Spec catalog not found: $CatalogPath" }
    $contracts = [System.Collections.Generic.List[object]]::new()
    foreach ($line in Get-Content -LiteralPath $CatalogPath) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try { $record = $line | ConvertFrom-Json } catch { continue }
        $statusNote = if ($record.PSObject.Properties.Name -contains 'statusNote') { [string]$record.statusNote } else { '' }
        if ($record.status -ne 'BlockNeedUserTest' -or [string]::IsNullOrWhiteSpace($statusNote)) { continue }
        foreach ($marker in [regex]::Matches($statusNote, '(?im)^\s*Probe\s+(?<kind>literal|template|none)\s*:\s*(?<value>.+?)\s*$')) {
            $value = $marker.Groups['value'].Value.Trim()
            if ([string]::IsNullOrWhiteSpace($value)) { continue }
            $kind = $marker.Groups['kind'].Value.ToLowerInvariant()
            $expected = $value
            if ($kind -ne 'none') {
                $ticketPrefix = [regex]::Match($value, '^(?<ticket>S\d{4}):\s*(?<message>.+)$')
                if ($ticketPrefix.Success -and $ticketPrefix.Groups['ticket'].Value -eq $record.id) {
                    $expected = $ticketPrefix.Groups['message'].Value
                }
            }
            $contracts.Add([pscustomobject]@{
                Ticket = $record.id
                Kind = $kind
                Expected = ConvertTo-ProbeTemplate $expected
                Evidence = $value
                Source = 'statusNote'
            })
        }
    }
    return $contracts
}

function Test-TicketAcceptanceProbeNote {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Ticket,
        [Parameter(Mandatory)][string] $StatusNote,
        [Parameter(Mandatory)][string[]] $SourceRoots
    )

    $templates = Get-TimberProbeTemplates -SourceRoots $SourceRoots
    $results = [System.Collections.Generic.List[object]]::new()
    foreach ($marker in [regex]::Matches($StatusNote, '(?im)^\s*Probe\s+(?<kind>literal|template|none)\s*:\s*(?<value>.+?)\s*$')) {
        $kind = $marker.Groups['kind'].Value.ToLowerInvariant()
        $value = $marker.Groups['value'].Value.Trim()
        if ($kind -eq 'none') {
            $outcome = if ($value.Length -ge 8) { 'pass' } else { 'invalid-alternative-evidence' }
            $results.Add([pscustomobject]@{ Outcome = $outcome; Expected = $value })
            continue
        }
        $expectedMatch = [regex]::Match($value, '^(?<ticket>S\d{4}):\s*(?<message>.+)$')
        if (-not $expectedMatch.Success) {
            $results.Add([pscustomobject]@{ Outcome = 'invalid-ticket-prefix'; Expected = $value })
            continue
        }
        if ($expectedMatch.Groups['ticket'].Value -ne $Ticket) {
            $results.Add([pscustomobject]@{ Outcome = 'foreign-ticket-expectation'; Expected = $value })
            continue
        }
        $expected = ConvertTo-ProbeTemplate $expectedMatch.Groups['message'].Value
        $match = @($templates | Where-Object { $_.Ticket -eq $Ticket -and $_.Template -eq $expected })
        $outcome = if ($match.Count -gt 0) { 'pass' } else { 'missing-source-template' }
        $results.Add([pscustomobject]@{ Outcome = $outcome; Expected = $value })
    }
    return $results
}

function Test-TicketAcceptanceProbeContracts {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $CatalogPath,
        [Parameter(Mandatory)][string[]] $SourceRoots
    )

    $templates = Get-TimberProbeTemplates -SourceRoots $SourceRoots
    $contracts = Get-TicketAcceptanceProbeContracts -CatalogPath $CatalogPath
    $results = [System.Collections.Generic.List[object]]::new()
    foreach ($contract in $contracts) {
        if ($contract.Kind -eq 'none') {
            $outcome = if ($contract.Evidence.Trim().Length -ge 8) { 'pass' } else { 'invalid-alternative-evidence' }
            $results.Add([pscustomobject]@{ Ticket = $contract.Ticket; Outcome = $outcome; Reason = 'named alternative evidence'; Expected = $contract.Expected })
            continue
        }
        $matches = @($templates | Where-Object { $_.Ticket -eq $contract.Ticket -and $_.Template -eq $contract.Expected })
        $outcome = if ($matches.Count -gt 0) { 'pass' } else { 'missing-source-template' }
        $results.Add([pscustomobject]@{
            Ticket = $contract.Ticket
            Outcome = $outcome
            Reason = if ($outcome -eq 'pass') { $matches[0].File } else { 'no matching Timber.d template' }
            Expected = $contract.Expected
        })
    }
    return $results
}
