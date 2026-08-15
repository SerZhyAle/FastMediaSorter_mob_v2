# Phase 01 — Fallback Alignment

**Strategic spec:** [../S0182_sticky_session_user_agent.md](../S0182_sticky_session_user_agent.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Align the shared HTTP fallback `User-Agent` with the sticky/mobile model and remove stale `BlockNeedUserTest` diagnostics before resuming active implementation.

---

## Prerequisites

- [ ] All items in INDEX "Pre-Implementation Blockers" are checked.
- [ ] Working tree conflicts for S0182-specific files are ruled out.
- [ ] Timestamped backups exist for every touched Kotlin file above 500 lines.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadUserAgents.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` | Modified | ≤ 120 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 580 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split before continuing.

---

## Steps

### Step 01.1 — Introduce a shared mobile fallback User-Agent profile

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadUserAgents.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a shared mobile browser fallback profile in common link-download code and make the shared HTTP client use it instead of the stale desktop fallback. Preserve the existing rule that an explicit `User-Agent` header set by the caller must win.

**Verification:**

- `Grep` — `object LinkDownloadUserAgents` is present in `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadUserAgents.kt`.
- `Grep` — `LinkDownloadUserAgents.MOBILE_BROWSER_UA` is present in `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`.
- `Grep` — `request.header("User-Agent") != null` remains present in `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: data/link/LinkDownloadUserAgents.kt (+11 LOC), di/LinkDownloadModule.kt (desktop fallback replaced with shared mobile profile). Dev log recorded.

---

### Step 01.2 — Reuse the shared fallback in noLegal and clear stale debug-tag state

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Point the noLegal extractor fallback to the same shared mobile profile so both source sets use one default string. Because the ticket is leaving `BlockNeedUserTest` and re-entering active execution, remove the stale `Timber.d("S0182: ..")` debug tag from the coordinator while keeping non-debug informational logging intact.

**Verification:**

- `Grep` — `LinkDownloadUserAgents.MOBILE_BROWSER_UA` is present in `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`.
- `Grep` — `Timber.d\("S0182:` returns zero matches in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`.
- `Grep` — `MOBILE_BROWSER_UA =` returns zero matches in `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: noLegal/data/link/nolegal/YtDlpExtractionStrategy.kt (shared fallback import), domain/usecase/link/LinkAutoDownloadCoordinator.kt (stale `S0182` debug tag removed). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] The shared HTTP fallback no longer uses a desktop UA.
- [x] Shared and noLegal code consume the same fallback UA source.
- [x] Ticket is no longer in `BlockNeedUserTest` after this phase's reopen step.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Phase 02 should freeze host-aware `userAgentFor()` matching and coordinator UA forwarding so later refactors cannot silently regress the sticky-UA contract.