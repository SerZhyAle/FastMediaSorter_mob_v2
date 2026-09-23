# absent-dependency-claims.psd1 - claims a user document may make only while a dependency ships (S3445)
#
# Consumed by AbsentDependencyClaims.ps1, run by scripts/quality/assert-doc-pin-drift.ps1.
#
# Schema per entry:
#   name        = claim id, printed in every finding
#   coordinate  = Maven group:artifact whose presence makes the claim true
#   reason      = why the coordinate is gone, printed with every finding
#   docs        = repo-relative documents judged for the claim
#   patterns    = .NET regexes; a line matching any of them makes the claim
#
# The claim is judged only while the coordinate is ABSENT from gradle/libs.versions.toml and every
# build.gradle.kts. Declaring it again silences the entry, so re-adding the engine never needs this
# file edited - removing it again re-arms the check.
#
# A deliberate mention is excused with <!-- absent-dependency-ignore: <name> --> on the same line.

@{
    Claims = @(
        @{
            name       = 'mlkit-text-recognition'
            coordinate = 'com.google.mlkit:text-recognition'
            reason     = 'removed from every build by S0386; Tesseract recognizes all text, ML Kit only translates and identifies language'
            docs       = @(
                'docs/README.md', 'docs/README-ru.md', 'docs/README-uk.md',
                'docs/FAQ.md', 'docs/FAQ-ru.md', 'docs/FAQ-uk.md',
                'docs/LIMITATIONS.md', 'docs/LIMITATIONS-ru.md', 'docs/LIMITATIONS-uk.md',
                'docs/HOW_TO.md', 'docs/HOW_TO-ru.md', 'docs/HOW_TO-uk.md'
            )
            patterns   = @(
                '(?i)ML\s?Kit[^\r\n]{0,60}(recogni[stz]|OCR|распозна|розпізна)'
                '(?i)(hybrid|гибридн|гібридн)\w*\s+(OCR|engine|движ|руші|систем)'
                '(?i)ML\s?Kit\s*\+\s*Tesseract'
                '(?i)Tesseract\s*\+\s*(Google\s+)?ML\s?Kit'
                '(?i)(than|чем|ніж)\s+ML\s?Kit'
                '(?i)(starts with|начинает с|починає з)\s+ML\s?Kit'
                '(?i)Tesseract\s+(for|для)\s+[^,\r\n]+,\s*ML\s?Kit'
                '(?i)(If using|Для|If the engine is)\s+ML\s?Kit'
            )
        }
    )
}
