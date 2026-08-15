# Phase 05 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0357_smb-video-playback-robustness.md`](../S0357_smb-video-playback-robustness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all (Phase 01, 02, 03, 04)
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog, confirm dev-log coverage, verify logging hygiene, and confirm the BlockNeedUserTest verification-tag count is consistent before the ticket enters device test.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated (gitignored) | n/a |

> No source files change in this phase. `dev/CHANGELOG.md` is updated via the dev-log script, not edited by hand. `docs/FEATURES*.md` is intentionally NOT touched - strategic §8 states "Без изменений" (reliability hardening, not a new user capability).

---

## Steps

### Step 05.1 - Regenerate the class catalog for app_v2

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the catalog sync wrapper for the affected module so the two new classes (`NetworkPlaybackDataSource`, `BufferingStallRecoveryPolicy`) are indexed:
>
> `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`
>
> Then set role + status for the two new entries via `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1` (role per layer: `NetworkPlaybackDataSource` is a `data` contract, `BufferingStallRecoveryPolicy` is a `core` playback policy). Both are shared `src/main` classes, so no `-NoFlavors` hint is needed.

**Verification:**

- Catalog query returns the new contract: `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -PathMatches "*NetworkPlaybackDataSource*"` (expected: one record | actual: recorded).
- Catalog query returns the new policy: `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -PathMatches "*BufferingStallRecoveryPolicy*"` (expected: one record | actual: recorded).

**Status:** `[ ]` not done

---

### Step 05.2 - Confirm logging hygiene and verification-tag consistency

**Files:** all `.kt` files touched in Phases 01-03
**Depends on:** Step 05.1

**Prompt for developer:**

> Confirm no `Log.d(` was introduced and the BlockNeedUserTest probe tags are exactly the ones declared by the phases (one per changed flow entry). The ticket is about to be in `BlockNeedUserTest`, so `S0357:` tags MUST be present now; they are removed only when the ticket leaves that status (by `/spec-check` on Verified, or `/spec-update` on re-open).

**Verification:**

- `Grep -n "Log\.d\("` across `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/` and `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/` returns zero hits (expected: 0 | actual: recorded).
- `Grep` count of `Timber.d("S0357:` across all `.kt` files equals the number of changed-flow entry tags declared (Step 01.4 + Step 02.4 + Step 03.4 = 3; expected: 3 | actual: recorded).
- No persistent `Timber.i/w/e` line contains `S0357` (expected: 0 | actual: recorded).

**Status:** `[ ]` not done

---

### Step 05.3 - Confirm dev-log coverage for every modified file

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for every source/resource file modified across Phases 01-04 (the new DataSource contract, the three DataSources, the recovery policy, `VideoPlayerErrorHandler`, `VideoPlayerManager`, and the three `strings.xml`). Add any missing entry via `.\scripts\add_to_dev_log.ps1` - never edit `dev/CHANGELOG.md` by hand.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains entries referencing `NetworkPlaybackDataSource`, `BufferingStallRecoveryPolicy`, `VideoPlayerErrorHandler`, and `video_decoder_unsupported_hardware` (expected: all present | actual: recorded).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and the two new classes are queryable.
- [ ] `docs/FEATURES*.md` deliberately NOT modified (strategic §8 = "Без изменений") - confirmed.
- [ ] Ready for `/spec-check S0357`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action after all phases Done: ticket moves to `BlockNeedUserTest` for on-device verification on Quest with a real SMB resource (strategic §3.3 validation level), then `/spec-check S0357`.

---

## Rollback Plan

No source change in this phase. The catalog is a regenerated gitignored index; re-run `scripts/catalog_sync.ps1` to restore it. Dev-log entries are append-only history and are not rolled back.
