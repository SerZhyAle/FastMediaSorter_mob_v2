# Phase 01 - Payload stamp

**Strategic spec:** [`../S1200_channel-preview-atlas-refresh.md`](../S1200_channel-preview-atlas-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Give a descriptor a stable identity string and give the marker store somewhere to remember which identity was installed - no behaviour change yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSourceDescriptor.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/InstalledSetMarkerStore.kt` | Modified | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/delivery/DeliverableSourceDescriptorStampTest.kt` | New | ≤ 80 |

> All `src/main` / `src/test`; the mechanism is flavor-agnostic, so no flavor source set and no `BuildConfig` guard. No layout touched.

---

## Steps

### Step 01.1 - Give the descriptor a stamp

**Files:** `domain/delivery/DeliverableSourceDescriptor.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val stamp: String` as a computed property on `DeliverableSourceDescriptor`: join each file as `fileName + "=" + sha256` (falling back to `fileName + "=size:" + minSize` when `sha256` is blank, the unverified pure-resource case the KDoc already describes), sorted by `fileName` so map/list ordering cannot change the result, joined with `;`. KDoc it as the payload's identity for staleness comparison (S1200 ADR-1/ADR-2), stating that this is deliberately derived from the compiled pins rather than from anything the mirror could serve.

**Verification:**

- `Grep` - `val stamp` present in `DeliverableSourceDescriptor.kt`.
- `Grep` - the implementation calls `sortedBy` (order independence is the point, not incidental).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 01.2 - Persist the stamp beside the install flag

**Files:** `data/delivery/InstalledSetMarkerStore.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `stringPreferencesKey("delivery_stamp_${set.name}")` and three members mirroring the existing flag API: `suspend fun setStamp(set, stamp)`, `suspend fun installedStamp(set): String?` (null when absent), and clearing inside the existing payload-delete path. Do not add a Flow variant unless a caller needs one. KDoc: the stamp shares the flag's DataStore precisely because it must share its lifetime - surviving app update and cache clear, dying with the payload.

**Verification:**

- `Grep` - `delivery_stamp_` present exactly once.
- `Grep` - `setStamp` and `installedStamp` both declared.
- `Grep` - `deletePayload` (or its caller) also clears the stamp key.
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 01.3 - Unit-test the stamp

**Files:** `src/test/.../DeliverableSourceDescriptorStampTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Cover: two descriptors with the same files in different list order produce the same stamp; changing one file's `sha256` changes the stamp; adding a file changes the stamp; a blank-sha file contributes its `minSize` instead of an empty hash (so two blank-sha files of different sizes do not collide).

**Verification:**

- `Glob` - the test file exists.
- `.\a.ps1 fu` - the new class's cases pass; record `expected: 0 failures | actual: <n>` for this class only (the suite carries unrelated pre-existing failures).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit - focus: the stamp is pure and order-independent; no disk or hashing work on the main thread.

---

## Handoff Notes to Next Phase

- Nothing writes a stamp yet; Phase 02 wires it into install and uninstall.

---

## Rollback Plan

Revert the phase commit(s) - additive only, no behaviour depends on the new members yet.
