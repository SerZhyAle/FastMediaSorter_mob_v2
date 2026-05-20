$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $repoRoot 'doc-drift/GradleParser.ps1')

$fixtureRoot = Join-Path $PSScriptRoot 'fixtures'
$pins = Get-GradlePins -RepoRoot $fixtureRoot

Describe-Test -Name 'gradle.wrapper extracted from properties' -Body {
    Assert-Equal -Expected '9.4.1' -Actual $pins['gradle.wrapper'] -Message 'gradle.wrapper mismatch'
}

Describe-Test -Name 'agp extracted from plugins block' -Body {
    Assert-Equal -Expected '9.2.1' -Actual $pins['agp'] -Message 'agp mismatch'
}

Describe-Test -Name 'kotlin extracted from classpath' -Body {
    Assert-Equal -Expected '2.2.10' -Actual $pins['kotlin'] -Message 'kotlin mismatch'
}

Describe-Test -Name 'per-flavor min-sdk override respected' -Body {
    Assert-Equal -Expected '23' -Actual $pins['min-sdk.legacy'] -Message 'legacy minSdk mismatch'
    Assert-Equal -Expected '26' -Actual $pins['min-sdk.standard'] -Message 'standard minSdk mismatch'
}

Describe-Test -Name 'library coordinate extractor produces lib.* keys' -Body {
    Assert-Equal -Expected '2.59' -Actual $pins['lib.com.google.dagger:hilt-android'] -Message 'hilt runtime mismatch'
    Assert-Equal -Expected '2.7.0' -Actual $pins['lib.androidx.room:room-runtime'] -Message 'room runtime mismatch'
}

Describe-Test -Name 'missing source file throws with path in message' -Body {
    $ex = Assert-Throws -ScriptBlock { Get-GradlePins -RepoRoot 'C:\does\not\exist' } -Message 'missing root must throw'
    Assert-Match -Pattern 'C:\\does\\not\\exist' -Text $ex.Message -Message 'missing root path absent from exception'
}
