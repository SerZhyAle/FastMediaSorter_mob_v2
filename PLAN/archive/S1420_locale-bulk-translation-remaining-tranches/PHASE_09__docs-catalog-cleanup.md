# Phase 09 - Docs and catalog cleanup

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Close the ticket mechanically: capability record, closure facade, and the coverage evidence that strategic §11 is written against.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done, or explicitly marked ⏭️ Skipped with a reason in the Blockers Log.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | n/a - appended by CLI |
| `dev/CHANGELOG.md` | Modified | n/a - appended by CLI |

> `docs/FEATURES*.md` is not touched: strategic §8 states no new capability appears, only the completeness of an already-shipped one changes, and those files are owned by `/skill-release`.

---

## Steps

### Step 09.1 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Append one record with `scripts/all_features/add.ps1` describing the widened locale coverage, naming the languages actually completed and the flavors the record applies to. Take the flavor list from the gate rather than from this plan. If Phase 08 was skipped, the record covers `main` only and says so.

**Why:**

Strategic §8 states the user-visible effect is that a user on one of the ten new languages sees more of the app in their language, which is a shipped capability change even though no new feature appears.

**Verification:**

- `Grep` - `S1420` appears in the `spec` field of exactly one record in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1`: expected exit 0, actual must equal expected.

**Status:** `[x]` done - 2026-08-14. Record `ui.interface-languages` (area `User Interface`, spec `S1420`), all six flavors - `main` resources ship in every one of them, and Phase 08 completed the `vr` and `noLegal` sets, so no flavor is excluded. `validate.ps1` exit 0, 696 records.

---

### Step 09.2 - Capture the coverage evidence

**Files:** none - produces evidence for the audit
**Depends on:** Step 09.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1` and record its full per-locale output as the evidence `/spec-check` reads strategic §11 against. Record the reading whatever it says - a partial run that stopped mid-plan reports a non-zero shortfall, and that number is the honest state of the ticket, not a failure to hide.

**Why:**

Strategic §11 states all three completion criteria as readings of this gate, so the audit needs the reading rather than a claim about it.

**Verification:**

- Gate output captured for all ten locales plus the `en`/`ru`/`uk` strict line.
- Strict line reads `OK: no strict-locale gaps in main` - strategic §11 criterion 3.

**Status:** `[x]` done - 2026-08-14. `check_strings_localized.ps1`, exit 0 on all three source sets.

`main` - 4468 keys. Untranslated per best-effort locale: `zh-Hans` 99, `hi` 100, `es` 90, `fr` 89, `ar` 96, `bn` 93, `pt` 92, `ur` 89, `de` 94, `it` 90. Strict line: `OK: all 4468 key(s) present in en/ru/uk` and `OK: no strict-locale gaps in main`.

`vr` - 53 keys, 2 untranslated in each of the ten. `noLegal` - 17 keys, 0 untranslated in any. Both strict lines clean.

Read against the entry state of this ticket - 1887 untranslated per locale - the residue is 89-100, and it is not arbitrary: 91 units the exporter declines by design (symbols and layout literals, shrinking as `S1550` marks them `translatable="false"`) plus the 19 keys owned by `S1626`, whose English phrasing leaves a format placeholder standing alone. `S1627` froze exactly those 19 as its baseline, so the number is now accounted for key by key rather than estimated.

---

### Step 09.3 - Run the closure facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 09.2

**Prompt for developer:**

> Close through `scripts/post-change.ps1` with `-ChangeType Mixed`, naming the whole changed set with `-Files` and adding `-ScopeToFile`, so the scoped gates judge this ticket's files rather than other tickets' work in progress on the same tree. Read the verdict: only a bare `post-change: PASS` is clean, and `PASS WITH ADVISORIES` names each advisory to read.

**Why:**

The tree carries work in progress from other tickets, so an unscoped closure would fail on findings this ticket did not introduce.

**Verification:**

- `post-change.ps1` exit code: expected 0, actual must equal expected.
- One dev-log entry exists for this ticket, not one per touched file.
- Settings-doc-sync gate passes without a manifest regeneration - this ticket changes no setting's presence, behavior, position or naming, only the locale text of existing ones.

**Status:** `[x]` done - 2026-08-14. `post-change.ps1 -ChangeType Mixed -ScopeToFile` verdict `PASS`, exit 0, one dev-log row for the whole set. Settings-doc-sync skipped as predicted - no changed file is a settings surface.

The first run returned `PASS WITH ADVISORIES`: the `feature-inventory` registry record covers `docs/ALL_FEATURES.jsonl` and had not been acknowledged. Its sibling, `docs/FEATURES*.md`, is deliberately not touched here - that file is written only by `/skill-release`, from the inventory diff since the previous release, so the record added above is exactly how this ticket reaches the showcase.

---

## Phase Done Criteria

- [ ] Every `Step 09.*` above is `[x] done`.
- [ ] `post-change.ps1` returned 0.
- [ ] Coverage gate output recorded for the audit.
