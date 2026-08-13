# Phase 01 - Info model and strings

**Strategic spec:** [`../S1474_stream-about-channel.md`](../S1474_stream-about-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Introduce the platform-free description of an "about channel" readout - the property rows, their three groups, the measured-values holder - plus the formatter that turns a `StreamSourceEntity` and stored codes into labelled rows, and every string the window needs. No dialog, no ExoPlayer, no menu.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - all four are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamInfoModels.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamPropertiesFormatter.kt` | New | ≤ 220 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 60 added |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 60 added |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 60 added |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamPropertiesFormatterTest.kt` | New | ≤ 200 |

---

## Steps

### Step 01.1 - Add the readout model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamInfoModels.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the data model for one "about channel" readout: a property row carrying a label string resource and a display value that is either a filled string, an explicit "empty" (the channel has no such value stored), or an explicit "unavailable" (the engine did not supply it); a group carrying a title string resource and its rows; and a measured-values holder with the fields from strategic §2 goal 4 - video mime, picture width/height, frame rate, declared video bitrate, audio mime, channel count, sample rate, declared audio bitrate, observed connection bitrate. Every measured field is nullable and nullable means unavailable. Add a third readout state for a channel absent from the catalog, so the window can render the stored group as "this channel is not in the list" without inventing rows. Model only - no Android framework imports beyond `androidx.annotation.StringRes`, no coroutines.

**Why:**

Strategic §3.2 forbids reporting anything as measured that was not measured, and ADR-5 requires the measured group to exist even when it holds nothing; both are only expressible if "empty", "unavailable" and "absent from the catalog" are distinct states in the model rather than three different empty strings decided later in the view.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamInfoModels.kt` exists.
- `Grep` - `class StreamInfoProperty|data class StreamInfoProperty` matches once.
- `Grep` - `StreamInfoGroup` present.
- `Grep` - `class StreamMeasuredFormats|data class StreamMeasuredFormats` matches once.
- `Grep` - `import android\.(?!.*annotation)` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 5\5 PASS. `StreamInfoModels.kt` created (72 LOC, budget 120): `StreamInfoValue` as a sealed interface with the three distinct states `Text` / `Empty` / `Unavailable`, `StreamInfoProperty`, `StreamInfoGroup`, `StreamMeasuredFormats` with all ten fields nullable, and `StreamInfoReadout` carrying `NotInCatalog` as its own case. The file's only framework import is `androidx.annotation.StringRes`. Dev log recorded with step 01.3.

---

### Step 01.2 - Add the label and value strings in EN/RU/UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add every string the window needs under the `stream_info_` prefix, using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>` - one lockstep call per key, never three hand edits. Cover: the menu item title; the window title; the three group titles; one label per stored property of `StreamSourceEntity` (address, title, kind, origin, added, last played, last outcome, pinned, topic, category, language, country, access); one label per measured value; the words for the fixed-choice codes (kind AUDIO/VIDEO/RTSP, origin MANUAL/IMPORTED/CATALOG, outcome OK/FAIL/never tried, access geo/open); the "measuring" state; the "could not be measured" state; the "not in the list" state; the copy action; the "copied" confirmation. Reuse an existing key wherever the same notion is already named in the filters - do not introduce a second name for topic, language, country or category. Check each new user-visible string against `docs/COMMUNICATION_POLICY.md` §2 (message formula for its type) and §6 (tone checklist); the observed-rate label must say it describes this connection now, not the channel, per research artifact 02.

**Why:**

Strategic §3.2 makes EN/RU/UK parity mandatory and §7 names "two names for the same thing in one product" as a real risk, since several of these notions already have labels in the stream filters; the observed-rate wording is called out separately in §7 as the value most likely to be misread as a verdict on the channel.

**Verification:**

- `Grep` - each new key present in all three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "stream_info_"` - exit code 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. 44 keys added under `stream_info_`, each through one `-Action add` lockstep call (driver kept at `temp/S1474/add-strings.ps1`), 0 failures. `check_strings_localized.ps1 -KeyPrefix "stream_info_"` exit 0 - all 44 present in en/ru/uk; the ten best-effort locales are reported untranslated, which the audit states is not fatal.
- Reuse rather than a second name, per the prompt: topic, language, country and category take the existing filter labels `streams_filter_topic` / `_language` / `_country` / `_category`, and the pinned row takes the existing `yes` / `no`. Only the notions the filters do not name got a new key.
- §6 tone checklist: no exception text or error code is user-facing; no confirmation prompt is added; no "completed successfully" phrasing; the one empty state (`stream_info_not_in_catalog`) carries an invitation to act - "Add it to the list to keep its details"; parity confirmed by exit 0; no emoji. The observed-rate label says "Incoming rate right now" rather than naming the channel, which is the §7 risk research artifact 02 called out - it describes this connection, not the broadcast.

---

### Step 01.3 - Add the properties formatter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamPropertiesFormatter.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Create `StreamPropertiesFormatter` that turns a `StreamSourceEntity` into the two stored groups of strategic decision 1 - the channel itself and what the catalog says about it - and a `StreamMeasuredFormats` into the third. Render fixed-choice codes as words through the step 01.2 keys, timestamps through the platform date format, the address in full, and an unset stored field as the explicit "empty" state rather than a dropped row. Add a function that renders a whole readout as plain text for the clipboard, labels and values, one row per line. Take a string resolver as a collaborator instead of holding a `Context`, so the class stays unit-testable.

**Why:**

Strategic §11 criteria 3, 4 and 9 demand words instead of stored tokens, unset properties shown rather than omitted, and the entire list copyable as text; putting all three in one formatter keeps the dialog free of business logic as CLAUDE.md requires of UI classes.

**Verification:**

- `Glob` - `.../helpers/StreamPropertiesFormatter.kt` exists.
- `Grep` - `class StreamPropertiesFormatter` matches once.
- `Grep` - `fun .*[Cc]lipboard|fun .*asText|fun .*toPlainText` present.
- `Grep` - `Context` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `StreamPropertiesFormatter.kt` created (170 LOC, budget 220): three group builders, `readout()`, `asPlainText()` for the clipboard, and the four code-to-word mappers, each falling back rather than throwing on an unknown code. The collaborator is a new `StreamInfoResources` interface in the same file - strings, the platform date format, and the four value formats - so the class holds no framework type. `.\a.ps1 fc` exit 0.
- The `Context` predicate first scored 1: a KDoc sentence explaining that this class deliberately does not hold one. Reworded to "an Android context" in prose, so the type name appears nowhere and the grep is honestly zero rather than argued around.
- Reuse: `streams_filter_topic` / `_category` / `_language` / `_country` and `yes` / `no` are read straight from the existing keys, so no second name for those notions enters the product.

---

### Step 01.4 - Unit-test the formatter

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamPropertiesFormatterTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Cover with unit tests: every fixed-choice code maps to its word and an unknown code falls back without throwing; an entity with all optional fields null still produces every row, each in the "empty" state; a `StreamMeasuredFormats` with all fields null produces a measured group whose rows are all "unavailable" rather than an absent group; the clipboard text contains one line per row with both label and value.

**Why:**

Strategic ADR-5 and §11 criterion 3 turn on rows never disappearing, which is exactly the behaviour a later refactor silently breaks and no build gate catches.

**Verification:**

- `Glob` - the test file exists.
- Run `.\a.ps1 fu` and confirm `StreamPropertiesFormatterTest` reports zero failures.
- `Grep` - at least four `@Test` annotations in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `StreamPropertiesFormatterTest.kt` created with 6 `@Test` methods against a required 4. Run scoped to this class exit 0, and `TEST-..StreamPropertiesFormatterTest.xml` reads `tests="6" skipped="0" failures="0" errors="0"` - read directly, since a green exit alone does not prove the class ran. Covers all four required cases plus a positive measured value.
- The fake resolver returns each resource id as its own text, so an assertion names the key that was chosen rather than a translated phrase - the test then survives a wording change, which is exactly the kind of edit this test exists to outlive.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0 and `.\a.ps1 fk` exit 0 after the detekt fixes, 2026-08-08.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in all three `.kt` files.
- [x] Dev log entry added via `post-change.ps1` - one row naming all six files.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2587 records, `StreamPropertiesFormatter` present.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Phase-boundary audit (2026-08-08)

- Layer 1 - architecture and budgets. `StreamInfoModels.kt` 72 LOC of 120, `StreamPropertiesFormatter.kt` 176 of 220, the test 108 of 200, and 44 string keys against a 60-line budget per locale file. No business logic sits in a view: the formatter is a plain class taking a resolver, which is what keeps the Phase 03 dialog free of it.
- Layer 2 - lifecycle and coroutines. Nothing asynchronous exists yet; every function is a pure mapping.
- Layer 3 - listener and memory ownership. No listener, and deliberately no `Context` held anywhere, so nothing in this phase can outlive a screen.
- Layer 4 - Room. `StreamSourceEntity` is read, never written, and no schema, DAO or migration is touched.
- P2, fixed in this phase rather than deferred: the first closure failed `assert-detekt` on three over-length lines, two `ArgumentListWrapping` sites and a `ReturnCount` in `pictureSize`. Extracting `bitrate(Int?)` and rewriting `pictureSize` and `accessRes` cleared all of them; the unit run was repeated afterwards, `tests="6" failures="0"`, because the code under test had changed since the first green run.

---

## Handoff Notes to Next Phase

The measured-values holder is the contract Phase 02 fills and Phase 03 renders. Its nullability rule - null means unavailable, never zero and never a catalog fallback - is the invariant the rest of the ticket rests on.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no user-facing surface changed yet; the added string keys are unreferenced until Phase 03.
