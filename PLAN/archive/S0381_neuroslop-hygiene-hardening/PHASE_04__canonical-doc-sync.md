# Phase 04 - Canonical Doc Sync

**Strategic spec:** [`../S0381_neuroslop-hygiene-hardening.md`](../S0381_neuroslop-hygiene-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Make version facts in canonical documents generated from the build configuration so drift between docs and the real toolchain becomes structurally impossible.

> Owner decision (strategic §6.5): generate version facts from build config, not hand-edit + check. The generator is the source of truth; documents carry a generated, delimited block.
> Confirmed audit drift (2026-06-07): `CLAUDE.md` pins Glide `4.15.1` while `app_v2/build.gradle.kts` builds Glide `4.16.0`; `docs/TECH_STACK.md` lists media libraries without versions while the build uses Media3 `1.2.1`. Do NOT introduce any version number that is not read directly from the build configuration - no hand-typed Kotlin/Room/AGP versions.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/generate-toolchain-pins.ps1` | New | ≤ 260 |
| `CLAUDE.md` | Modified (generated block only) | ≤ 40 |
| `docs/TECH_STACK.md` | Modified (generated block only) | ≤ 60 |
| `scripts/post-change.ps1` | Modified | ≤ 40 |

> The generator owns a delimited managed block (e.g. `<!-- toolchain-pins:start -->` .. `<!-- toolchain-pins:end -->`) in each target document; only that block is rewritten. Prose outside the block is never touched by the generator.

---

## Steps

### Step 04.1 - Add the toolchain-pin generator

**Files:** `scripts/quality/generate-toolchain-pins.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a script that reads dependency and SDK version facts directly from `app_v2/build.gradle.kts` (and the Gradle version catalog if present) and emits a normalized list of `name: version` pins. Cover the audited keys first: image-loading library, Media3, compileSdk/minSdk/targetSdk. Read every version from the build files - never hard-code a version literal in the script.

**Verification:**

- `Glob` - `scripts/quality/generate-toolchain-pins.ps1` exists.
- `Grep` - `build.gradle.kts` appears in `scripts/quality/generate-toolchain-pins.ps1`.
- `Grep` - `4\.\d+\.\d+` does NOT appear as a hard-coded literal in `scripts/quality/generate-toolchain-pins.ps1` (versions are read, not embedded).
- Run - `pwsh -NoProfile -File scripts/quality/generate-toolchain-pins.ps1` exits 0 and prints a pin list that includes the image-loading library at the version found in the build file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 4/4 PASS. Created `scripts/quality/generate-toolchain-pins.ps1` (Print/`-Write`/`-Check` modes). Reads compileSdk/targetSdk/minSdk, Kotlin, Room, Media3, Glide from `app_v2/build.gradle.kts` + root `build.gradle.kts`; no version literal embedded (grep clean). Print output: Glide 4.16.0 (build truth) vs CLAUDE.md's stale 4.15.1. Note: there is NO `gradle/libs.versions.toml` despite TECH_STACK.md referencing it - versions are inline; generator reads the real source.

---

### Step 04.2 - Generate the managed pin blocks into canonical docs

**Files:** `CLAUDE.md`, `docs/TECH_STACK.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a delimited managed block to `CLAUDE.md` (Tech Stack Pins area) and `docs/TECH_STACK.md`, then have the generator write the current pins into it. Remove the stale hand-typed Glide `4.15.1` pin so the only version source is the generated block. Run the generator and verify the documents now reflect the real build versions.

**Verification:**

- `Grep` - `toolchain-pins:start` appears in both `CLAUDE.md` and `docs/TECH_STACK.md`.
- `Grep` - `4.15.1` returns zero hits in `CLAUDE.md` (stale pin removed).
- `Grep` - the image-loading library version inside the managed block equals the version in `app_v2/build.gradle.kts` (`expected: <build value> | actual: <doc value>`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. Added managed marker pair to `CLAUDE.md` (Tech Stack Pins; stripped hand-typed versions incl. stale Glide 4.15.1) and `docs/TECH_STACK.md` (also fixed stale `gradle/libs.versions.toml` reference - no catalog exists). Ran `-Write`: both blocks filled. `4.15.1` zero hits in CLAUDE.md; managed Glide pin = 4.16.0 = build value. Dev logs recorded.

---

### Step 04.3 - Wire a drift gate into routine validation

**Files:** `scripts/post-change.ps1`, `scripts/quality/generate-toolchain-pins.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add a `-Check` mode to the generator that regenerates the pins in memory and fails (non-zero) if any managed block in the target documents differs from the freshly generated content. Call this check from the repository's routine hygiene path so future drift is caught automatically instead of by a one-off audit.

**Verification:**

- `Grep` - `generate-toolchain-pins.ps1` appears in `scripts/post-change.ps1`.
- `Grep` - `Check` (the check-mode parameter) appears in `scripts/quality/generate-toolchain-pins.ps1`.
- Run - `pwsh -NoProfile -File scripts/quality/generate-toolchain-pins.ps1 -Check` exits 0 immediately after Step 04.2 (no drift), and exits non-zero if a pin is manually corrupted in a doc.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. `generate-toolchain-pins.ps1` already has `-Check` (regenerates + compares managed blocks, exit 1 on drift/missing). Added `doc-pins-sync` step to `scripts/post-change.ps1` for ChangeType Config/Doc/Mixed. `-Check` exits 0 (OK CLAUDE.md, OK TECH_STACK.md). Integration test: `post-change.ps1 ... -ChangeType Doc` ran `doc-pins-sync` → PASS. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - N/A: phase touches only `.md` + `.ps1`; no compiled code changed (`build.gradle.kts` was read, not edited). Doc/Script closure = dry-run/`-Check`, all green.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (generator, CLAUDE.md, TECH_STACK.md, post-change.ps1).

---

## Handoff Notes to Next Phase

Version facts in canonical documents are now generated and drift-gated; manual version edits in the managed blocks will fail the check.

---

## Rollback Plan

Revert phase commit(s). The managed blocks and generator are additive; removing them restores the prior hand-maintained version prose.
