Set-StrictMode -Version Latest

$script:AndroidStringFormatFlagChars = '-#+ 0,(<'
$script:AndroidStringFormatConversions = 'bBhHsScCdoxXeEfgGaA'

function Get-AndroidStringFormatKind([char]$Conversion, [bool]$IsDateTime) {
    if ($IsDateTime) { return 'datetime' }
    $kind = switch -Regex ([string]$Conversion) {
        '[bB]' { 'boolean'; break }
        '[hH]' { 'hash'; break }
        '[sS]' { 'string'; break }
        '[cC]' { 'char'; break }
        '[doxX]' { 'int'; break }
        '[eEfgGaA]' { 'float'; break }
        default { 'other' }
    }
    return $kind
}

function New-AndroidStringFormatError([int]$Offset, [string]$Code, [string]$Message) {
    [pscustomobject]@{
        Offset = $Offset
        Code = $Code
        Message = $Message
    }
}

function Get-AndroidStringFormatAnalysis([AllowEmptyString()][string]$Value) {
    $errors = [System.Collections.Generic.List[object]]::new()
    $tokens = [System.Collections.Generic.List[object]]::new()

    if ($null -eq $Value) { $Value = '' }

    $length = $Value.Length
    $index = 0
    $nextImplicitArg = 1
    $lastArgIndex = $null

    while ($index -lt $length) {
        if ($Value[$index] -ne '%') {
            $index++
            continue
        }

        $start = $index
        $previousChar = if ($start -gt 0) { $Value[$start - 1] } else { [char]0 }
        $index++
        if ($index -ge $length) {
            $tokens.Add([pscustomobject]@{
                Offset = $start
                Kind = 'literal-percent'
                ArgIndex = $null
                ConsumesArgument = $false
            })
            break
        }

        $marker = $Value[$index]
        $looksLikeNumericPercent = [char]::IsDigit($previousChar) -and $marker -match '[,.;:!?\)]'
        if ($looksLikeNumericPercent) {
            $tokens.Add([pscustomobject]@{
                Offset = $start
                Kind = 'literal-percent'
                ArgIndex = $null
                ConsumesArgument = $false
            })
            continue
        }
        if ([char]::IsWhiteSpace($marker)) {
            $tokens.Add([pscustomobject]@{
                Offset = $start
                Kind = 'literal-percent'
                ArgIndex = $null
                ConsumesArgument = $false
            })
            continue
        }

        if ($marker -eq '%') {
            $tokens.Add([pscustomobject]@{
                Offset = $start
                Kind = 'literal-percent'
                ArgIndex = $null
                ConsumesArgument = $false
            })
            $index++
            continue
        }

        if ($marker -eq 'n') {
            $tokens.Add([pscustomobject]@{
                Offset = $start
                Kind = 'newline'
                ArgIndex = $null
                ConsumesArgument = $false
            })
            $index++
            continue
        }

        $startsFormat = [char]::IsDigit($marker) -or
            $script:AndroidStringFormatFlagChars.Contains([string]$marker) -or
            $script:AndroidStringFormatConversions.Contains([string]$marker) -or
            $marker -eq 't' -or
            $marker -eq 'T'
        if (-not $startsFormat) {
            $tokens.Add([pscustomobject]@{
                Offset = $start
                Kind = 'literal-percent'
                ArgIndex = $null
                ConsumesArgument = $false
            })
            continue
        }

        $argIndex = $null
        $usesExplicitIndex = $false
        $usesPreviousArgument = $false

        $digitsStart = $index
        while ($index -lt $length -and [char]::IsDigit($Value[$index])) {
            $index++
        }
        if ($index -gt $digitsStart -and $index -lt $length -and $Value[$index] -eq '$') {
            $argIndex = [int]$Value.Substring($digitsStart, $index - $digitsStart)
            $usesExplicitIndex = $true
            if ($argIndex -le 0) {
                $errors.Add((New-AndroidStringFormatError -Offset $start -Code 'invalid-argument-index' -Message 'Argument index in format specifier must be >= 1.'))
                $index = $start + 1
                continue
            }
            $index++
        }
        else {
            $index = $digitsStart
        }

        while ($index -lt $length -and $script:AndroidStringFormatFlagChars.Contains([string]$Value[$index])) {
            if ($Value[$index] -eq '<') { $usesPreviousArgument = $true }
            $index++
        }

        if ($usesPreviousArgument -and $usesExplicitIndex) {
            $errors.Add((New-AndroidStringFormatError -Offset $start -Code 'mixed-explicit-and-previous' -Message 'Format specifier cannot combine an explicit argument index with the < reuse flag.'))
            $index = $start + 1
            continue
        }

        if ($usesPreviousArgument) {
            if ($null -eq $lastArgIndex) {
                $errors.Add((New-AndroidStringFormatError -Offset $start -Code 'missing-previous-argument' -Message 'Format specifier uses < but there is no previous argument to reuse.'))
                $index = $start + 1
                continue
            }
            $argIndex = $lastArgIndex
        }

        while ($index -lt $length -and [char]::IsDigit($Value[$index])) {
            $index++
        }

        if ($index -lt $length -and $Value[$index] -eq '.') {
            $index++
            $precisionStart = $index
            while ($index -lt $length -and [char]::IsDigit($Value[$index])) {
                $index++
            }
            if ($index -eq $precisionStart) {
                $errors.Add((New-AndroidStringFormatError -Offset $start -Code 'invalid-precision' -Message 'Precision marker . in format specifier must be followed by digits.'))
                $index = $start + 1
                continue
            }
        }

        $isDateTime = $false
        if ($index -lt $length -and ($Value[$index] -eq 't' -or $Value[$index] -eq 'T')) {
            $isDateTime = $true
            $index++
            if ($index -ge $length -or -not [char]::IsLetter($Value[$index])) {
                $errors.Add((New-AndroidStringFormatError -Offset $start -Code 'invalid-datetime-conversion' -Message 'Date/time format specifier must end with a conversion letter after t/T.'))
                $index = $start + 1
                continue
            }
        }

        if ($index -ge $length) {
            $errors.Add((New-AndroidStringFormatError -Offset $start -Code 'missing-conversion' -Message 'Format specifier is missing a conversion character.'))
            break
        }

        $conversion = $Value[$index]
        if ($isDateTime) {
            if (-not [char]::IsLetter($conversion)) {
                $errors.Add((New-AndroidStringFormatError -Offset $start -Code 'invalid-datetime-conversion' -Message "Unsupported date/time conversion '$conversion'."))
                $index = $start + 1
                continue
            }
        }
        elseif (-not $script:AndroidStringFormatConversions.Contains([string]$conversion)) {
            $errors.Add((New-AndroidStringFormatError -Offset $start -Code 'invalid-conversion' -Message "Unsupported conversion '$conversion' in format specifier."))
            $index = $start + 1
            continue
        }

        $index++
        if ($null -eq $argIndex) {
            $argIndex = $nextImplicitArg
            $nextImplicitArg++
        }
        $lastArgIndex = $argIndex

        $tokens.Add([pscustomobject]@{
            Offset = $start
            Kind = (Get-AndroidStringFormatKind -Conversion $conversion -IsDateTime $isDateTime)
            ArgIndex = $argIndex
            ConsumesArgument = $true
        })
    }

    $argumentTokens = @($tokens | Where-Object { $_.ConsumesArgument })
    $signature = @($argumentTokens | ForEach-Object { '{0}:{1}' -f $_.ArgIndex, $_.Kind })
    $argIndexes = @($argumentTokens | ForEach-Object { $_.ArgIndex } | Sort-Object -Unique)
    if ($argIndexes.Count -gt 0) {
        $maxIndex = ($argIndexes | Measure-Object -Maximum).Maximum
        $expected = 1..$maxIndex
        $missing = @($expected | Where-Object { $argIndexes -notcontains $_ })
        if ($missing.Count -gt 0) {
            $errors.Add((New-AndroidStringFormatError -Offset 0 -Code 'argument-index-gap' -Message ("Argument indexes are not contiguous; missing {0}." -f ($missing -join ', '))))
        }
    }

    [pscustomobject]@{
        PercentCount = ([regex]::Matches($Value, '%')).Count
        Tokens = $tokens
        Errors = $errors
        ArgumentTokens = $argumentTokens
        Signature = $signature
        SignatureText = ($signature -join '|')
    }
}

