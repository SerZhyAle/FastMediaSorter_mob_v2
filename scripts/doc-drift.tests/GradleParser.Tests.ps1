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

# S1496: a library coordinate declared in both modules must carry the same version. Both directions
# are covered on purpose - a suite that only proves the passing side would not have caught the
# defect these fixtures were added to fix, where the whole file stopped running and nothing said so.

Describe-Test -Name 'coordinate shared by both modules at one version resolves without throwing' -Body {
    Assert-Equal -Expected '2.59' -Actual $pins['lib.com.google.dagger:hilt-android'] -Message 'shared coordinate mismatch'
}

Describe-Test -Name 'coordinate diverging between modules throws naming both versions' -Body {
    $divergentRoot = Join-Path $PSScriptRoot 'fixtures-divergent'
    if (Test-Path -LiteralPath $divergentRoot) {
        Remove-Item -LiteralPath $divergentRoot -Recurse -Force
    }
    try {
        Copy-Item -LiteralPath $fixtureRoot -Destination $divergentRoot -Recurse -Force
        $wearFixture = Join-Path $divergentRoot 'wear/build.gradle.kts'
        (Get-Content -LiteralPath $wearFixture -Raw -Encoding UTF8).
            Replace('com.google.dagger:hilt-android:2.59', 'com.google.dagger:hilt-android:2.48') |
            Set-Content -LiteralPath $wearFixture -Encoding UTF8 -NoNewline

        $ex = Assert-Throws -ScriptBlock { Get-GradlePins -RepoRoot $divergentRoot } -Message 'diverging coordinate must throw'
        Assert-Match -Pattern 'hilt-android' -Text $ex.Message -Message 'coordinate name absent from exception'
        Assert-Match -Pattern '2\.59' -Text $ex.Message -Message 'app_v2 version absent from exception'
        Assert-Match -Pattern '2\.48' -Text $ex.Message -Message 'wear version absent from exception'
    }
    finally {
        if (Test-Path -LiteralPath $divergentRoot) {
            Remove-Item -LiteralPath $divergentRoot -Recurse -Force
        }
    }
}
