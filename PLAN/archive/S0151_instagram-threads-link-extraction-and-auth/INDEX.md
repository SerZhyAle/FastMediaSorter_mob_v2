# Tactical Plan: S0151 — instagram-threads-link-extraction-and-auth

**Strategic spec:** [`../S0151_instagram-threads-link-extraction-and-auth.md`](../S0151_instagram-threads-link-extraction-and-auth.md)
**Feature:** Instagram / Threads link download — real media extraction and auth offer
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 70
**Status:** In Progress
**Phases:** 3 / 4 done
**Last updated:** 2026-05-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | threads-domain | — | ✅ Done | 1/1 | [PHASE_01__threads-domain.md](PHASE_01__threads-domain.md) |
| 02 | preview-only-signal | 01 | ✅ Done | 6/6 | [PHASE_02__preview-only-signal.md](PHASE_02__preview-only-signal.md) |
| 03 | social-auth-ux | 02 | ✅ Done | 4/4 | [PHASE_03__social-auth-ux.md](PHASE_03__social-auth-ux.md) |
| 04 | docs-catalog-cleanup | all | ⬜ Not started | 0/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [ ] **Research §6.1 (architecture)** — Validate on-device, using the `S0151-diag:` log line added in Step 02.6, whether `InvisibleWebViewExtractionStrategy` with injected known-host cookies surfaces real video URLs (reel) or carousel image URLs after dynamic DOM render, or falls back to OG preview only. Required before Phase 02 can be called correct at `BlockNeedUserTest`, and before §6.2 (b) partial-carousel-save can be specified. Phase 02 (steps 02.1–02.6) may be implemented speculatively — the research item validates the reactive UX outcome and the carousel-extraction approach, not the code structure of the preview-only signal itself. See strategic §6.1.

**Open research items resolved inline (no device test required):**
- §6.1 (diagnostics) — Resolved: Step 02.6 adds a permanent `S0151-diag:` log line on every strategy attempt for a known auth host — strategy id, candidate breakdown, whether a saved session was applied, outcome kind. The architectural choice (a/b/c) remains Open above and is re-checked against this log on-device.
- §6.2 — Preview on final failure: **partial carousel saved with "N of M" message**; nothing extracted → no save, "content unavailable" toast. **Not implemented in this tactical plan yet** — depends on extracting carousel elements, which is gated on the §6.1 architecture decision (Open above). A follow-up `/spec-update --tactical` adds the partial-save step once §6.1 architecture is resolved. Until then a fully empty result behaves as "content unavailable".
- §6.3 — Scope of "preview ≠ content" rule: **registry-wide, but per-entry-gated.** `KnownAuthResource` gets `previewOnlyMeansLogin: Boolean` (default false), set true only for video/reel-first hosts (Instagram, Threads `.net`/`.com`, TikTok, X). `isPreviewSensitiveHost(host) == matchHost(host)?.previewOnlyMeansLogin == true`. Image-first registry hosts (Pinterest, Flickr, DeviantArt, ArtStation, Tumblr, Reddit) keep behaving as before — the OG image stays the result there. See Phase 02 step 02.2.
- §6.4 — Resolved: **no "freshness" heuristic.** Always apply the latest stored session; on extraction failure offer re-login with an explanatory dialog text ("the saved sign-in didn't return content — it may have expired"). The "annoying re-prompt after just signing in" symptom is rooted in single-account-per-host modelling and is fully fixed by **S0155 (link-auth-multi-account)**. Until S0155 lands, S0151 only updates the dialog wording (Phase 03 step 03.4).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 04).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after Phase 02 public API changes.
- [ ] `/spec-check S0151` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0151`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-11 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-11 — `/spec-update --tactical --force-locked`: mirrored strategic §6.4 into Pre-Implementation Blockers; added Step 03.4 (skip re-login offer for a recently-used session) to Phase 03; Phase 03 step count 3 → 4. Override reason: spec was at journal status `Partial` (in-flight, not historical) and the owner approved propagating the new §6.4 nuance into the tactical plan.
- 2026-05-11 — `/spec-update --tactical --force-locked` (second pass): owner resolved research items §6.1–§6.4 (see strategic spec). Pre-Implementation Blockers reshaped — §6.4 dropped (no freshness heuristic), §6.2/§6.3 resolved inline, §6.1 split into architecture (Open) + diagnostics (Resolved). Step 03.4 rewritten: no longer about `isSessionRecentlyApplied` — now improves the re-auth dialog wording so it explains WHY the user is asked to sign in again. Phase 03 step count stays at 4. The root cause of the "annoying re-prompt" symptom is delegated to **S0155 (link-auth-multi-account)**.
- 2026-05-11 — `/spec-update --tactical --force-locked` (third pass, pre-dev alignment): brought the tactical plan in line with the resolved strategic spec before `/spec-dev`. Step 02.2 reworked from `isVideoFirstHost` (hardcoded Instagram/Threads set) to `isPreviewSensitiveHost` = `matchHost(host) != null` (§6.3 (b) — whole registry); callers in `HtmlPageExtractionStrategy` / `InvisibleWebViewExtractionStrategy` renamed accordingly. Added Step 02.6 — permanent `S0151-diag:` structured log line per strategy attempt for known auth hosts (§6.1 diagnostics). Phase 02 step count 5 → 6. Noted that §6.2 (b) partial-carousel-save stays out of this plan until §6.1 architecture is resolved on-device.
