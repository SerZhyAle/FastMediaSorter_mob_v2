# Phase 05 - Docs, inventory and catalog cleanup

**Strategic spec:** [`../S1832_stable-channel-identity-survives-prune.md`](../S1832_stable-channel-identity-survives-prune.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Record the delivered capability, correct the one registered document whose contract verdict this ticket
falsifies, and regenerate the derived indexes.

---

## Prerequisites

- [x] Phases 01 through 04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `docs/STREAM_CATALOG_CONSUMERS.md` | Modified | ≤ 40 changed lines |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended via script | - |

---

## Steps

### Step 05.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `scripts/all_features/add.ps1` - never by hand - describing, in English, that a
> channel keeps its pin, its position in the pinned list, its play history and its desktop cell when the
> catalog drops it and a later catalog brings it back, and that a channel republished under `http`
> instead of `https` is recognised as the same channel. Not flavor-gated.
> Then run `scripts/all_features/validate.ps1`.

**Why:**

Strategic §8 routes this ticket's user-visible effect to the developer inventory rather than to the
public showcase, and `/spec-all` surfaces a missing record as an unresolved manual item at close.

**Verification:**

- `Grep` - `S1832` present in `docs/ALL_FEATURES.jsonl` exactly once.
- `scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 05.2 - Correct the external-consumer contract document

**Files:** `docs/STREAM_CATALOG_CONSUMERS.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> That document records, per contract rule, whether a check enforces it, the code merely happens to
> satisfy it, or nothing protects it. One of its rules states that the channel address is compared
> byte-exactly, on their side and on ours. Ours is no longer byte-exact: the app now compares by the
> derived identity, which folds scheme case, host case, a trailing slash, a default port and the
> difference between `http` and `https`. Update that rule's verdict and say what our side now does, and
> what it still requires of the publisher - an address whose host or path really changes is still a
> different channel and still costs the user everything.
> Do not change what we publish or the CSV format - that is S1828's scope and this ticket's non-goal.

**Why:**

The strategic spec's §0 records the external consumer's own framing that addresses must be more stable
than names, and a contract register that still claims our side compares byte-exactly would send the next
reader to the wrong conclusion about what a cosmetic address edit costs.

**Verification:**

- `Grep` - `identity` present in `docs/STREAM_CATALOG_CONSUMERS.md`.
- `Grep` - the document still contains no change to the published asset names.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit 0.

**Status:** `[x]` done

---

### Step 05.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket. Then set `role` and `status`
> for the three classes this ticket introduced - `StreamChannelIdentity`, `StreamUserStateEntity` and
> `StreamUserStateDao` - through `set.ps1`, and confirm the two deleted play-outcome classes are gone
> from the index.

**Why:**

CLAUDE.md's catalog rules make the index the first stop of every later investigation, and a new public
type absent from it sends the next reader to a global grep instead.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamUserState*"` returns two records.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamPlayOutcome*"` returns zero records.

**Status:** `[x]` done

---

### Step 05.4 - Close the ticket through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Close through `scripts/close-and-log.ps1` naming the whole changed set, with `-ScopeToFile` so the
> scoped gates judge this ticket's files rather than whatever else is in flight. Read the verdict line:
> only a bare `post-change: PASS` is clean, and `PASS WITH ADVISORIES` names what still needs reading.
> Then set the status. Device verification is required by §11 criterion 5 - the migration has to be
> proven on a device that already carries pins - so the ticket goes to `BlockNeedUserTest` with a note
> saying exactly that, and the debug tags go in before the final build, one per changed flow entry.

**Why:**

Strategic §11 criterion 5 requires the migration to be proven on a device with accumulated pins rather
than on a clean install, which no gate and no emulator sweep in this repository can substitute for.

**Verification:**

- `close-and-log.ps1` - exit 0, verdict line read and quoted.
- `Grep` - `Timber.d("S1832:` present at each changed flow entry, and nowhere else.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1832 -Format json` shows `BlockNeedUserTest` with a status note.

**Status:** `[x]` done

---

## Corrections made while implementing

- **Step 05.2's premise was false.** The step said `docs/STREAM_CATALOG_CONSUMERS.md` carries a contract
  rule stating the channel address is compared byte-exactly on both sides, and told the developer to
  amend that rule's verdict. Grepping the whole file, the invariants table included, found no such row,
  and no other row makes that claim. The invariants block is hand-maintained - no generator reads its
  marker - so there was nothing to regenerate either. The change was written instead as a new entry
  under "Findings that read wrong from the outside", which is where that document already keeps
  statements about what a reader would otherwise conclude wrongly.
- **`set.ps1 -Status active` is not a value.** Step 05.3 needed the three new classes marked; the
  catalog's status set is `new,tested,legacy,todo,unknown`. All three were set to `tested`.
- **The merge probe was first written as a multi-line `Timber.d(` call.** CLAUDE.md removes these tags
  by grepping for the literal `Timber.d("S1832:`, which a call whose format string sits on the next line
  does not match - the tag would have outlived the ticket silently. Rewritten so the literal and the
  format string share one line, which is now true of all five tags.

---

## Verification actually run

- `scripts/all_features/validate.ps1` - exit 0, 759 records, one carrying `S1832`.
- `scripts/document_registry/validate.ps1` and `generate.ps1 -Check` - exit 0 after the consumer-doc edit.
- `dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamUserState*"` - 2 records; `"StreamPlayOutcome*"` - 0.
- `grep 'Timber.d("S1832:'` over `app_v2/src` and `wear/src` - 5 tags, one per changed flow entry.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` untouched - strategic §8 routes this to `ALL_FEATURES.jsonl`.
- [x] `dev/CHANGELOG.md` carries exactly one row for this ticket's closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation and index changes only. Revert the phase commit; nothing user-visible and no schema.
