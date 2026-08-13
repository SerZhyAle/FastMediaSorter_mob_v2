# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0303_telegram-integration.md`](../S0303_telegram-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all implemented phases (01; 02 and 03 if not skipped)
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Regenerate the class catalog, flag the `noLegal`-only classes, and update the feature docs to reflect the shipped capabilities (public send-to-Telegram; gitignored t.me download).

---

## Prerequisites

- [ ] All implemented phases are ✅ Done (or ⏭️ Skipped).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` | Modified | - |
| `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` | Regenerated | - |

---

## Steps

### Step 04.1 - Update feature docs

**Files:** `docs/FEATURES.md` + `_RU` + `_UK`; `docs/FEATURES_noLegal.md` + `_RU` + `_UK`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one concise bullet for "Send to Telegram" under the share-related area in the public `docs/FEATURES.md` trilingual set (use `/doc-update`). Add the t.me download capability only to the gitignored `docs/FEATURES_noLegal.md` trilingual set - never to the public files. Skip the download entry entirely if Phase 02 was not implemented; skip the Bot API note if Phase 03 was skipped.

**Verification:**

- `Grep` - the Telegram send bullet present in all three public FEATURES files.
- `Grep` - the t.me download entry present only in the `_noLegal` files, absent from public files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification PASS. "Send to Telegram" bullet present in FEATURES.md/_RU/_UK (actual 1 each); t.me download bullet present in FEATURES_noLegal.md/_RU/_UK (actual 1 each); public files contain no `t.me`/`embed=1`/`CANONICAL_ORDER` leak (actual: none). Files: 3 public + 3 noLegal FEATURES (gitignored). Dev log recorded.

---

### Step 04.2 - Flag noLegal-only classes in the catalog

**Files:** `dev/CATALOG/app_v2.jsonl` (via `set.ps1`)
**Depends on:** Step 04.1

**Prompt for developer:**

> For each `noLegal`-only class introduced (`TelegramExtractionStrategy`, and if Phase 03 shipped `BotApiTelegramUploadTarget` + `NoLegalTelegramUploadModule`), record the non-flavor list via `set.ps1 -NoFlavors "<flavors that exclude it>"`. Confirm the exact list against the flavor inclusion hierarchy (standard ⊂ vr ⊂ noLegal) before recording - these classes are present only where the noLegal source set compiles.

**Verification:**

- `Grep` - each noLegal-only class carries a non-empty `NoFlavors` value in `app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification PASS. `TelegramExtractionStrategy` noFlavors | expected: non-empty | actual: `["standard","lite","photos","legacy","vr"]` (present only where the noLegal source set compiles). Phase 03 was skipped, so no Bot API classes to flag. Files: dev/CATALOG/app_v2.jsonl (manual field). Dev log: catalog is gitignored, no dev-log entry required.

---

### Step 04.3 - Regenerate catalog and record dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Confirm dev log entries exist for every file modified across all phases. Append the functionality-log line(s) via `scripts/add_to_functionality_log.ps1 -Id S0303 -Op ADD`.

**Verification:**

- `catalog_sync.ps1` - exit 0.
- `Grep` - `S0303` appears in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. catalog_sync exit 0 (1234 files, 1499 records scanned + rendered; manual noFlavors preserved); `S0303` in dev/FUNCTIONALITY.log | actual:1. Functionality log: ADD - send to Telegram + noLegal t.me download. Files: dev/CATALOG/app_v2.{jsonl,md} (gitignored, regenerated).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1499 records).
- [x] Dev log complete for all modified files across the spec.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0303`.

---

## Rollback Plan

Docs and catalog are regenerable - revert the doc edits; the catalog is rebuilt from source by `catalog_sync.ps1`.
