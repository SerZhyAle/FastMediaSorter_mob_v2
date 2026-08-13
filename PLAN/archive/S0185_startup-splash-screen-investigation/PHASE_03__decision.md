# Phase 03 — Decision

**Strategic spec:** [`../S0185_startup-splash-screen-investigation.md`](../S0185_startup-splash-screen-investigation.md)
**Tactical INDEX:** [`INDEX.md`](INDEX.md)
**Depends on:** Phase 02 (measurement) — ⛔ Blocked
**Status:** ⬜ Not started
**Steps:** 0 / 2

> **Scope:** owner decision on Tier selection, followed by child spec creation (or explicit "no action" conclusion). This phase produces no code changes — its deliverable is a decision committed to this file and reflected in the strategic spec §6.4 and §10.

---

## Goals

1. Review Phase 02 numbers (TTID baseline, CastContext contribution, baseline profile recommendation) and choose a Tier (1, 2, 3, combination, or no action).
2. If one or more Tiers are chosen, create child specs under the appropriate Tier scope, link them in strategic §10, and close S0185 as Verified.
3. If "no action" is chosen, document the rationale in strategic §10 and close S0185 as Verified.

---

## Decision inputs

From Phase 02:

- TTID baseline (Device A API 31+): **??? ms** — see Phase 02 Step 02.1 findings.
- TTID baseline (Device B API 26..30): **??? ms** — see Phase 02 Step 02.1 findings.
- CastContext contribution estimate: **??? ms** — see Phase 02 Step 02.2 findings.
- Baseline profile pipeline recommendation: **???** — see Phase 02 Step 02.3 findings.

Decision thresholds (from strategic §6.1):

- TTID < 500 ms → Tier 2 not urgent; Tier 1 sufficient if desired.
- TTID 500–1500 ms → Tier 2 worthwhile; Tier 1 alone is insufficient.
- TTID > 1500 ms → Tier 2 mandatory before Tier 1.

---

## Step 03.1 — Tier decision

**Procedure:** owner reviews Phase 02 findings and records the decision below.

**Decision options:**

- `Tier 1 only` — visual polish: adjust splash background and/or icon to minimise the splash-to-first-frame visual discontinuity. No startup performance work.
- `Tier 2 only` — startup performance: defer `CastContext.getSharedInstance` and/or `GmsAvailabilityChecker.check`; optionally add baseline profile pipeline.
- `Tier 1 + Tier 2` — both.
- `Tier 1 + Tier 2 + Tier 3` — full: performance + seamless handoff (requires Tier 2 already successful).
- `No action` — startup is already fast enough; no child specs needed.

**Output artefact:** `### Step 03.1 — Decision` below.

---

## Step 03.2 — Child spec creation (if Tier chosen)

**Procedure:**

If any Tier is chosen, create one or more child specs via `scripts/spec_catalog/insert.ps1`. One spec per Tier is the recommended granularity, unless the Tier scope is small enough to fit in a single implementation phase (in which case one spec suffices).

Minimum child spec set:

- **Tier 1 child spec** — `PLAN/Sxxxx_startup-splash-visual-polish.md` — cover: theme background colour alignment day/night, icon sizing or replacement, `windowDisablePreview` policy review on pre-31.
- **Tier 2 child spec** — `PLAN/Sxxxx_startup-cast-sdk-defer.md` — cover: defer `CastContext.getSharedInstance` behind `firstFrameSignal.await`; optional: add `GmsAvailabilityChecker` deferral if measurement shows it is material.
- **Tier 2 baseline profile sub-spec** — `PLAN/Sxxxx_startup-baseline-profile-pipeline.md` — cover: macrobenchmark module, CI generation task, commit `baseline-prof.txt`. Only if Phase 02 Step 02.3 recommends it.
- **Tier 3** — deferred until Tier 2 is complete; no spec created now.

After creation, add each new spec id to strategic §10.

If "no action" chosen: skip this step; record rationale in `### Step 03.2` below and in strategic §10.

**Output artefact:** `### Step 03.2 — Child specs` below.

---

## Phase Done Criteria

1. `### Step 03.1 — Decision` populated with a concrete Tier choice and rationale.
2. If Tier chosen: child spec(s) created and their ids listed in `### Step 03.2` and strategic §10.
3. If no action: rationale documented in both this file and strategic §10.
4. Strategic spec §6.4 updated to `Resolved` with the chosen Tier.
5. INDEX row flipped to ✅ Done; `Phases: 3/3 done`; `Status: Done`.
6. S0185 spec catalog status flipped to `Verified` via `/spec-check S0185`.

---

## Step Findings

### Step 03.1 — Decision

_(pending — requires Phase 02 numbers)_

**Chosen Tier:** ???

**Rationale:**

_(pending)_

---

### Step 03.2 — Child specs

_(pending — populated after Step 03.1)_

---

## Change Log

- 2026-05-16 — Phase 03 authored by `/spec-all` (claude-sonnet-4-6). Decision framework and child spec creation procedure drafted. Status: Not started (gated on Phase 02).
