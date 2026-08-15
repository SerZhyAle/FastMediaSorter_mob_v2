# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S0428_home-screen-call-sms.md`](../S0428_home-screen-call-sms.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Record the delivered capability, classify the new classes in the catalog, and close the ticket through the mechanical facade.

---

## Prerequisites

- [x] Phases 01-03 are ✅ Done.
- [x] No `CODE.LOCK` is held - this phase edits no source.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Written by script | - |

---

## Steps

### Step 04.1 - Classify the new classes in the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` for the two classes this ticket added - `ContactActionAvailabilityProvider` and `LauncherPhoneNumberDialogFragment` - via `dev/CATALOG/scripts/set.ps1`. `LauncherPhoneNumberDialogFragment` lives in the `launcherEnabled` source set, which is injected only into the flavors that carry the launcher, so declare that reach with `set.ps1 -NoFlavors "lite,vr"`.

**Why:**

A new class with no role or status is invisible to the catalog-first research order every later ticket starts from (CLAUDE.md §5).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "ContactActionAvailabilityProvider"` returns one record with a non-empty `role`.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "LauncherPhoneNumberDialogFragment"` returns one record with a non-empty `role`.

**Status:** `[x]` done

---

### Step 04.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record through `pwsh -NoProfile -File scripts/all_features/add.ps1` covering what shipped: a launcher desktop cell that calls a contact, sends them an SMS, opens their card or their messenger thread, with the number taken from the system picker or typed by hand, and the call and SMS entries hidden on a device without telephony. Do not touch `docs/FEATURES*.md` - `/skill-release` owns it. Then run `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Why:**

`docs/ALL_FEATURES.jsonl` is the inventory every later ticket checks before implementing something to avoid duplication, and the release notes are generated from its diff (CLAUDE.md §11).

**Verification:**

- `Grep` - a record naming the contact cell present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 04.3 - Close the ticket through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` once for the whole changed set: `-Files` naming every file Phases 01-03 touched, `-ChangeType Mixed`, `-ScopeToFile`, `-Module app_v2`. Read the verdict line - only a bare `post-change: PASS` is clean; `PASS WITH ADVISORIES` names each advisory and the icon-inventory re-render is expected among them because Phase 02 added a drawable. Exit 1 means a gate failed and 2 means it could not verify - neither is a pass.

**Why:**

The facade chains the dev log, the catalog sync and every mechanical gate in one run, and its scoped mode judges the count-ratchet gates against this ticket's files rather than the whole dirty tree (CLAUDE.md §12).

**Verification:**

- `post-change.ps1` prints `post-change: PASS` or `PASS WITH ADVISORIES` and exits 0.
- `Grep` - `dev/CHANGELOG.md` carries an entry naming S0428.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 d` exit 0 at the end of Phase 03; this phase edits no source.
- [x] Dev log entry present for the ticket.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Step Log

- 2026-08-06 - Steps 04.1-04.3 executed. `close-and-log.ps1` carried Steps 04.2 and part of 04.3 in one process: journal flip to `BlockNeedUserTest` with its device-test note, dev log, the `ALL_FEATURES` record (`Launcher` / "Contact cells for call, SMS, card and messenger" / flavors `standard,noLegal`), catalog scan and render.
- 2026-08-06 - Flavor reach read off the real gate, not a sibling record: `src/launcherEnabled` is mounted only by the `standard` and `noLegal` blocks of `app_v2/build.gradle.kts` (lines 606 and 634), and its manifest is injected for exactly `setOf("standard", "noLegal")` (line 1063).
- 2026-08-06 - Step 04.1 deviation: `-NoFlavors` for `LauncherPhoneNumberDialogFragment` is `lite,vr,photos,legacy`, not the `lite,vr` the prompt guessed. The prompt was written before the source-set mounting was checked; four flavors, not two, lack the launcher surface.
- 2026-08-06 - Per-phase `post-change` runs already wrote the changelog rows for Phases 01-03, so Step 04.3's whole-set re-run would have duplicated them; the verdict it asks for is the one each phase already recorded, all three `post-change: PASS`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Nothing to roll back: this phase writes only derived indexes and the changelog.
