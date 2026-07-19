$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $repoRoot 'doc-drift/Comparator.ps1')
. (Join-Path $repoRoot 'doc-drift/Output.ps1')

$gradle = @{
    agp = '9.2.1'
    kotlin = '2.2.10'
    'hilt-android' = '2.59'
    media3 = '1.2.1'
    nanohttpd = '2.3.1'
}

Describe-Test -Name 'agp drifted produces FAIL record' -Body {
    $records = Compare-PinsToDocs -GradlePins $gradle -DocMentions @(
        [pscustomobject]@{ Pin = 'agp'; DocPath = 'dev/TECH_REQUIREMENTS.md'; Required = $true; Mentions = @('9.2.0'); Policy = 'allMustMatch' }
    )
    Assert-Equal -Expected 'FAIL' -Actual $records[0].Status -Message 'agp status mismatch'
    Assert-Equal -Expected '9.2.1' -Actual $records[0].Gradle -Message 'agp gradle mismatch'
    Assert-Equal -Expected '9.2.0' -Actual $records[0].DocValue -Message 'agp doc value mismatch'
}

Describe-Test -Name 'multi-mention divergence produces INCONSISTENT record' -Body {
    $records = Compare-PinsToDocs -GradlePins $gradle -DocMentions @(
        [pscustomobject]@{ Pin = 'hilt-android'; DocPath = 'dev/TECH_REQUIREMENTS.md'; Required = $true; Mentions = @('2.50','2.57.2'); Policy = 'allMustMatch' }
    )
    Assert-Equal -Expected 'INCONSISTENT' -Actual $records[0].Status -Message 'hilt status mismatch'
    Assert-Match -Pattern '2\.50 vs 2\.57\.2' -Text $records[0].DocValue -Message 'hilt inconsistent payload mismatch'
}

Describe-Test -Name 'repeated identical mentions collapse to PASS not first-char' -Body {
    # Regression (S1075): two identical mentions must compare as the full version,
    # not the first character of the collapsed scalar string ("2" from "2.59").
    $records = Compare-PinsToDocs -GradlePins $gradle -DocMentions @(
        [pscustomobject]@{ Pin = 'hilt-android'; DocPath = 'dev/TECH_REQUIREMENTS.md'; Required = $true; Mentions = @('2.59','2.59'); Policy = 'allMustMatch' }
    )
    Assert-Equal -Expected 'PASS' -Actual $records[0].Status -Message 'identical mentions must PASS'
    Assert-Equal -Expected '2.59' -Actual $records[0].DocValue -Message 'collapsed doc value must be the full version, not first char'
}

Describe-Test -Name 'exact match produces PASS record' -Body {
    $records = Compare-PinsToDocs -GradlePins $gradle -DocMentions @(
        [pscustomobject]@{ Pin = 'media3'; DocPath = 'dev/TECH_REQUIREMENTS.md'; Required = $true; Mentions = @('1.2.1'); Policy = 'allMustMatch' }
    )
    Assert-Equal -Expected 'PASS' -Actual $records[0].Status -Message 'media3 status mismatch'
}

Describe-Test -Name 'range marker satisfied by gradle version produces WARN record' -Body {
    $records = Compare-PinsToDocs -GradlePins $gradle -DocMentions @(
        [pscustomobject]@{ Pin = 'kotlin'; DocPath = 'CLAUDE.md'; Required = $true; Mentions = @('1.9+'); Policy = 'allMustMatch' }
    )
    Assert-Equal -Expected 'WARN' -Actual $records[0].Status -Message 'kotlin warn mismatch'
}

Describe-Test -Name 'missing required mention produces MISSING record' -Body {
    $records = Compare-PinsToDocs -GradlePins $gradle -DocMentions @(
        [pscustomobject]@{ Pin = 'agp'; DocPath = 'dev/TECH_REQUIREMENTS.md'; Required = $true; Mentions = @(); Policy = 'allMustMatch' }
    )
    Assert-Equal -Expected 'MISSING' -Actual $records[0].Status -Message 'missing required mismatch'
}

Describe-Test -Name 'missing optional mention produces SKIP record' -Body {
    $records = Compare-PinsToDocs -GradlePins $gradle -DocMentions @(
        [pscustomobject]@{ Pin = 'agp'; DocPath = 'docs/TECH_STACK.md'; Required = $false; Mentions = @(); Policy = 'allMustMatch' }
    )
    Assert-Equal -Expected 'SKIP' -Actual $records[0].Status -Message 'optional skip mismatch'
}

Describe-Test -Name 'report formatter preserves manifest order from comparator' -Body {
    $records = Compare-PinsToDocs -GradlePins $gradle -DocMentions @(
        [pscustomobject]@{ Pin = 'kotlin'; DocPath = 'CLAUDE.md'; Required = $true; Mentions = @('1.9+'); Policy = 'allMustMatch' },
        [pscustomobject]@{ Pin = 'agp'; DocPath = 'dev/TECH_REQUIREMENTS.md'; Required = $true; Mentions = @('9.2.0'); Policy = 'allMustMatch' }
    )
    $lines = Format-DriftReport -Records $records
    Assert-Match -Pattern '^WARN \| kotlin' -Text $lines[0] -Message 'first line must preserve first manifest pin'
    Assert-Match -Pattern '^FAIL \| agp' -Text $lines[1] -Message 'second line must preserve second manifest pin'
}
