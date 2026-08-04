# pins.psd1 - declarative pin manifest consumed by DocParser.ps1 + Comparator.ps1
#
# Schema per entry (PLAN/S0271_truth_drift_detection/DECISIONS.md D-1):
#   name         = canonical pin id (matches GradleParser key when same)
#   gradleKey    = key into Get-GradlePins output (defaults to name if omitted)
#   docs         = hashtable doc-path -> @{ required = $true|$false; matcher = '<regex>' | $null }
#   policy       = 'allMustMatch' (default) | 'firstOnly'
#   exclude      = array of regex patterns; matched spans stripped before matcher runs
#
# Conventions:
#   - Matchers capture the version into named group 'v'.
#   - 'matcher = $null' AND 'required = $true' -> SKIP not MISSING (D-2 escape hatch).
#   - For TECH_REQUIREMENTS.md table rows the artifact is enclosed in backticks ` ` `;
#     matchers anchor the closing backtick + pipe to disambiguate (e.g. 'hilt-android' vs
#     'hilt-android-compiler' both start with the same string).
#   - 'exclude' is currently unused; reserved for §11 history fencing when needed.
#   - CLAUDE.md and docs/TECH_STACK.md pin mentions are OWNED by
#     scripts/quality/generate-toolchain-pins.ps1 (a managed generated block, gated by
#     doc-pins-sync in post-change). Those two docs cannot drift, so their pins are
#     required = $false here (S1075). This checker's unique coverage is the hand-maintained
#     dev/TECH_REQUIREMENTS.md library/platform tables. Do NOT re-enable required = $true
#     for a generated-block doc - it only re-introduces format-mismatch MISSING noise.

@{
    Pins = @(

        # ============================================================
        # Class-1: build tools
        # ============================================================

        @{
            name      = 'gradle.wrapper'
            gradleKey = 'gradle.wrapper'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\| Gradle\s+\|\s*(?<v>[\d\.]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'agp'
            gradleKey = 'agp'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = 'Android Gradle Plugin\s*\|\s*(?<v>[\d\.]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'kotlin'
            gradleKey = 'kotlin'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = 'Kotlin version\s*\|\s*(?<v>[\d\.]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'ksp'
            gradleKey = 'ksp'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = 'KSP version\s*\|\s*(?<v>[\d\.]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'compose-plugin'
            gradleKey = 'compose-plugin'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        # hilt-plugin, navigation-safe-args, chaquopy: parsed for completeness
        # but not currently named in any documentation file with a captured version.
        # Kept as entries so the manifest mirrors the Class-1 list from strategic §5.1.
        @{
            name      = 'hilt-plugin'
            gradleKey = 'hilt-plugin'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'navigation-safe-args'
            gradleKey = 'navigation-safe-args'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                # Dead: the Safe-Args Gradle plugin is not applied in this repo and the
                # doc row was removed (S1075). Not required anywhere; kept as a manifest
                # entry only to mirror the Class-1 plugin list.
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'chaquopy'
            gradleKey = 'chaquopy'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        # ============================================================
        # Class-2: SDK and Java toolchain
        # ============================================================

        @{
            name      = 'compile-sdk'
            gradleKey = 'compile-sdk'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $true;  matcher = '`compileSdk`\s*/\s*`targetSdk`:\s*`?(?<v>\d+)`?\s+for both modules' }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = 'compileSdk\s*\|\s*(?<v>\d+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'target-sdk'
            gradleKey = 'target-sdk'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $true;  matcher = '`compileSdk`\s*/\s*`targetSdk`:\s*`?(?<v>\d+)`?\s+for both modules' }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = 'targetSdk\s*\|\s*(?<v>\d+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'min-sdk.standard'
            gradleKey = 'min-sdk.standard'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'firstOnly'
            exclude   = @()
        }

        @{
            name      = 'min-sdk.legacy'
            gradleKey = 'min-sdk.legacy'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'firstOnly'
            exclude   = @()
        }

        @{
            name      = 'jvm-target'
            gradleKey = 'jvm-target'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        # min-sdk.lite, min-sdk.photos, ndk-version: parsed for completeness;
        # documentation does not currently enumerate these per-flavor.
        @{
            name      = 'min-sdk.lite'
            gradleKey = 'min-sdk.lite'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'min-sdk.photos'
            gradleKey = 'min-sdk.photos'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'ndk-version'
            gradleKey = 'ndk-version'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'room-schema-version'
            gradleKey = 'room-schema-version'
            docs      = @{
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true; matcher = '\| Room DB version\s*\|\s*(?<v>\d+)\s*\|\s*Current schema version' }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        # ============================================================
        # Class-3: library coordinates (high-signal subset per D-2)
        # ============================================================

        @{
            name      = 'lib.androidx.core:core-ktx'
            gradleKey = 'lib.androidx.core:core-ktx'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`core-ktx`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.androidx.appcompat:appcompat'
            gradleKey = 'lib.androidx.appcompat:appcompat'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`appcompat`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.androidx.constraintlayout:constraintlayout'
            gradleKey = 'lib.androidx.constraintlayout:constraintlayout'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`constraintlayout`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.com.google.android.material:material'
            gradleKey = 'lib.com.google.android.material:material'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`material`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.com.google.dagger:hilt-android'
            gradleKey = 'lib.com.google.dagger:hilt-android'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                # Captures BOTH the inventory mention (table row) AND the prose mention
                # in §11 "Version History" - intentional, to surface the documented
                # internal inconsistency per strategic §4 evidence.
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '(?:\|\s*`hilt-android`\s*\|\s*|Hilt\s+)(?<v>\d+(?:\.\d+)+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.androidx.room:room-runtime'
            gradleKey = 'lib.androidx.room:room-runtime'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`room-runtime`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                # CLAUDE.md's Room pin is now emitted by generate-toolchain-pins.ps1 as the
                # library version ("Room: <ver>") inside the managed generated block, so the
                # old "Room v6" schema-major mention no longer exists and cannot drift (S1075).
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.androidx.media3:media3-exoplayer'
            gradleKey = 'lib.androidx.media3:media3-exoplayer'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`media3-exoplayer`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.com.github.bumptech.glide:glide'
            gradleKey = 'lib.com.github.bumptech.glide:glide'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`glide`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.com.github.mwiede:jsch'
            gradleKey = 'lib.com.github.mwiede:jsch'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`jsch`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.com.hierynomus:smbj'
            gradleKey = 'lib.com.hierynomus:smbj'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`smbj`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.commons-net:commons-net'
            gradleKey = 'lib.commons-net:commons-net'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`commons-net`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.com.squareup.okhttp3:okhttp'
            gradleKey = 'lib.com.squareup.okhttp3:okhttp'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`okhttp`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.com.squareup.retrofit2:retrofit'
            gradleKey = 'lib.com.squareup.retrofit2:retrofit'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true;  matcher = '\|\s*`retrofit`\s*\|\s*(?<v>[\d\.\-A-Za-z]+)' }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }

        @{
            name      = 'lib.org.nanohttpd:nanohttpd'
            gradleKey = 'lib.org.nanohttpd:nanohttpd'
            docs      = @{
                'docs/TECH_STACK.md'       = @{ required = $true;  matcher = 'nanohttpd:(?<v>[\d\.]+)' }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md'                = @{ required = $false; matcher = $null }
            }
            policy    = 'allMustMatch'
            exclude   = @()
        }
    )
}
