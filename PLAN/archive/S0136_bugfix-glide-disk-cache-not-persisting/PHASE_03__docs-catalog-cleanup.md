# Phase 03 — Docs / Catalog / Cleanup

**Strategic spec:** [`../S0136_bugfix-glide-disk-cache-not-persisting.md`](../S0136_bugfix-glide-disk-cache-not-persisting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Remove all `S0136:` Timber instrumentation tags introduced in Phase 01 (except the canonical `GlideCacheStats summary` line, which stays as a permanent regression-detection signal), refresh the catalogue, and finalise the dev log so `/spec-check` can mark the spec `Verified`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Field-test confirms `disk ≥ 30 %` of total `GlideCacheStats summary` in second-session log.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| All `app_v2/src/main/java/**/*.kt` containing `S0136:` tags | Modified (deletions) | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | n/a |
| `dev/CHANGELOG.md` | Modified (appended) | n/a |

> No user-facing strings; `docs/FEATURES*` not touched (strategic §8: feature does not appear in user-facing inventory).

---

## Steps

### Step 03.1 — Remove all `S0136:` Timber.d tags except the persistent `GlideCacheStats summary`

**Files:** every `.kt` file under `app_v2/src/main/java/` containing `Timber.d("S0136:`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `Grep` with pattern `Timber\.d\("S0136:` across `app_v2/src/main/java/**/*.kt`. Delete every matching line and any companion supporting code introduced solely for instrumentation (e.g. the `s0136PostFirstLoadDone` AtomicBoolean and its `compareAndSet` block in `AdapterThumbnailLoader`, and the new `logGlideDiskCacheStatusOnce` helper in `CacheStatusHelper`).
>
> **Exception — keep this one line as permanent diagnostic:**
>
> ```kotlin
> Timber.d("S0136: GlideCacheStats summary total=$total disk=$disk memory=$memory repo=$repo network=$network local=$local")
> ```
>
> Rename the prefix from `S0136:` to `GlideCacheStats:` for permanence — `S0136` is a ticket id, not a runtime concept, and we want this line to outlive the ticket. After rename:
>
> ```kotlin
> Timber.d("GlideCacheStats: summary total=$total disk=$disk memory=$memory repo=$repo network=$network local=$local")
> ```

**Verification:**

- `Grep` — `Timber\.d\("S0136:` returns zero hits across `app_v2/src/main/java/`.
- `Grep` — `s0136PostFirstLoadDone` returns zero hits.
- `Grep` — `logGlideDiskCacheStatusOnce` returns zero hits.
- `Grep` — `GlideCacheStats: summary total=` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt`.

**Status:** `[x]` done — all S0136: Timber.d tags removed (0 found); s0136PostFirstLoadDone removed; logGlideDiskCacheStatusOnce removed; GlideCacheStats: summary line added.

---

### Step 03.2 — Regenerate `dev/CATALOG/app_v2.{jsonl,md}`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run, in order:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Commit the resulting `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` together with the cleanup commit.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists.
- `Glob` — `dev/CATALOG/app_v2.md` exists.
- `git diff --quiet dev/CATALOG/app_v2.jsonl` exits non-zero (file changed).

**Status:** `[x]` done — scan.ps1 + render.ps1 executed; 1009 files processed.

---

### Step 03.3 — Append dev-changelog entries for every modified file

**Files:** `dev/CHANGELOG.md` (via `scripts/add_to_dev_log.ps1`, never edited directly)
**Depends on:** Step 03.2

**Prompt for developer:**

> For every `.kt` file modified in Phase 01 cleanup and Phase 02 fix, run:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "<path>" "<short target>" "<description>"
> ```
>
> Suggested entries:
>
> - `app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt` — `GlideCacheStats.logStats` — `S0136: keep summary line under stable GlideCacheStats: prefix; instrumentation removed`.
> - For each Phase 01 instrumentation file: `S0136: Phase 01 instrumentation removed after Verified`.
> - For Phase 02 fix file(s): `S0136: <one-line fix description>`.
>
> Do not edit `dev/CHANGELOG.md` directly. Do not edit `PLAN/spec-catalog.jsonl` directly — `/spec-check` flips the status separately.

**Verification:**

- `Grep` — `S0136:` matches at least once per modified file path inside `dev/CHANGELOG.md` (each entry is one row in the changelog table).
- `Grep` — `Timber\.d\("S0136:` still returns zero hits across `app_v2/src/main/java/` (re-check after dev-log step in case stray tag survived).

**Status:** `[x]` done — dev-log entries added for all 3 modified files.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [x] `Grep` for `Timber\.d\("S0136:` returns zero hits.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated and committed.
- [x] `dev/CHANGELOG.md` has entries for every modified file.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md](INDEX.md) Completion Gate.

---

## Rollback Plan

Revert the cleanup commit. The Phase 02 fix and the permanent `GlideCacheStats: summary` line can be retained independently.
