# Phase 01 - Contract relaxation + config serializer

**Strategic spec:** [`../S0984_share-sftp-resource-config.md`](../S0984_share-sftp-resource-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-11
**Completed:** 2026-07-11

---

## Objective

Relax the companion config parser and import use case to accept empty `password` and empty `hostKeyFingerprintSha256`, and add a serializer that writes a `CompanionConfigDto` back to `.fmscfg` JSON. Data layer only; no UI.

---

## Prerequisites

- [ ] Strategic §6 items Resolved (they are).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/companion/CompanionConfigParser.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/companion/CompanionConfigSerializer.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/companion/ImportCompanionConfigUseCase.kt` | Modified | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/companion/CompanionConfigParserTest.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/companion/CompanionConfigSerializerTest.kt` | New | ≤ 90 |

> No file exceeds 500 LOC after edit - no backup step required.

---

## Steps

### Step 01.1 - Relax parser validation for password + fingerprint

**Files:** `data/companion/CompanionConfigParser.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `validate()`, remove the two hard-fail lines that reject an empty credential: `if (dto.password.isNullOrEmpty()) invalid("password missing")` and `if (dto.hostKeyFingerprintSha256.isNullOrBlank()) invalid("hostKeyFingerprintSha256 missing")`. Keep every other check unchanged (schemaVersion range, `protocol == "sftp"`, non-empty `accessPaths` with valid host/port, non-blank `username`, non-empty `roots` with `/`-rooted `virtualPath`). Add a one-line WHY comment: empty password/fingerprint are valid Android-side (passwordless share + no-pin TOFU); the frozen cross-repo contract still always sends both, so no schemaVersion bump.

**Verification:**

- `Grep` - `password missing` returns zero hits in the file.
- `Grep` - `hostKeyFingerprintSha256 missing` returns zero hits in the file.
- `Grep` - `if (dto.username.isNullOrBlank()) invalid` still present (unrelated checks intact).

**Status:** `[x] done`

---

### Step 01.2 - Add `CompanionConfigSerializer`

**Files:** `data/companion/CompanionConfigSerializer.kt` (New)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class CompanionConfigSerializer @Inject constructor()` in package `com.sza.fastmediasorter.data.companion`. Expose `fun serialize(dto: CompanionConfigDto): String` returning `Gson().toJson(dto)` (plain-JSON transport - starts with `{`, so `CompanionConfigParser.parse` round-trips it). Use the same `Gson` construction style as the parser. No compression - the `FMSCFG1:` variant is import-only. KDoc: mirror of `CompanionConfigParser`, write side (S0984 export).

**Verification:**

- `Glob` - `data/companion/CompanionConfigSerializer.kt` exists.
- `Grep` - `class CompanionConfigSerializer` matches once (declaration).
- `Grep` - `fun serialize(dto: CompanionConfigDto): String` present.

**Status:** `[x] done`

---

### Step 01.3 - Import use case: blank fingerprint -> no pinning (do not fail)

**Files:** `domain/usecase/companion/ImportCompanionConfigUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `import(config)`, change the fingerprint handling so a blank/empty `hostKeyFingerprintSha256` yields `hostKeyFingerprint = null` on the built `MediaResource` (no TOFU pin - same as manual entry) instead of returning `Result.failure`. Only when the fingerprint is non-blank AND `SshFingerprintNormalizer.canonical(...)` returns null should it still fail with `INVALID_CONTENT`. Apply the resolved fingerprint (`canonical` result or `null`) to the `hostKeyFingerprint =` argument in the `MediaResource` builder. Add a WHY comment: blank fingerprint is an intentional no-pin share (S0984), non-canonical non-blank is still malformed.

**Verification:**

- `Grep` - `hostKeyFingerprint = ` present in the `MediaResource(` builder (assigns the resolved nullable value).
- `Grep` - the unconditional `Host-key fingerprint not in canonical` failure is now guarded by a non-blank check (`isNotBlank()` or equivalent appears near the fingerprint resolution).
- Build predicate covered by Phase Done Criteria.

**Status:** `[x] done`

---

### Step 01.4 - Unit tests for relaxation + serializer round-trip

**Files:** `data/companion/CompanionConfigParserTest.kt` (Modified), `data/companion/CompanionConfigSerializerTest.kt` (New)
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> In `CompanionConfigParserTest`, add two tests using NEW inline JSON (do NOT mutate the frozen `canonical_vector` fixture): `parse accepts empty password` and `parse accepts empty hostKeyFingerprintSha256` - each asserts the returned dto carries the empty value and no exception is thrown. Create `CompanionConfigSerializerTest` with a round-trip test: build a `CompanionConfigDto` (schemaVersion 1, one lan accessPath, one root, non-empty and empty-credential variants), `serialize` it, feed the result to `CompanionConfigParser.parse`, assert the parsed dto equals the original for all fields.

**Verification:**

- `Grep` - `empty password` and `empty hostKeyFingerprintSha256` test names present in `CompanionConfigParserTest.kt`.
- `Glob` - `CompanionConfigSerializerTest.kt` exists; `Grep` - `serialize` + `parse` both referenced (round-trip).
- `.\a.ps1 fu` (or `gradlew testStandardDebugUnitTest --tests "*CompanionConfig*"`) - the new tests pass.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `*CompanionConfig*` unit tests pass.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `CompanionConfigSerializer`).

---

## Handoff Notes to Next Phase

`CompanionConfigSerializer.serialize` produces parser-round-trippable `.fmscfg` JSON - Phase 02's export use case consumes it. The parser + import use case now tolerate passwordless / no-fingerprint configs end to end.

---

## Rollback Plan

Revert the phase commit(s). No data migration, no user-facing surface, no schema bump - pure additive relaxation.
