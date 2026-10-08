<sub class="doc-stamp">26.10.07 12:50</sub>

# Pointer - `PACKAGE-VERSIONING`

| | |
| --- | --- |
| **Id** | `PACKAGE-VERSIONING` |
| **Version** | 0.5, draft. Owner: this product |
| **Home** | `package-versioning/README.md` in the shared contracts catalog |
| **Role here** | owner and reference implementation (the version stamps of the phone app, the watch app and the watch face) |

## What this repository must do to stay conformant

- Derive versionName and versionCode from one `yyMMddHHmm` instant per packaging invocation, never from two.
- versionName `Y.YM.MDDH.Hmm` (rule 2); a 9-digit versionCode `yyMMddHH * 10 + p` whose last digit follows the family's registered band (rule 3): phone `floor(minute / 10)` on 0..5, watch app `6 + floor(minute / 15)` on 6..9, watch face on its own application id.
- Pin an override as the pair `-Pfms.versionName` + `-Pfms.versionCode`, together or not at all (rule 5); the release orchestrator passes one shared pair to every module of the release.
- Keep the checked-in constants as non-releasable sentinels: nothing writes them, and they never reach a store, a sideload page or an update feed (rule 7).
- Release every family from one pin; an edition on a clock of its own, if one is ever declared, writes the E1
  declaration in its registry row first (0.5, section 8).
- Never repeat and never lower a versionCode within one application id; an out-of-scheme installed build is replaced by uninstall or a strictly greater code, recorded as a dated registry exception (rule 4).

## Where it lives here

- `gradle/build-version-stamp.gradle.kts` - the in-build net: the `ValueSource` clock, the memoised invocation stamp, the packaging predicate, the one-sided-override refusal.
- `scripts/utils/build-version-stamp.ps1` - `Get-BuildVersionStamp`, the PowerShell half kept byte-compatible with the gradle derivation.
- `scripts/release/build-release-spectrum.ps1` - the release orchestrator that derives one instant and passes the pair to every module it builds.
- `scripts/quality/assert-module-version-parity.ps1` - the parity gate over the checked-in sentinels (conformance rung 1).
- `app_v2/build.gradle.kts`, `wear/build.gradle.kts` - the three version sources per module (override, stamp, sentinel).
- `watchface/build.gradle.kts` - declared, adoption pending: its frozen `versionCode = 1` sentinel is outside the stamp until the family is wired.
