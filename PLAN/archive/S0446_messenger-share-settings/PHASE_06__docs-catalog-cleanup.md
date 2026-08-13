# Phase 06 - Docs & catalog cleanup

**Strategic spec:** [`../S0446_messenger-share-settings.md`](../S0446_messenger-share-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Sync user-facing docs, the class catalog, and the dev changelog to the implemented state, and stage on-device verification. The feature is now user-visible (three new toggles + WhatsApp/Instagram send), so a FEATURES entry is required (unlike the S0452 foundation, which had none).

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done; `.\a.ps1 fc` green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | + 1 entry |
| `docs/FEATURES_RU.md` | Modified | + 1 entry |
| `docs/FEATURES_UK.md` | Modified | + 1 entry |
| `dev/CATALOG/app_v2.jsonl` (+`.md`) | Regenerated | - |

---

## Steps

### Step 06.1 - FEATURES entry (EN/RU/UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** start of phase

**Prompt for developer:**

> Add a feature entry describing: per-profile toggles in Player settings to allow/hide "Send to Telegram / WhatsApp / Instagram" across the app (default off; disabled with "Not installed" when the client is absent), plus the ability to send a file to WhatsApp/Instagram. Keep EN/RU/UK in lockstep, RU with ё. These are standard/full-flavor features - no `FEATURES_noLegal` entry needed.

**Verification:**

- `Grep` - WhatsApp/Instagram send feature line present in all three FEATURES files.

**Status:** `[ ]` not done

---

### Step 06.2 - Catalog + dev-log sync

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to index the new classes (`WhatsAppShareTargets`, `InstagramShareTargets`) and changed ones. Set `role`/`status` on the two new catalogue classes via `set.ps1`. Confirm `dev/CHANGELOG.md` has an entry for every file touched across Phases 01-05 (added via `add_to_dev_log.ps1` during each phase). Add a functionality-log entry for the new user-visible send capability via `scripts/add_to_functionality_log.ps1` (ADD).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ShareTargets*"` lists the two new objects.
- `Grep` - `dev/CHANGELOG.md` references `WhatsAppShareTargets` (proxy for the batch).

**Status:** `[ ]` not done

---

### Step 06.3 - Stage on-device verification (BlockNeedUserTest)

**Files:** - (insert one `Timber.d("S0446:` tag per changed flow only when entering `BlockNeedUserTest`)
**Depends on:** Step 06.2

**Prompt for developer:**

> Per CLAUDE.md §2, a `Timber.d("S0446: ...")` tag exists in `.kt` iff the spec is in `BlockNeedUserTest`. When the parent advances S0446 into `BlockNeedUserTest`, insert one tag at the entry of each changed flow (settings toggle write, player messenger gate, browse messenger gate, each send path). Do NOT insert these tags during Phases 01-05. The on-device checklist: toggles default off; toggling on reveals the command in player + browse only when the client is installed; disabled "Not installed" shown otherwise; WhatsApp/Instagram send delivers the file or falls back to the chooser. Record results, then on leaving `BlockNeedUserTest` (to Verified) delete every `Timber.d("S0446:` line and commit the removal with the status change.

**Verification:**

- (Into `BlockNeedUserTest`) `Grep -n 'Timber\.d\("S0446:'` - one tag per changed flow.
- (Out to Verified) `Grep -n 'Timber\.d\("S0446:'` - zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] FEATURES EN/RU/UK updated.
- [ ] Catalog regenerated; dev-log complete.
- [ ] On-device checklist passed (BlockNeedUserTest -> Verified); zero stale `S0446:` tags afterward.

---

## Handoff Notes to Next Phase

- Final phase. After device verification, `/spec-check S0446` confirms `Verified`.

---

## Rollback Plan

Docs/catalog/dev-log are additive; revert the FEATURES edits and regenerate the catalog if the feature is pulled.
