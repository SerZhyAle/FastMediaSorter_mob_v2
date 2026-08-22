# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1831_video-channel-thumbnail-from-verification-pass.md`](../S1831_video-channel-thumbnail-from-verification-pass.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Put the preview sheet's real boundaries into the registry that owns them and record the delivered capability,
so the next person deciding whether a taller sheet is safe reads the answer instead of re-deriving it.

---

## Prerequisites

- [x] Phase 01 is ✅ Done - its Step 01.5 run produced the measured figures these documents quote.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/STREAM_CATALOG_CONSUMERS.md` | Modified | +8 net |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |

---

## Steps

### Step 03.1 - Record the preview-sheet byte ceiling in the consumer registry

**Files:** `docs/STREAM_CATALOG_CONSUMERS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the preview sheet's 48 MiB limit to the pinned-numbers and invariants tables, attributed to StreamsPlayer, with a verdict column entry saying it is now checked by the gate Phase 01 Step 01.4 added. State next to it that this is a different contract from the favicon atlas's 30 MiB `MaximumAtlasBytes` and that the two must not be conflated.

**Why:**

That document exists, in its own registry record's words, to hold "the asset names and numbers each consumer
has hard-coded", and research 01 found the 48 MiB figure living only inside a closed spec - so a reader
consulting the registry today finds the 30 MiB favicon number, applies it to the wrong asset, and reaches a
different answer about whether a taller sheet is safe.

**Verification:**

- `Grep` - `48 MiB` appears in `docs/STREAM_CATALOG_CONSUMERS.md`.
- `Grep` - the same lines distinguish it from the 30 MiB favicon ceiling by name.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one record via `scripts/all_features/add.ps1` describing what the user gains: a broadcast frame on the card of every live video channel the capture reaches, no longer limited to the first two thousand.

**Why:**

Strategic §8 describes a user-visible change, and CLAUDE.md section 11 makes this inventory the place a spec
records its delivered capability - the public showcase is generated from its diff at release time and is
never edited per spec.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - `S1831` appears in the `spec` field of exactly one record.

**Status:** `[x]` done

---

### Step 03.3 - Close the ticket through the facade

**Files:** none - closure only
**Depends on:** Step 03.2

**Prompt for developer:**

> Run the closure facade over the whole changed set with `-ScopeToFile`, then advance the catalog status. If Phase 02 has not run, strategic §6.2 is still Open and needs a `Carrier: Sxxxx` token naming the ticket that takes the question on, before any transition into `Implemented` or `Verified`.

**Why:**

CLAUDE.md section 4 refuses a close whose research items are neither Resolved nor carried, because a closed
ticket otherwise leaves the queue and takes its open question with it - measured across 1 506 closed specs,
which stranded 372 such items.

**Verification:**

- `scripts/post-change.ps1` prints `post-change: PASS` or `PASS WITH ADVISORIES` and exits 0.
- `pwsh -NoProfile -File scripts/spec_catalog/check-open-items-carried.ps1 -Id S1831` exits 0.
- `select.ps1 -Id S1831` reports the intended status.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- [x] Closure through `scripts/post-change.ps1` with the full changed set and `-ScopeToFile`.

---

## Note on Step 03.1 and a sibling session

While this phase ran, a concurrent session working S1828 wrote
`dev/handoff/streams-source-spec/10_contract_amendment_2026-08-20.md`, which already documents the preview
sheet's new shape for external consumers - the retired 8192 budget, the 877-of-2917 gap it caused, the 16383 px
and 48 MiB ceilings, the fail-rather-than-truncate behaviour, and "87 of 2 917" as the only remaining reason a
video channel lacks a tile. It was checked against the code as landed and is accurate, so nothing was
duplicated into it here. The consumer-registry rows this phase added are the machine-readable half of the same
fact and are not redundant with it: the gate reads the registry, not the handoff prose.

---

## Handoff Notes to Next Phase

Final phase of what could run - see INDEX.md Completion Gate. Phase 02 remains blocked on the owner.

---

## Rollback Plan

Documentation and one inventory record only. Revert the two files; no code, no published asset, no migration.
