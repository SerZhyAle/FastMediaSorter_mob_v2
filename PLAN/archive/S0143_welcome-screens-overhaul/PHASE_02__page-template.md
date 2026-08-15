# Phase 02 — Unified "Compact Header + Scrollable Details" Template

**Strategic spec:** [`../S0143_welcome-screens-overhaul.md`](../S0143_welcome-screens-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Introduce an optional `detailDescriptionRes` on the page model, restructure the standard welcome page (portrait + landscape) into a fixed header plus a scrollable details area, wire the binding, and add the trilingual detail-copy string resources for every content page (consumed here and in Phases 03–04).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `docs/COMMUNICATION_POLICY.md` re-read (§2 message formula, §6 tone checklist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/page_welcome.xml` | Modified | ≤ 110 |
| `app_v2/src/main/res/layout-land/page_welcome.xml` | Modified | ≤ 110 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

---

## Steps

### Step 02.1 — Add `detailDescriptionRes` to the page model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt`

**Depends on:** — start of phase

**Prompt for developer:**

> Add an optional field `detailDescriptionRes: Int = 0` to the `WelcomePage` data class (a string resource id for the scrollable "details" block; `0` means no details). Do not change any binding behaviour in this step.

**Verification:**

- `Grep -n "detailDescriptionRes"` in `WelcomePagerAdapter.kt` — at least one hit (the property declaration).

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 1/1 PASS (detailDescriptionRes added to WelcomePage). File: WelcomePagerAdapter.kt. Dev log recorded.

---

### Step 02.2 — Restructure `page_welcome.xml` (portrait + landscape)

**Files:** `app_v2/src/main/res/layout/page_welcome.xml`, `app_v2/src/main/res/layout-land/page_welcome.xml`

**Depends on:** Step 02.1

**Prompt for developer:**

> Rebuild both variants as: a fixed header region (`ivIcon` + `tvTitle` + short `tvDescription`) pinned to the top of the page area, and below it a `ScrollView` (height `0dp`, fills the remaining space) containing a single `TextView` `@+id/tvDetails` for the multi-paragraph detail copy. Keep ids `ivIcon`, `tvTitle`, `tvDescription`; add `tvDetails`. Content column stays capped at `@dimen/welcome_content_max_width` and centred. In landscape, keep the existing two-column idea (icon left, header text + details scroll on the right) — but the details `ScrollView` is the right column's lower region. The page itself never scrolls — only the details `ScrollView` does.

**Verification:**

- `Grep -n "@+id/tvDetails"` in `layout/page_welcome.xml` — exactly once.
- `Grep -n "@+id/tvDetails"` in `layout-land/page_welcome.xml` — exactly once.
- `Grep -n "ScrollView"` in `layout/page_welcome.xml` — at least once; same in `layout-land/page_welcome.xml`.
- `Grep -n "@+id/ivIcon"`, `"@+id/tvTitle"`, `"@+id/tvDescription"` — each exactly once in each file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (tvDetails 1, ScrollView present, ivIcon/tvTitle/tvDescription 1 in both portrait + landscape). Files: layout/page_welcome.xml, layout-land/page_welcome.xml (both rewritten). Dev log recorded.

---

### Step 02.3 — Bind details in view holders + set page detail ids

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

**Depends on:** Step 02.2

**Prompt for developer:**

> In `WelcomeViewHolder.bind()` (and `EnhancedViewHolder.bind()` where a `tvDetails` exists in its layout), set `tvDetails` text from `page.detailDescriptionRes` when it is non-zero and make the view `VISIBLE`; otherwise set it `GONE`. Reuse the existing staggered `animateEntrance` pattern for the details view. In `WelcomeActivity.setupViewPager()`, pass `detailDescriptionRes = R.string.welcome_description_2_details` for the Resource Types page and `detailDescriptionRes = R.string.welcome_description_4_details` for the Resources & Destinations page. (Pages 1, 3, 5 get their detail ids wired in Phases 03/04 where their layouts are rebuilt.) If `WelcomeActivity.kt` would exceed 500 lines, back it up to `temp/` first. Add `Timber.d("S0143: welcome page details bound")` once in `WelcomePagerAdapter.onBindViewHolder()`.

**Verification:**

- `Grep -n "detailDescriptionRes"` in `WelcomePagerAdapter.kt` — at least two hits (declaration + usage).
- `Grep -n "welcome_description_2_details"` in `WelcomeActivity.kt` — at least one hit.
- `Grep -n "welcome_description_4_details"` in `WelcomeActivity.kt` — at least one hit.
- `Grep -n "Timber.d(\"S0143:"` in `WelcomePagerAdapter.kt` — at least one hit.
- `Grep -n "Log\.d\("` in `WelcomePagerAdapter.kt` and `WelcomeActivity.kt` — zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (adapter detailDescriptionRes 4, Timber S0143 1, Log.d 0; activity welcome_description_2/4_details 1 each, Log.d 0). bindDetails() helper added; WelcomeViewHolder binds tvDetails; pages 2 & 4 wired. Files: WelcomePagerAdapter.kt, WelcomeActivity.kt. Dev log recorded.

---

### Step 02.4 — Trilingual detail-copy strings for content pages

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 02.1

**Prompt for developer:**

> Add five new string keys — `welcome_description_1_details`, `welcome_description_2_details`, `welcome_description_3_details`, `welcome_description_4_details`, `welcome_description_5_details` — to all three locale files. Each is a multi-paragraph "details" block expanding the corresponding page's short summary (welcome overview; resource types; touch zones; resources & destinations; powerful extras). Author the copy per `docs/COMMUNICATION_POLICY.md` §2 (informative formula) and run it through the §6 tone checklist before committing. Author style: `..` not `...`, always `ё`/`Ё` in the Russian copy. Do not place HTML markup in these strings unless rendered via `HtmlCompat`.

**Verification:**

- `Grep -n "welcome_description_1_details"` in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` — one hit in each.
- `Grep -n "welcome_description_5_details"` in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` — one hit in each.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_description_"` — exit code 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (check_strings_localized welcome_description_ → 10/10 EN/RU/UK, exit 0). Added welcome_description_1..5_details to values/, values-ru/, values-uk/. UK apostrophes escaped as \'. Dev log recorded.

---

### Step 02.5 — Wire page 1 detail id (Welcome page)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

**Depends on:** Step 02.4

**Prompt for developer:**

> Set `detailDescriptionRes = R.string.welcome_description_1_details` on the first (Welcome) page entry. The enhanced layout already gets its `tvDetails` in Phase 04; this just makes the data ready. If the file would exceed 500 lines, back it up to `temp/` first.

**Verification:**

- `Grep -n "welcome_description_1_details"` in `WelcomeActivity.kt` — at least one hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 1/1 PASS (welcome_description_1_details on Welcome page; file 484 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` → BUILD SUCCESSFUL (50s).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_description_"` exits 0.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `WelcomePage` gained `detailDescriptionRes` — catalog regen deferred to Phase 05.

---

## Handoff Notes to Next Phase

- `WelcomePage.detailDescriptionRes` is the single channel for the scrollable details block; pages 3 and 5 wire their ids when their layouts are rebuilt (Phases 03, 04).
- `tvDetails` is the conventional id for the details `TextView` in every page layout — keep it consistent in the touch-zones and enhanced layouts.

---

## Rollback Plan

Revert phase commit(s) — additive data field, layout restructure, and new string keys; no persisted state touched.
