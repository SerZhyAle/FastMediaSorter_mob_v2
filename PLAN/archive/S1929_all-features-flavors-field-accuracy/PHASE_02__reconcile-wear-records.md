# Phase 02 - Reconcile the existing records, one judgement at a time

**Strategic spec:** [`../S1929_all-features-flavors-field-accuracy.md`](../S1929_all-features-flavors-field-accuracy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Every `wear.*` inventory record either carries the flag it lives behind with a matching flavor set, or is recorded as living behind no flag - each decision made from its actual consumer.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done, so a wrong set now fails rather than passing quietly.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | 8 records at most |

---

## Steps

### Step 02.1 - Judge each record by its consumer

**Files:** none - research only
**Depends on:** - start of phase

**Prompt for developer:**

> For each of the eight `wear.*` records, find the capability's actual consumer and decide whether it needs the phone-side bridge that lives in the `wearGms` source set. Read the record's own spec and the consumer class; do not decide from the `wear.` id prefix. Record, per record: the verdict, the evidence, and whether the verdict is evidenced or inferred. A record you cannot settle is left alone and said so - an unsupported "fix" to a correct record is worse than the inconsistency.

**Why:**

Strategic §2 Non-goals and §7 both name judging by prefix as the way this phase goes wrong, and the capture warns by name that `documentation_site_pages` is not a runtime capability - sweeping the eight to one set would corrupt a record that is currently right.

**Verification:**

- Recorded in this file: one row per record with verdict, evidence and confidence.
- Every record marked "uncertain" is left unmodified in Step 02.2.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1929 step 02.1

---

### Step 02.2 - Apply the verdicts

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `gate` and correct `flavors` only for the records Step 02.1 settled as gated. Leave the ungated and the uncertain ones untouched. Then run the validator over the whole inventory and confirm every record passes, including the 775 the ticket never looked at.

**Why:**

Strategic §11.6 requires all 783 records to pass afterwards, because a data fix that trades one class of wrong record for a validator that now refuses unrelated ones has moved the problem rather than solved it.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/all_features/validate.ps1` - expected: exit 0, 783 records.
- Recorded in this file: the before and after flavor set of every record changed.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1929 step 02.2

---

## Evidence (2026-08-21)

**Step 02.1 - eight records, judged from their consumers.** Each verdict below rests on the record's own spec plus the consumer class, not on the `wear.` prefix.

| # | Record | Gated? | Evidence | Action |
| --: | --- | --- | --- | --- |
| 1 | `home-sections-and-selective-transfer` | **split** | Watch section rendering is not gated (the `wear` module declares no flavors); the selective-transfer picker is - `WearSyncSettingsFragment.kt:92` sets `showResourceSelection = mediaCapabilities.supportsWearCompanion` | left alone, parked as S1933 |
| 2 | `file-list-grid-view-and-thumbnails` | **split** | Network/local grid is not gated - `WearThumbnailRepositoryImpl.kt:104-119` fetches SMB/FTP/SFTP straight from the watch; the phone-folder grid is, through `PhoneResourceClient` -> `PhoneWearListenerService` | left alone, parked as S1933 |
| 3 | `documentation_site_pages` | **no - not a runtime capability** | S1801 states no app code changes at all; these are site pages | left alone, no `gate` |
| 4 | `favourites-list-on-watch` | **no** | `WearFavoritesRepositoryImpl.kt:19-35` uses local `EncryptedSharedPreferences` only; S1846 states there is no incoming transfer from the phone at all | left alone, no `gate`; its narrow flavor set parked as S1934 |
| 5 | `phone-media-browse-by-type` | **yes** | Watch `PhoneResourceClient` -> `PhoneWearListenerService.kt:74,159-193`, mounted only in `src/wearGms` | `gate` added, flavors corrected `standard,noLegal` -> `standard,noLegal,legacy` |
| 6 | `settings-group-master-switch` | **yes** | `OperationsWearGroupManager.kt:40` - `isAvailableInBuild = mediaCapabilities.supportsWearCompanion` | `gate` added, flavors already correct |
| 7 | `network-thumbnail-loading` | **no** | S1888's fix lives entirely in `wear/`, touches no `app_v2` file, and was verified with `a.ps1 fw`; the watch module has no flavors | left alone, no `gate`; its `["standard"]` set parked as S1934 |
| 8 | `open_virtual_resource_item` | **yes** | Same consumer chain as #5; fix sits in use cases reachable only from the wearGms listener | `gate` added, flavors already correct |

Three records changed, five deliberately not. The two "split" verdicts are the finding that mattered most: a record covering both a gated and an ungated half cannot carry a single `gate` honestly - tagging it would assert the ungated half is gated, and leaving it untagged leaves the gated half unchecked. Both answers are wrong, so the record boundary is what needs deciding, and that is S1933 rather than a value to be guessed here.

**Step 02.2 - the result.**

```
wear.home-sections-and-selective-transfer   standard,noLegal,legacy                  gate=-
wear.file-list-grid-view-and-thumbnails     standard,legacy                          gate=-
wear.documentation_site_pages               standard,lite,photos,legacy,vr,noLegal   gate=-
wear.favourites-list-on-watch               standard,noLegal                         gate=-
wear.phone-media-browse-by-type             standard,noLegal,legacy                  gate=SUPPORT_WEAR_COMPANION
wear.settings-group-master-switch           standard,noLegal,legacy                  gate=SUPPORT_WEAR_COMPANION
wear.network-thumbnail-loading              standard                                 gate=-
wear.open_virtual_resource_item             standard,noLegal,legacy                  gate=SUPPORT_WEAR_COMPANION
```

`ALL_FEATURES validation PASS: 783 record(s)`, exit 0 - every record still passes, including the 775 this ticket never examined. Records were rewritten through `add.ps1`'s upsert rather than by hand.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] No record was changed without a recorded per-record verdict.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The inventory now agrees with the matrix wherever it makes a claim about a flag. Phase 03 carries documentation and closure only.

---

## Rollback Plan

Revert the inventory lines - the mechanism from Phase 01 stays and simply guards nothing again.
