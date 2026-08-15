# Tactical Plan: S0214 — github-store-publication

**Strategic spec:** [`../S0214_github-store-publication.md`](../S0214_github-store-publication.md)
**Feature:** Publish FastMediaSorter to GitHub Store (OpenHub-Store/GitHub-Store)
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-06-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | tactical-decisions | — | ✅ Done | 5/5 | [PHASE_01__tactical-decisions.md](PHASE_01__tactical-decisions.md) |
| 02 | repo-metadata | 01 | ✅ Done | 4/4 | [PHASE_02__repo-metadata.md](PHASE_02__repo-metadata.md) |
| 03 | release-publish-script | 01 | ✅ Done | 6/6 | [PHASE_03__release-publish-script.md](PHASE_03__release-publish-script.md) |
| 04 | fingerprint-pinning | 03 | ✅ Done | 4/4 | [PHASE_04__fingerprint-pinning.md](PHASE_04__fingerprint-pinning.md) |
| 05 | readme-badge | 01 | ✅ Done | 4/4 | [PHASE_05__readme-badge.md](PHASE_05__readme-badge.md) |
| 06 | docs-catalog-cleanup | 02, 03, 04, 05 | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **External (acknowledged 2026-05-15):** GitHub Pages site at `https://serzhyale.github.io/FastMediaSorter_mob_v2/` — owner accepts that URL may resolve to placeholder/404 at the moment Phase 02 Step 02.3 writes it. Page content remains owner-owned and out of spec scope.
- [x] **External (acknowledged 2026-05-15):** GitHub credentials — owner accepts that Phase 02 Steps 02.3/02.4 (live API mutate) and Phase 03 Step 03.5 (real release upload) will be marked `⛔ Blocked` if `gh auth` / `$env:GITHUB_TOKEN` is not available at execution time. Script-authoring steps and dry-runs proceed without credentials.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — **skip** (strategic §8: «Без изменений»; задача про канал distribution, не про новую user-visible capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration — **skip** (no `.kt` files modified).
- [ ] `scripts/check_strings_localized.ps1` — **skip** (no `strings.xml` changes).
- [ ] `/skill-release` builds `standard_release` and `vr` release artifacts, publishes the standard AAB to Google Play, and publishes the GitHub Release assets in the same release window.
- [ ] At least one published GitHub Release at `https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases` contains both `FastMediaSorter-standard-<version>.apk` and `FastMediaSorter-vr-<version>.apk` assets.
- [ ] Search query `FastMediaSorter` inside GitHub Store mobile app surfaces the repository within 24h of release publication.
- [ ] `/spec-check S0214` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0214`.

---

## Blockers Log

- 2026-05-15 — Phase 02 blocked at Step 02.4: GitHub credentials (`gh auth login` or `$env:GITHUB_TOKEN`) not available in spec-dev session. Owner resolves manually by supplying credentials and running `pwsh -File scripts/release/apply-github-store-metadata.ps1` once.
- 2026-05-15 — Phase 06 partial (3/4) at Step 06.3: cannot mark "every row ✅" until Phase 02 unblocks.
- 2026-05-15 — Spec catalog status set to `BlockExternal` (S0214 In Progress → BlockExternal). Resume with `update.ps1 -Status "In Progress"` after credentials supplied.
- 2026-05-16 — Blocker cleared: active `gh auth` session available, `apply-github-store-metadata.ps1` completed successfully, and the Phase 02 / Phase 06 matrix advanced to full `6 / 6 done`.
- 2026-05-16 — First live release publication is still blocked operationally: `P:\ANDROID\FastMediaSorter_release` on `main` does not yet contain the S0214 `scripts/release/*` helpers, and its local `versionName` (`2.60.5160.429`) is ahead of the top `docs/WHATS_NEW.md` marker (`2.60.5160.425`). Merge/sync S0214 to `main`, align version + release notes, then run the release builders + publisher.
- 2026-06-04 — Owner decision: do not publish a standalone GitHub Store release now. GitHub Store publication becomes part of `/skill-release`, in the same release window as Google Play `standard_release`; owner performs the first GitHub Store client check after indexing. Spec catalog status moves from `BlockQuestions` to `BlockExternal`.

---

## Change Log

- 2026-05-15 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-15 — `/spec-dev` executed Phases 01, 03, 04, 05 to ✅ Done; Phase 02 to ⛔ Blocked at Step 02.4 (owner credentials); Phase 06 in progress, ready for handoff to `/spec-check`.
- 2026-05-16 — Phase 02.4 completed with live GitHub metadata apply; all six tactical phases are now ✅ Done. Remaining external closure sits only in the Completion Gate (publish first release + confirm GitHub Store indexing) before `/spec-check` can mark the ticket `Verified`.
- 2026-05-16 — Release-worktree audit showed that first live publish cannot proceed yet: `main` lacks the new S0214 release scripts, and the release-worktree version bump is ahead of `docs/WHATS_NEW.md`. Completion Gate remains open pending merge/sync + version/notes alignment.
- 2026-06-04 — Blocker questions resolved by owner. `/skill-release` now owns GitHub Store publication timing; S0214 remains externally blocked until the next release window and owner store verification.
