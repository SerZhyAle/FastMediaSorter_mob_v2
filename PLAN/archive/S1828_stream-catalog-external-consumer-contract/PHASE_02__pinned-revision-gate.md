# Phase 02 - Pinned-revision check

**Strategic spec:** [`../S1828_stream-catalog-external-consumer-contract.md`](../S1828_stream-catalog-external-consumer-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Add `scripts/quality/assert-stream-asset-revisions.ps1`, which reads the pinned-asset block written in Phase 01 and refuses a publication whose revision defaults would stop republishing a pinned asset; wire it into the publishing path and into the fast-gate batch.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the `pinned-assets` block exists and parses.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-stream-asset-revisions.ps1` | New | ≤ 160 |
| `scripts/streams/collect-stream-candidates.ps1` | Modified | ≤ 25 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 15 |

> `scripts/streams/collect-stream-candidates.ps1` is over 500 LOC, so Step 02.2 carries an explicit backup sub-step per CLAUDE.md Rule 5.

---

## Steps

### Step 02.1 - Write the pinned-revision check

**Files:** `scripts/quality/assert-stream-asset-revisions.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/quality/assert-stream-asset-revisions.ps1`. It reads the block between `<!-- pinned-assets:begin -->` and `<!-- pinned-assets:end -->` in `docs/STREAM_CATALOG_CONSUMERS.md` to obtain the pinned `(asset base name, revision)` pairs, then reads the revision defaults out of `scripts/streams/collect-stream-candidates.ps1` - `$SheetRev` and `$CoordsRev`, both currently `v3` at lines 183 and 184. Compute the asset names the next run would publish: `channel-preview-atlas-{SheetRev}.webp` and `stream-logo-atlas-{SheetRev}.webp`, `channel-preview-coords-{CoordsRev}.json` and `stream-logo-coords-{CoordsRev}.json`.
>
> Fail when a pinned pair whose `Coverage` reads `default` is absent from that produced set, and name the lost asset file name in the message together with the registry line that pins it and the revision that displaced it. A pinned pair whose `Coverage` reads `frozen` is deliberately no longer republished, so its absence from the produced set is the expected state and passes. Fail separately, and differently, when the markers are missing or the block parses to zero pairs: an unreadable registry must not read as "nothing is pinned".
>
> The two tokens are what make the check useful rather than permanently red: the consumer pins both the `-v1` and `-v3` preview assets while the publisher has one revision default, so a rule demanding every pinned name be produced would refuse every publication forever. Bumping `$SheetRev` from `v3` to `v4` without first recording the `v3` rows as `frozen` is the failure this catches.
>
> Give the file a header listing every exit code it returns, per CLAUDE.md Rule 7, and make each listed code reachable. Suggested: 0 pass, 1 a pinned asset would stop being published, 2 the registry or the publisher could not be read.

**Why:**

Strategic §5.1 pillar 3 states that publication must refuse to start when a run would drop a revision the registry declares pinned, chosen first among the unprotected rules because the consequence - every client on the old revision losing preview loading - is irreversible on our side. ADR-2 requires the check to read the pinned names from the registry rather than hold its own copy, because two places holding one list diverge silently.

**Verification:**

- `Glob` - `scripts/quality/assert-stream-asset-revisions.ps1` exists.
- `Grep` - `pinned-assets:begin` present (the check parses the marker, not a heading).
- `Grep` - `SheetRev` and `CoordsRev` both present.
- `Grep` - `frozen` present, so the check distinguishes the two coverage tokens rather than treating every pin alike.
- `Grep` - header lists exit codes `0`, `1` and `2`.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.
- Run the script on the live tree and record `expected: <verdict> | actual: <verdict>` with its exit code.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Gate written and proven: exit 0 on the live tree, exit 1 on a fixture bumping SheetRev v3->v4 (message named channel-preview-atlas-v3.webp and stream-logo-atlas-v3.webp), exit 2 on a registry with the markers stripped. assert-exit-contract PASS. Guard at collect-stream-candidates.ps1:2972, once, ahead of all four dispatch sites (2986/2990/3010/3014); publisher parses with 0 syntax errors and was never executed. Registered in assert-fast-gates.ps1: a.ps1 fg PASS, gate listed at 300 ms.
- 2026-08-20 - Reproducing the two failure verdicts without any scratch artifact: copy docs/STREAM_CATALOG_CONSUMERS.md and scripts/streams/collect-stream-candidates.ps1 into an empty tree keeping those relative paths, change the SheetRev default from v3 to v4, then run assert-stream-asset-revisions.ps1 -RepoRoot <that tree>. Expected exit 1, naming channel-preview-atlas-v3.webp and stream-logo-atlas-v3.webp as pinned default rows the run would stop publishing. Deleting the pinned-assets:begin marker in that copy instead yields exit 2 with 'must not read as nothing is pinned'.

---

### Step 02.2 - Refuse publication before upload

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Take a timestamped backup of `scripts/streams/collect-stream-candidates.ps1` before editing it, per CLAUDE.md Rule 5 - the file is over 500 LOC, and that rule names the scratch location.
>
> Call the Step 02.1 check from the publishing path so it runs before the first `gh release upload`, and make a non-zero exit abort the run with the check's own message. Place the call so it covers both revisioned publishers, `Invoke-PublishChannelPreviewAtlas` (line 2228) and `Invoke-PublishStreamLogoAtlas` (line 2467), rather than duplicating it into each.

**Why:**

Strategic §3.1 records the owner's requirement that the check refuse before upload rather than after, because rolling an asset back on GitHub costs more than a refusal. Strategic §11 criterion 2 requires the stopped publication to name the lost asset.

**Verification:**

- Re-run the gate the guard calls: `pwsh -NoProfile -File scripts/quality/assert-stream-asset-revisions.ps1` - expected `assert-stream-asset-revisions: PASS - 4 pinned asset(s) still published, 2 frozen and untouched.`, exit 0.
- `Grep` - `assert-stream-asset-revisions` present in `collect-stream-candidates.ps1`.
- `Grep` - the guard sits in the top-level dispatch and precedes every call to `Invoke-PublishChannelPreviewAtlas` and `Invoke-PublishStreamLogoAtlas` by line number. Comparing against the `gh release upload` lines instead would be wrong: those sit inside function bodies defined far earlier in the file than the dispatch that runs them.
- `Grep` - the call appears exactly once, not once per publisher.
- The modified publisher still parses - `[Parser]::ParseFile` reports zero syntax errors. The script is never executed to verify this: running it publishes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Gate written and proven: exit 0 on the live tree, exit 1 on a fixture bumping SheetRev v3->v4 (message named channel-preview-atlas-v3.webp and stream-logo-atlas-v3.webp), exit 2 on a registry with the markers stripped. assert-exit-contract PASS. Guard at collect-stream-candidates.ps1:2972, once, ahead of all four dispatch sites (2986/2990/3010/3014); publisher parses with 0 syntax errors and was never executed. Registered in assert-fast-gates.ps1: a.ps1 fg PASS, gate listed at 300 ms.

---

### Step 02.3 - Register the check in the fast-gate batch

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add the Step 02.1 check to the batch `assert-fast-gates.ps1` runs, following the registration shape the neighbouring gates already use in that file: a comment naming the ticket and what the gate catches, then the script name mapped to its extra arguments. The file has no per-file scoping mechanism and its map is ordered cheapest first, so keep the gate cheap - two file reads and a regex - rather than trying to scope it. List it in the header synopsis as well, which is how every other gate in the batch is discoverable.

**Why:**

Strategic §1 records the measurement that no gate under `scripts/quality/` reads the publishing script and that `.\a.ps1 fg` therefore catches a regression in none of the eleven invariants; registering the check is what changes that for the one invariant this ticket closes.

**Verification:**

- `Grep` - `assert-stream-asset-revisions` present in `scripts/quality/assert-fast-gates.ps1`.
- Run `pwsh -NoProfile -File ./a.ps1 fg` - exit 0, and the batch's output names the new gate.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Gate written and proven: exit 0 on the live tree, exit 1 on a fixture bumping SheetRev v3->v4 (message named channel-preview-atlas-v3.webp and stream-logo-atlas-v3.webp), exit 2 on a registry with the markers stripped. assert-exit-contract PASS. Guard at collect-stream-candidates.ps1:2972, once, ahead of all four dispatch sites (2986/2990/3010/3014); publisher parses with 0 syntax errors and was never executed. Registered in assert-fast-gates.ps1: a.ps1 fg PASS, gate listed at 300 ms.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] No app build - strategic §3.3 states the app build is not needed; validation is the new check plus `document_registry/validate.ps1`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `.\a.ps1 fg` exits 0.
- [x] Dev log entry added for every file in "Files Touched" (or deferred to Phase 04's batch).

---

## Handoff Notes to Next Phase

The check is the address the Phase 01 verdict table cites for the pinned-revision rule; if Phase 03 renames either file, that table's `Address` cell changes with it.

---

## Rollback Plan

Delete `scripts/quality/assert-stream-asset-revisions.ps1` and revert the two call sites; the publisher edit is a single self-contained guard block marked `S1828`. No data migration or user-facing surface changed.
