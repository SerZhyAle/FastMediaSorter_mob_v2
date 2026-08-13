# Phase 01 - URL source and strings

**Strategic spec:** [`../S0994_companion-publish-folders-help-link.md`](../S0994_companion-publish-folders-help-link.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-11
**Completed:** 2026-07-11 (compile proof: shared `fc` after Phase 03 - BUILD SUCCESSFUL)

---

## Objective

Introduce a single canonical source for the companion publish-folders URL and the trilingual label both UI entry points will reference; no UI wiring yet.

---

## Prerequisites

- [ ] Strategic spec is `Approved` or later.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 01.1 - Add the companion publish-folders URL as a single source

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `SupportIntentFactory`, add a private const holding `https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html` and a public accessor returning it (mirroring the existing `helpUrl` pattern). This is the sole definition of the URL; both UI entry points must call this accessor rather than hard-coding the address. The page is currently EN-only, so no locale switch - keep the accessor shape ready for a future locale variant.

**Verification:**

- `Grep` - `publish-folders-android.html` matches exactly once in `SupportIntentFactory.kt`.
- `Grep` - a public `fun companionPublishGuideUrl(` (or equivalent accessor) is present.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - Verification 2/2 PASS. `SupportIntentFactory.kt`: added `COMPANION_PUBLISH_GUIDE_URL` const + `companionPublishGuideUrl()` accessor. URL appears exactly once.

---

### Step 01.2 - Add the trilingual label string

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add one string key (e.g. `companion_publish_folders_guide`) via `scripts/utils/set-android-string.ps1 -Action add` (lockstep EN/RU/UK, parity-enforced) - it labels both UI entry points and doubles as their `contentDescription`. Suggested copy: EN "How to publish PC folders to Android", RU "Как опубликовать папки ПК для Android", UK "Як опублікувати теки ПК для Android". Copy must satisfy `docs/COMMUNICATION_POLICY.md` §2 (label formula) and §6 (tone checklist): no ellipsis (use `..`), plain hyphen, `ё` where grammatical.

**Verification:**

- `Grep` - key present in all three `strings.xml` files (three matches).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "companion_publish"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - Verification 3/3 PASS. Key `companion_publish_folders_guide` added EN/RU/UK via `set-android-string.ps1 -Action add`; `check_strings_localized.ps1` exit 0; Cyrillic bytes verified.

---

## Phase Done Criteria

- [ ] Both steps are `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the touched files (batched) via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The URL accessor and the shared label key exist. Phases 02 and 03 consume both; neither may re-declare the URL or a duplicate label.

---

## Rollback Plan

Revert the phase commit - no data migration or user-facing surface shipped yet (string keys unused until Phase 02/03).