function Compare-AndroidStringFormatContracts([string]$Key, [hashtable]$EntriesByLocale) {
    $findings = [System.Collections.Generic.List[object]]::new()
    $presentEntries = @($EntriesByLocale.GetEnumerator() | Sort-Object Key | ForEach-Object { $_.Value } | Where-Object { $null -ne $_ })
    if ($presentEntries.Count -lt 2) { return $findings }

    $needsComparison = $false
    foreach ($entry in $presentEntries) {
        if ($entry.Analysis.PercentCount -gt 0 -or $entry.Analysis.ArgumentTokens.Count -gt 0 -or $entry.Analysis.Errors.Count -gt 0) {
            $needsComparison = $true
            break
        }
    }
    if (-not $needsComparison) { return $findings }

    $reference = $presentEntries[0]
    foreach ($entry in $presentEntries | Select-Object -Skip 1) {
        if ($reference.Analysis.SignatureText -ne $entry.Analysis.SignatureText) {
            $referenceContract = if ([string]::IsNullOrWhiteSpace($reference.Analysis.SignatureText)) { '<none>' } else { $reference.Analysis.SignatureText }
            $entryContract = if ([string]::IsNullOrWhiteSpace($entry.Analysis.SignatureText)) { '<none>' } else { $entry.Analysis.SignatureText }
            $findings.Add([pscustomobject]@{
                Id = ('contract|{0}|{1}|{2}|{3}' -f $Key, $reference.Locale, $entry.Locale, $entryContract)
                Key = $Key
                Locale = $entry.Locale
                File = $entry.File
                Type = 'contract-mismatch'
                Message = ("Format contract mismatch: {0}={1}; {2}={3}" -f $reference.Locale, $referenceContract, $entry.Locale, $entryContract)
            })
        }
    }

    return $findings
}
