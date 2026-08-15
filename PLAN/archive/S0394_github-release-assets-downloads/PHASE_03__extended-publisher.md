# Phase 03 - Extended GitHub Release publisher

**Strategic spec:** [`../S0394_github-release-assets-downloads.md`](../S0394_github-release-assets-downloads.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Extend the existing publisher from {standard, vr} to the full spectrum (standard, vr, lite, photos, legacy, noLegal, wear): discover, fingerprint-verify, stage with versioned names, upload, and verify all assets are present on one release.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (uniform-version spectrum APKs exist).
- [ ] `gh` CLI authenticated with `repo` scope.
- [ ] On `main` (release worktree) - the publisher enforces branch == main.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/publish-github-release.ps1` | Modified | ≤ 520 |

> If the file exceeds 500 LOC after edits, take a timestamped backup in `temp/` first (Constraints).

---

## Steps

### Step 03.1 - Discover + stage the full spectrum with versioned names

**Files:** `scripts/release/publish-github-release.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Generalize the APK discovery. Replace the hardcoded standard+vr pair with a declarative list of flavors {standard, vr, lite, photos, legacy, noLegal} read from `app_v2/build/outputs/apk/<flavor>/release` plus wear read from `wear/build/outputs/apk/release`, each via the existing `output-metadata.json`-first / newest-`.apk`-fallback helper. Stage each as `FastMediaSorter-<flavor>-<version>.apk` (keep `wear` as the `<flavor>` token for the wear artifact) in the existing `temp/release/<version>` staging dir. Keep the naming versioned - IzzyOnDroid (S0215) globs `FastMediaSorter-standard-*.apk`. Keep the staleness guard (APK newer than build.gradle.kts minus 24h) applied to every discovered APK.

**Verification:**

- `Grep` - `lite`, `photos`, `legacy`, `noLegal`, `wear` all referenced in the discovery/staging section.
- `Grep` - `FastMediaSorter-` staging name pattern present and parameterized by flavor + version.
- `Grep` - `wear/build/outputs/apk/release` path present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification PASS. Replaced the hardcoded standard+vr dirs with an ordered `$spectrum` map (standard, vr, lite, photos, legacy, noLegal, wear); a single loop discovers + staleness-guards + stages each as `FastMediaSorter-<flavor>-<version>.apk` into `$stagedAssets`. Whole-script AST parse SYNTAX-OK.

---

### Step 03.2 - Fingerprint-verify every staged asset

**Files:** `scripts/release/publish-github-release.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Pass the full staged asset list (all seven) to the existing `Assert-ExpectedFingerprint` against the single pinned `scripts/release/expected-signing-fingerprint.txt`. All flavors and the wear module now share the one release key (Phase 01), so one expected value covers the set. Keep the hard-fail on mismatch.

**Verification:**

- `Grep` - `Assert-ExpectedFingerprint` is invoked with the full staged-asset array (not just two).
- `Grep` - single `expected-signing-fingerprint.txt` still the only pin source.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification PASS. `Assert-ExpectedFingerprint -ApkPaths $stagedAssets` (all seven); pin-file referenced exactly once (single shared fingerprint). Syntax OK.

---

### Step 03.3 - Upload all assets and verify presence

**Files:** `scripts/release/publish-github-release.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Upload every staged asset via `gh release upload $tag .. --clobber`. Expand the post-publish verification: build `$expectedAssetNames` from the full staged list and assert the read-back `gh api repos/.../releases/tags/$tag --jq '.assets[].name'` contains every one; hard-fail listing any missing. Keep `-DryRun` resolving + planning all seven without mutating.

**Verification:**

- `Grep` - `$expectedAssetNames` built from the full staged list (loop, not two literals).
- `Grep` - the missing-asset hard-fail check still present.
- Run with `-DryRun` on main: plan output lists all seven asset names, no mutation.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Static verification PASS. `$expectedAssetNames` built from `$stagedAssets` via ForEach; upload loops over `$stagedAssets`; missing-asset hard-fail (`if ($missing.Count -gt 0)`) intact; zero stale standard/vr-only var refs. The live `-DryRun` exit-0 listing all seven (needs the built spectrum + gh on main) is deferred to release-time operator validation - tracked in the final BlockNeedUserTest acceptance.

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [~] Script runs `-DryRun` exit 0, planning all seven assets - DEFERRED to release-time (needs the built spectrum + gh on main); statically verified (syntax OK, full-list discovery/upload/verify).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for `scripts/release/publish-github-release.ps1`.

---

## Handoff Notes to Next Phase

The release now carries assets named `FastMediaSorter-<flavor>-<version>.apk` for the seven builds. Phase 04 maps website buttons to these names; the public-page mapping must exclude `noLegal`, which is matched only on `nolegal*.html`.

---

## Rollback Plan

Revert the phase commit - the publisher returns to standard+vr only; no release is mutated by reverting source.
