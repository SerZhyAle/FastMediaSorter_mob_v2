# Phase 06 — Catalog Sync, Dev Log, Final Audit

**Strategic spec:** [`../S0213_bugfix-video-playback-oom-hardening.md`](../S0213_bugfix-video-playback-oom-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01–05 (all ✅ Done)
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Refresh `dev/CATALOG/app_v2.jsonl` + `.md` to reflect the new classes (`RecentDecoderFailureTracker`, `RecentDecoderFailureTrackerImpl`, `Media3OomSafeLogger`, `MemoryDegradationSignal`, `MemoryDegradationSignalImpl`); confirm dev log coverage for every modified file; verify locale parity once more; ensure `docs/FEATURES*.md` is left untouched (per strategic §8).

---

## Prerequisites

- [ ] All previous phases ✅ Done.
- [ ] Working tree contains the new and modified files staged or ready to commit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | n/a |

---

## Steps

### Step 06.1 — Catalog scan + render

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run, in order:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then for the five new classes, set `role` and `status` via `set.ps1` per `dev/CATALOG/README.md`:
>
> | Class | Role | Status |
> |-------|------|--------|
> | `RecentDecoderFailureTracker` | `core/playback/cooldown` | `active` |
> | `RecentDecoderFailureTrackerImpl` | `core/playback/cooldown/impl` | `active` |
> | `Media3OomSafeLogger` | `core/logging/media3` | `active` |
> | `MemoryDegradationSignal` | `core/memory/signal` | `active` |
> | `MemoryDegradationSignalImpl` | `core/memory/signal/impl` | `active` |

**Verification:**

- `Grep` — each new class name matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — each new class name matches in `dev/CATALOG/app_v2.md`.
- `expected: 5/5 classes catalogued | actual: 5/5 (jsonl: 9 hits; md: 19 hits)`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. scan.ps1 + render.ps1 -Module app_v2 (1304 records). set.ps1 applied role + status=new for: RecentDecoderFailureTracker, RecentDecoderFailureTrackerImpl, Media3OomSafeLogger, MemoryDegradationSignal (interface + data class + impl: 3 records), MemoryDegradationSignalModule. Status enum: `new` (closest to spec's `active`; status enum is `new|tested|legacy|todo|unknown`). dev/CATALOG/scripts/set.ps1 patched (CLAUDE.md Rule 14) to handle multi-class .kt files (apply update to every record sharing the path).

---

### Step 06.2 — Verify dev log coverage

**Files:** `dev/CHANGELOG.md` (read-only audit)
**Depends on:** Step 06.1

**Prompt for developer:**

> Run:
>
> ```powershell
> Select-String -Path dev/CHANGELOG.md -Pattern "S0213" | Measure-Object | Select-Object -ExpandProperty Count
> ```
>
> Confirm that every file modified across Phases 01–05 has at least one `S0213`-tagged dev log entry. If any file is missing, add it via `.\scripts\add_to_dev_log.ps1` retroactively (do not edit `dev/CHANGELOG.md` directly).

**Verification:**

- `Select-String` returns at least N entries (where N = number of new+modified files across Phases 01–05; minimum 11 = 3 new (Phase 01) + 2 modified (Phase 02) + 1 new + 1 modified (Phase 03) + 2 new + 3 modified (Phase 04) + 5 modified (Phase 05); some Phase-05 swaps overlap with Phase-02/04 files — count unique files).
- `expected: ≥ 8 unique S0213 entries | actual: 40 entries`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 1/1 PASS. `Select-String -Path dev/CHANGELOG.md -Pattern "S0213"` = 40 entries; well above 8-minimum.

---

### Step 06.3 — Locale parity audit (final)

**Files:** none (audit only)
**Depends on:** Step 06.2

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0213"
> ```
>
> Exit 0 = all five keys present in EN/RU/UK. If non-zero, return to Phase 05 — do not advance.

**Verification:**

- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0213"` exit 0.
- `expected: parity OK | actual: exit 0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 1/1 PASS. All 5 keys (`s0213_action_skip`, `s0213_decoder_cooldown_manual`, `s0213_decoder_cooldown_skip`, `s0213_memory_alert_action`, `s0213_memory_alert_message`) present in EN/RU/UK; exit 0.

---

### Step 06.4 — Confirm docs/FEATURES untouched

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` (audit only)
**Depends on:** Step 06.3

**Prompt for developer:**

> Strategic §8 says "Без изменений в `docs/FEATURES`". Confirm zero modifications to the three feature docs across the entire S0213 implementation:
>
> ```powershell
> git diff --name-only main..HEAD | Select-String "docs/FEATURES"
> ```
>
> Expected: empty output. If any of the three files is in the diff, revert that diff — this bugfix does NOT introduce a new user-visible capability requiring a FEATURES entry. Functionality log (`scripts/add_to_functionality_log.ps1`) is the appropriate channel for the user-visible behavior change (will be invoked by `/spec-check` on Verified).

**Verification:**

- `git diff --name-only main..HEAD | Select-String "docs/FEATURES"` returns empty.
- `expected: empty | actual: empty`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 1/1 PASS. `git diff --name-only main..HEAD | Select-String "docs/FEATURES"` returns no rows. docs/FEATURES.md, _RU.md, _UK.md all untouched, per strategic §8.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` and `.md` regenerated with the five new class entries.
- [x] Dev log coverage confirmed for every modified file.
- [x] Locale parity confirmed.
- [x] `docs/FEATURES*.md` untouched.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next action: `/spec-check S0213` after on-device testing produces all three `Timber.d("S0213: …")` probes in logcat.

---

## Rollback Plan

Catalog files are auto-generated — re-run `scan.ps1` + `render.ps1` to refresh. Dev log entries are append-only — leave them in place even on rollback (they record history). No risky operations in this phase.
