$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $repoRoot 'doc-drift/DocParser.ps1')

$fixtureRoot = Join-Path $PSScriptRoot 'fixtures'
$manifest = Import-PowerShellDataFile -LiteralPath (Join-Path $fixtureRoot 'pins.sample.psd1')
$mentions = Get-DocMentions -Manifest $manifest -RepoRoot $fixtureRoot

Describe-Test -Name 'mentions for agp in TECH_REQUIREMENTS extract one value' -Body {
    $record = $mentions | Where-Object { $_.Pin -eq 'agp' -and $_.DocPath -eq 'dev/TECH_REQUIREMENTS.md' }
    Assert-Equal -Expected @('9.2.0') -Actual @($record.Mentions) -Message 'agp mentions mismatch'
}

Describe-Test -Name 'mentions for hilt in TECH_REQUIREMENTS keep both values without exclude' -Body {
    $record = $mentions | Where-Object { $_.Pin -eq 'hilt-android' -and $_.DocPath -eq 'dev/TECH_REQUIREMENTS.md' }
    Assert-Equal -Expected @('2.50','2.57.2') -Actual @($record.Mentions) -Message 'hilt mentions mismatch'
}

Describe-Test -Name 'mentions for kotlin in CLAUDE extract range token' -Body {
    $record = $mentions | Where-Object { $_.Pin -eq 'kotlin' -and $_.DocPath -eq 'CLAUDE.md' }
    Assert-Equal -Expected @('1.9+') -Actual @($record.Mentions) -Message 'kotlin range mismatch'
}

Describe-Test -Name 'null matcher produces empty mention list for agp in TECH_STACK' -Body {
    $record = $mentions | Where-Object { $_.Pin -eq 'agp' -and $_.DocPath -eq 'docs/TECH_STACK.md' }
    Assert-Equal -Expected @() -Actual @($record.Mentions) -Message 'agp tech stack mentions mismatch'
}

Describe-Test -Name 'exclude strips version-history span when configured' -Body {
    $manifestWithExclude = Import-PowerShellDataFile -LiteralPath (Join-Path $fixtureRoot 'pins.exclude.sample.psd1')
    $excludeMentions = Get-DocMentions -Manifest $manifestWithExclude -RepoRoot $fixtureRoot
    $record = $excludeMentions | Where-Object { $_.Pin -eq 'hilt-android' -and $_.DocPath -eq 'dev/TECH_REQUIREMENTS.md' }
    Assert-Equal -Expected @('2.50') -Actual @($record.Mentions) -Message 'exclude did not fence history section'
}
