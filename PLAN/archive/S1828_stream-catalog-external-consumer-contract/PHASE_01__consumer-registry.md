# Phase 01 - Consumer registry and verdict table

**Strategic spec:** [`../S1828_stream-catalog-external-consumer-contract.md`](../S1828_stream-catalog-external-consumer-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Create `docs/STREAM_CATALOG_CONSUMERS.md`: one record per external consumer of our release assets, a machine-readable list of pinned asset names, and one row per contract invariant carrying an explicit verdict.

---

## Prerequisites

- [ ] Strategic §6 items 1 and 2 are Resolved (they are; §6.3 is carried by S1835).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/STREAM_CATALOG_CONSUMERS.md` | New | ≤ 250 |

---

## Steps

### Step 01.1 - Create the consumer registry document with a machine-readable pinned-asset block

**Files:** `docs/STREAM_CATALOG_CONSUMERS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `docs/STREAM_CATALOG_CONSUMERS.md` in English. Open with one record for the external consumer StreamsPlayer (Windows): the artifacts it reads, the names and numbers pinned in its code, and how it confirms it accepted a build. Transcribe those facts from strategic §0, which holds the maintainer's note verbatim; do not restate the catalog format, which `dev/handoff/streams-source-spec/01_delivery_contract.md` already describes.
>
> Inside the record, put the pinned asset names in a block delimited by the literal HTML comments `<!-- pinned-assets:begin -->` and `<!-- pinned-assets:end -->`, as a markdown table with the columns `Asset base name`, `Pinned revision`, `Coverage`, `Consumer`, `Reason`. This block is the single source Phase 02's check reads, so every pinned name appears here exactly once.
>
> `Coverage` holds exactly one of two literal tokens. `default` means the publisher's current revision defaults still produce this name on every run. `frozen` means the name is deliberately no longer republished and must never be deleted. Per strategic §0's second letter the consumer pins both revisions of the preview pair - `channel-preview-atlas-v1.webp` and `channel-preview-coords-v1.json` alongside the `-v3` pair - and switches to neither on its own, so `v1` is recorded `frozen` and `v3` `default`. That distinction is the point of the block: strategic §4 records that `v1` survives today only because nothing deletes it, and writing it as `frozen` turns that accident into a stated rule.

**Why:**

Strategic §2 goal 1 requires one place stating who outside reads our artifacts and what each consumer pinned, so that the question "may this asset be renamed" has an address instead of requiring correspondence. ADR-2 requires the pinned names to live in this registry rather than in the check, because a second independent literal is the exact failure this ticket documents elsewhere.

**Verification:**

- `Glob` - `docs/STREAM_CATALOG_CONSUMERS.md` exists.
- `Grep` - `<!-- pinned-assets:begin -->` matches exactly once.
- `Grep` - `<!-- pinned-assets:end -->` matches exactly once.
- `Grep` - `StreamsPlayer` present.
- `Grep` - all four base names present: `channel-preview-atlas`, `channel-preview-coords`, `stream-logo-atlas`, `stream-logo-coords`.
- `Grep` - both `default` and `frozen` appear as `Coverage` values inside the block.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Wrote docs/STREAM_CATALOG_CONSUMERS.md: StreamsPlayer record, pinned-assets block (6 rows, 4 default / 2 frozen), invariants block (2 checked / 6 by-construction / 3 unprotected), and the two misreadable findings. All Verification predicates PASS with exact counts.

---

### Step 01.2 - Write the verdict table for the eleven invariants

**Files:** `docs/STREAM_CATALOG_CONSUMERS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a section holding one row per contract invariant named by the consumer, delimited by the literal HTML comments `<!-- invariants:begin -->` and `<!-- invariants:end -->`. Columns: `Invariant`, `Verdict`, `Address`. The `Verdict` cell holds exactly one of the literal tokens `checked`, `by-construction`, `unprotected`. The `Address` cell names the file, function or gate at which the verdict is re-checked a year from now.
>
> Take the eleven invariants and their verdicts from strategic §6.1, which already resolved them: two `checked` (`Invoke-PublishCatalog`'s zero-entry throw, and `Assert-AtlasBudget` called from `Build-FaviconAtlas`), six `by-construction`, three `unprotected`. Each invariant appears exactly once. Add one sentence recording that no gate under `scripts/quality/` reads the publishing script today, so `.\a.ps1 fg` catches a regression in none of the eleven.

**Why:**

Strategic §2 goal 2 requires each rule to carry an explicit verdict so the "unprotected" category stops being indistinguishable from "checked"; §11 criterion 3 requires every rule to appear exactly once with an address at which its verdict is re-checked. Strategic §7 names the risk that the table drifts from the code, and the address column is the stated mitigation.

**Verification:**

- `Grep` - `<!-- invariants:begin -->` and `<!-- invariants:end -->` each match exactly once.
- `Grep -c` - rows containing `| checked |` count 2.
- `Grep -c` - rows containing `| by-construction |` count 6.
- `Grep -c` - rows containing `| unprotected |` count 3.
- `Grep` - `Assert-AtlasBudget` and `Invoke-PublishCatalog` both present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Wrote docs/STREAM_CATALOG_CONSUMERS.md: StreamsPlayer record, pinned-assets block (6 rows, 4 default / 2 frozen), invariants block (2 checked / 6 by-construction / 3 unprotected), and the two misreadable findings. All Verification predicates PASS with exact counts.

---

### Step 01.3 - Record the two findings the document must not get wrong

**Files:** `docs/STREAM_CATALOG_CONSUMERS.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a short "Findings that read wrong from the outside" section with two entries.
>
> First: an empty `access` column in a catalog snapshot is the outcome of a run, not a missing producer. `Invoke-SignalProbe` assigns `geo` on HTTP 403 and 451, and `Invoke-CatalogMaintenance` moves it into the column on a run with `-DeepSignal` and without `-Limit`.
>
> Second: `artwork-manifest.json` carries sha256 and a stamp and would serve as an invalidation handle, but no consumer reads it yet, so it is an extension point and not a declared part of the contract. Name `S1835` as the ticket holding that decision.

**Why:**

Strategic §6.2 states the consumer's conclusion that the `access` producer disappeared is wrong and must be recorded as such, because otherwise the next investigation again mistakes a run's outcome for the state of the code. Strategic §6.3 is Open and carried by S1835, and §2 Non-goals exclude deciding it here, so the document must record it as undeclared rather than claim either answer.

**Verification:**

- `Grep` - `Invoke-SignalProbe` present.
- `Grep` - `Invoke-CatalogMaintenance` present.
- `Grep` - `artwork-manifest.json` present.
- `Grep` - `S1835` present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Wrote docs/STREAM_CATALOG_CONSUMERS.md: StreamsPlayer record, pinned-assets block (6 rows, 4 default / 2 frozen), invariants block (2 checked / 6 by-construction / 3 unprotected), and the two misreadable findings. All Verification predicates PASS with exact counts.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] No build - strategic §3.3 sets validation to the new check plus `document_registry/validate.ps1`; no Kotlin, resource or build file is touched.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Document is English only, per strategic §3.2.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1` (or deferred to Phase 04's batch).

---

## Handoff Notes to Next Phase

`docs/STREAM_CATALOG_CONSUMERS.md` exists and holds exactly one `pinned-assets` block. Phase 02's check parses that block and must fail loudly if the markers are absent, rather than treating an unparseable registry as an empty pin list.

---

## Rollback Plan

Delete `docs/STREAM_CATALOG_CONSUMERS.md`. No data migration or user-facing surface changed.
