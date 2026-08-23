# Phase 02 - Record the narrowed question

**Strategic spec:** [`../S1876_ocr-camera-photo-resolution-estimator.md`](../S1876_ocr-camera-photo-resolution-estimator.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - closing phase
**Steps done:** 3 / 3
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

The exchange document and the strategic spec both state which half of the camera estimator is now arithmetic and which half is still open, so the residual question survives this ticket's closure.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/OCR_OVERLAY_ACCURACY.md` | Modified | ≤ 40 added |
| `PLAN/S1876_ocr-camera-photo-resolution-estimator.md` | Modified | ≤ 20 added |

---

## Steps

### Step 02.1 - Amend the transfer verdict for the DPI rule

**Files:** `docs/OCR_OVERLAY_ACCURACY.md`
**Depends on:** - start of phase

**Prompt for developer:**

> The section 5 table row "DPI declared, floor 70, upscale below 120 DPI" carries the verdict "form only" with the reason that our estimator itself needs re-thinking. Keep the verdict and extend the reason: re-thinking produced scene arithmetic from EXIF subject distance and 35 mm-equivalent focal length for the photos that carry them, with the floor retained for the rest, delivered by S1876. Do not restate the formula - link to strategic §5.1.

**Why:**

That row is the recorded reason the neighbouring project's 11-inch page assumption was rejected, so leaving it unamended would make a future reader re-open a question this ticket answered for half the input.

**Scope note - this is not an exchange round.** The document is our side of a three-sided exchange whose other two participants live outside this repo, and a round arriving from either side is appended as a new numbered section rather than edited in place. This edit is neither: it revises our own transfer verdict's reason after our own implementation. It also introduces no new threshold - the tier-A rule is arithmetic and `FLOOR_DPI` is unchanged - so the exchange's "no threshold outside a dated report" rule is not engaged and no cross-repo write is owed.

**Verification:**

- `Grep` - `S1876` present in `docs/OCR_OVERLAY_ACCURACY.md`.
- `Grep` - the row still reads `form only`, since only the reason changed, not the verdict.
- `Grep` - no new numeric constant enters the document; `70` remains the only DPI figure in that row.

**Status:** `[x]` done - the verdict still reads `form only` and the reason now names S1876's arithmetic and S1716 as the carrier for the remaining half. The only figures in the row are the pre-existing `70` and `120` plus the `35 mm` that names the EXIF tag, so no threshold was introduced.

---

### Step 02.2 - Carry or resolve the residual research item

**Files:** `PLAN/S1876_ocr-camera-photo-resolution-estimator.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Strategic §6.1 stays Open, so before this ticket reaches `Implemented` it must name the ticket that now owns the question with a literal `Carrier: Sxxxx` token. Draft that carrier via `/spec-draft` if none exists, dedup-checking by symptom first with `scripts/spec_catalog/search.ps1`; otherwise reference the existing id.

**Why:**

CLAUDE.md section 4 refuses a transition into `Implemented` while a research item is neither `Resolved` nor carried, because a closed ticket otherwise leaves the queue and takes the question with it.

**Verification:**

- `Grep` - `Carrier: S` matches in the strategic spec's §6, or item 1's status reads `Resolved`.
- `pwsh -NoProfile -File scripts/spec_catalog/check-open-items-carried.ps1 -Id S1876` exits 0.

**Status:** `[x]` done - no new ticket was drafted. `scripts/spec_catalog/search.ps1` on single tokens found S1716 `ocr-accuracy-corpus-and-harness`, which already owns "which scenes are representative"; the tier-B sample is that same set, so §6.1 names `Carrier: S1716`. Gate output: `PASS S1876 - Research section: 1 item(s); every open one names a carrier.`

---

### Step 02.3 - Close the ticket mechanically

**Files:** none - tooling only
**Depends on:** Step 02.2

**Prompt for developer:**

> Run the closure facade over the whole changed set of this ticket with `-ScopeToFile`, then advance the catalog status. The ticket ships no user-visible capability, so record no `docs/ALL_FEATURES.jsonl` entry and change no `docs/FEATURES*.md`.

**Why:**

Strategic §8 states the change is invisible to the user, and the feature inventory is for shippable capability, so a record here would misreport a recognition-internal change as a user-facing one.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` and exits 0.
- `scripts/spec_catalog/select.ps1 -Id S1876 -Format json` shows the advanced status.

**Status:** `[x]` done - `post-change: PASS (Mixed, 40583 ms)`, exit 0, over the whole 8-file set with `-ScopeToFile`. The first run returned `PASS WITH ADVISORIES (1)` naming the registered document; the record was read, found to list one path and no siblings, and the run repeated with `-RegistryAck 'ocr-overlay-accuracy'`. The dev log kept exactly one row - the second run reported `SKIP duplicate`. Scoped detekt: 41 files carry new findings project-wide, none among the changed files.

**No device gate.** The ticket is not set `BlockNeedUserTest`. `EstimateOcrResolutionUseCase` still has no production call site - wiring it into the recogniser is S1715 Phase 02, which has not started - so there is no on-device flow that could observe this change, and a device pass would certify nothing. The unit tests are the whole of the available evidence.

---

## Phase Done Criteria

- [ ] All 3 steps `[x]`.
- [ ] `check-open-items-carried.ps1` exits 0 for S1876.
- [ ] Closure facade exits 0.
