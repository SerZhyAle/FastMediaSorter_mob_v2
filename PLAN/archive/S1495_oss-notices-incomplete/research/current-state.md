# S1495 research - current state of OSS notices

**Date:** 2026-08-10
**Method:** read-only sweep of build files, `scripts/`, `docs/`, document registry.

## Measured payload

- `app_v2/build.gradle.kts` - 104 unique `group:artifact` coordinates across all configurations. `dependencies {}` block spans lines 1323-1650.
- `wear/build.gradle.kts` - 33 unique coordinates. `dependencies {}` block spans lines 126-211. No shared version catalog with `app_v2`.
- `docs/OPEN_SOURCE.md` - 34 lines, hand-authored, lists 2 libraries (SMBJ, epub4j).

## Declaration shape

- Dependencies are declared as literal `"group:artifact:version"` strings. No `gradle/libs.versions.toml` version catalog is in use.
- Flavor scoping is expressed only through configuration names, quoted: `"standardImplementation"(..)`, `"noLegalImplementation"(..)` and siblings. No `BuildConfig.IS_*` guard participates.
- `com.github.TeamNewPipe:NewPipeExtractor` is declared at `app_v2/build.gradle.kts:1593-1595` under the `noLegal` configuration, with an in-file comment recording that the GPL extractor is linked only into the sideload-only flavor.
- BouncyCastle arrives transitively through SMBJ and is version-asserted at `app_v2/build.gradle.kts:1652-1671` but never declared as a coordinate. No text parser can see it.

## Existing tooling

- No licence plugin in `app_v2/build.gradle.kts`, `wear/build.gradle.kts` or `settings.gradle.kts`.
- No licence, notice or attribution generator anywhere under `scripts/`. The "OSS license aggregator in release-prep tooling" named at `THIRD_PARTY_LICENSES.md:5` does not exist.
- `scripts/doc-drift/GradleParser.ps1:46` already extracts coordinates via one regex, but only for `implementation|api|kapt|ksp|coreLibraryDesugaring`. It matches no quoted flavor configuration and no `debug*`/`test*` configuration, so roughly half of `app_v2`'s declarations are invisible to it. Built for version-pin drift, not inventory - extend or supersede, do not assume coverage.
- `scripts/mcp/gradle-mcp-server.mjs:8-18` whitelists `:app_v2:dependencies`, so resolved-graph output is reachable, but nothing in the repo consumes it.

## Precedent to copy

`scripts/docs/generate-flavor-matrix.ps1` (312 lines) is the closest working shape:

- parses the build file as text with brace-depth tracking, never starts a gradle daemon;
- emits a machine-readable snapshot (`docs/flavors/flavor-matrix.json`) plus a rendered Markdown page (`docs/FLAVOR_MATRIX.md`);
- refuses to emit a partial result - exit 2 when parsing is ambiguous, e.g. fewer flavors than `-MinFlavorCount`;
- exposes `-Check` so a gate can run it without writing.

`scripts/quality/assert-flavor-matrix-docs.ps1` (305 lines) is the matching gate: cell-by-cell comparison against a manifest (`scripts/quality/flavor-matrix-docs.psd1`), typed failure classes, `-Gate` switch separating CI-fatal from read-only report mode.

Test convention for parsers of this class: a fixture directory plus a `Run-Tests.ps1` suite, as in `scripts/doc-drift.tests/fixtures/{app_v2,wear}/build.gradle.kts`.

## Document registry

- The `legal-downloads` record (`docs/DOCUMENT_REGISTRY.jsonl:17`) covers OSS notices, privacy policy, terms; `published: true`, `indexable: true`.
- Its `update_triggers` are `release,documentation,permission` - `dependency` is absent, though the `architecture` and `developer-operations` records both carry it. Nothing fires the legal page on a dependency change.
- Privacy policy and terms exist as EN/RU/UK triplets; `docs/OPEN_SOURCE.md` is the only EN-only member of the row.

## Consequences for the plan

- A text-only parser reaches every declared shipping coordinate but never a transitive one. Transitive entries must be carried by the licence manifest explicitly.
- The coordinate-to-licence mapping cannot be read from the build file at all - POM resolution would be required. The manifest is not a shortcut, it is the only cheap source.
- The gate must fail on an unknown coordinate, otherwise a new dependency silently reproduces the original defect.
