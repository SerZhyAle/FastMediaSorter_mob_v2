# Phase 01 - Section resources (strings, chevrons, dimens)

**Strategic spec:** [`../S1141_streams-split-pinned-list.md`](../S1141_streams-split-pinned-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Add the trilingual section-header strings, the expand/collapse chevron vector drawables, and the section dimens that the two-section layout and its manager reference. No layout or Kotlin change yet.

---

## Prerequisites

- [ ] Working tree is available for edit (CODE.LOCK acquired by `/spec-dev`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +4 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +4 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +4 keys |
| `app_v2/src/main/res/drawable/ic_expand_more.xml` | New | ≤ 12 |
| `app_v2/src/main/res/drawable/ic_expand_less.xml` | New | ≤ 12 |
| `app_v2/src/main/res/values/dimens.xml` | Modified | +2 keys |

---

## Steps

### Step 01.1 - Add trilingual section strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add four string keys via one lockstep call each: `streams_section_pinned`, `streams_section_main`, `streams_section_expand`, `streams_section_collapse`. Use `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>`. Suggested copy - EN/RU/UK: pinned = "Pinned" / "Закреплённые" / "Закріплені"; main = "Channels" / "Каналы" / "Канали"; expand = "Expand section" / "Развернуть раздел" / "Розгорнути розділ"; collapse = "Collapse section" / "Свернуть раздел" / "Згорнути розділ". `streams_section_pinned` / `streams_section_main` are section-header labels; `streams_section_expand` / `streams_section_collapse` are the header toggle contentDescription strings. Check the copy against `docs/COMMUNICATION_POLICY.md` §2 (label/hint formula) and §6 (tone checklist) - use `..` not `...`, plain hyphen, Ё where grammatical.

**Verification:**

- `Grep` - `streams_section_pinned`, `streams_section_main`, `streams_section_expand`, `streams_section_collapse` each present in all three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_section_"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Verification 3/3 PASS. 4 keys added EN/RU/UK via set-android-string; check_strings_localized exit 0. COMMUNICATION_POLICY §6 OK (concise labels, Ё, plain hyphen).

---

### Step 01.2 - Add chevron drawables and section dimens

**Files:** `res/drawable/ic_expand_more.xml`, `res/drawable/ic_expand_less.xml`, `res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create two 24dp Material chevron vector drawables: `ic_expand_less` (chevron pointing up - shown when a section is expanded, tap to collapse) and `ic_expand_more` (chevron pointing down - shown when collapsed, tap to expand). Use `android:fillColor="@android:color/white"` on the path so the tint is applied at the usage site via `app:tint` (no hardcoded theme color; matches the existing `ic_arrow_upward` pattern). Add two dimens to `res/values/dimens.xml`: `streams_section_header_height` (48dp - a full touch target) and `streams_section_header_padding` (12dp).

**Verification:**

- `Glob` - `res/drawable/ic_expand_more.xml` and `res/drawable/ic_expand_less.xml` both exist.
- `Grep` - `streams_section_header_height` and `streams_section_header_padding` present in `res/values/dimens.xml`.
- `Grep -n "#"` in the two new drawables returns no hardcoded hex in a `fillColor` other than `@android:color/white` (usage-site tint contract, Rule 19).

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Verification 3/3 PASS. ic_expand_less/ic_expand_more created (fillColor=@color/white, usage-site tint); streams_section_header_height/padding dimens added.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles resources - run `/build` (`.\a.ps1 fr` acceptable for a resource-only phase).
- [ ] Dev log entry added for the touched resource files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 02 references `@string/streams_section_pinned`, `@string/streams_section_main`, `@string/streams_section_expand`, `@drawable/ic_expand_less`, `@dimen/streams_section_header_height`, `@dimen/streams_section_header_padding`.

---

## Rollback Plan

Revert the phase commit - additive resources only, no behavior touched.
