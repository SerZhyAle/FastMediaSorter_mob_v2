# Phase 03 - Overlay Seams

**Strategic spec:** [`../S0379_standard-nolegal-storage-surface.md`](../S0379_standard-nolegal-storage-surface.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Introduce flavor-safe seams for follow-up noLegal storage extensions without leaking overlay behavior into market builds.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] noLegal-only behavior is still isolated to flavor source sets.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/...` | Modified | ≤ 250 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/...` | New / Modified | ≤ 250 |

> Resolve exact files during implementation; if the prompt cannot name them precisely, stop and run `/spec-update S0379`.

---

## Steps

### Step 03.1 - Add a main-source seam for restricted-tree policy

**Files:** exact file to be named during implementation
**Depends on:** 02.2

**Prompt for developer:**

- Add a small interface or contract in `src/main` that decides whether an advanced storage lane is available.
- Keep the default market implementation conservative.
- Do not add `BuildConfig` flavor checks in `src/main`.

**Verification:**

- A main-source seam exists for restricted-tree policy.
- No direct `BuildConfig` flavor check is introduced in `src/main`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification PASS. Files: `RestrictedTreeTargetPolicy.kt`, `RestrictedTreeTargetPolicyModule.kt`. No `BuildConfig` flavor checks added in `src/main`.

### Step 03.2 - Bind the aggressive implementation in noLegal only

**Files:** exact file to be named during implementation
**Depends on:** 03.1

**Prompt for developer:**

- Bind the aggressive overlay implementation only from `src/noLegal`.
- Keep non-noLegal builds on the conservative default implementation.
- Do not add UI or docs in this step.

**Verification:**

- A noLegal-only source-set implementation exists.
- Non-noLegal source sets still compile without the noLegal class.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification PASS. Files: `NoLegalRestrictedTreeTargetPolicy.kt`, `NoLegalRestrictedTreeTargetPolicyModule.kt`, picker/initializer injection points. Builds: `build-debug.PS1` PASS, `build-nolegal-debug.ps1` PASS.

---

## Phase Done Criteria

- [ ] A flavor-safe seam exists for future noLegal-only restricted-tree work.
- [ ] No noLegal-only behavior leaks into `src/main`.
