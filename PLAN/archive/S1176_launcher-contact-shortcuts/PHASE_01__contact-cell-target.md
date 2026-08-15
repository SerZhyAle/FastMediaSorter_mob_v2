# Phase 01 - Contact cell target

**Strategic spec:** [`../S1176_launcher-contact-shortcuts.md`](../S1176_launcher-contact-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Result (2026-07-30)

**Encoding.** Percent-encoding per field, joined on `:`. `URLEncoder` escapes the colon, so the record can be split naively and every field survives verbatim - the alternative, spotting escaped separators while splitting, is the bug this avoids. Eleven tests pin it: a name containing the separator, a name of separators alone, non-latin text and emoji, a literal `+` (form-encoding maps space to `+`, so a phone number is the easy thing to corrupt), every action, and five malformed inputs that must decode to null rather than throw. `expected: round-trip exact, malformed -> null | actual: 11 tests, 0 failures, 0 errors` - PASS.

**Two things this phase touched that the plan's file list did not name**, both forced by the sealed type - which is the point of it being sealed:

- `ResolveLauncherCommandLabelUseCase` stopped compiling until the new case was handled. Its contact branch returns `iconRes = null` on purpose, exactly as the installed-app branch does: the picture belongs to the person and Phase 03's binder draws their photo or monogram. A generic glyph here would win and every contact would look alike.
- A number-only contact is a real record, so the label falls back to the phone number, and only then to a generic caption. It is never empty - the caption is the only thing telling two monograms apart.

**One icon trap avoided.** The first draft referenced `ic_phone`, `ic_message` and `ic_person`. None exists in this project, and inventing three would have been a design decision this ticket has no mandate for (Rule 10). Reading Phase 03 first showed the cell never wanted them.

---

## Objective

Add the contact target to the launcher cell command model and teach the executor its three outcomes, so a stored cell can open a profile, a dialler or a messenger channel.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherContactTarget.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` | Modified | n/a |

---

## Steps

### Step 01.1 - Model the contact snapshot and its actions

**Files:** `LauncherContactTarget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `LauncherContactTarget` holding the pin-time snapshot plus an action enum with `PROFILE`, `DIAL`, `MESSAGE`. The snapshot carries only what each action needs: the contact lookup key (profile), the phone number (dial), and for `MESSAGE` the picked data-row id together with the package that registered it. Include the display name for the cell label. KDoc must state the ADR-1 consequence plainly: this is a snapshot, so a later rename or number change is not picked up, and that is a deliberate trade for not requesting `READ_CONTACTS`.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `enum class` with `PROFILE`, `DIAL`, `MESSAGE` present.
- `Grep` - `READ_CONTACTS` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 01.2 - Add the command case and its codec

**Files:** `LauncherCellCommand.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `Contact` case to the sealed `LauncherCellCommand` with prefix `contact:`, alongside the existing `app:`, `fn:`, `res:`, `stream:`, `os:`, `op:`. Extend `encode()`/`decode()` to round-trip the whole snapshot in one string. The display name is user data and may contain the separator, so pick an encoding that survives it and document the choice - `res:` splits on a fixed field count and is not a safe model here. Decoding a malformed or truncated target must return null, exactly as the other cases do, never throw: a cell written by a newer build must not crash an older one.

**Verification:**

- `Grep` - `contact:` prefix constant present.
- `Grep` - `Contact(` appears in both `encode` and `decode`.
- Unit test - a target whose display name contains the separator round-trips unchanged.
- Unit test - `decode` returns null for a truncated `contact:` string.

**Status:** `[x]` done

---

### Step 01.3 - Execute the three outcomes

**Files:** `ExecuteLauncherCommandUseCase.kt`, `strings.xml` x3
**Depends on:** Step 01.2

**Prompt for developer:**

> Extend the executor's `when` with the new case: `PROFILE` opens the contact card by lookup key, `DIAL` opens the dialler with the number prefilled (never place the call - ADR-3, and `CALL_PHONE` stays undeclared), `MESSAGE` opens the stored data row through the package that registered it. Every branch resolves its receiver before starting it and returns false when nothing can handle it, so the caller can tell the user rather than doing nothing (strategic §11.7). Add the "nothing can open this" message across EN/RU/UK in one `set-android-string.ps1 -Action add` call and check the copy against `docs/COMMUNICATION_POLICY.md` §2 and §6. Log the failure at `Timber.i` with the action kind only - never the name, number or lookup key.

**Verification:**

- `Grep` - all three actions handled in the executor.
- `Grep` - `resolveActivityCompat` or the project's equivalent guard used before each start.
- `Grep` - no log line in this file interpolates a name, number or lookup key.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0.
- Strings pass the `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL, reaching `testStandardDebugUnitTest`.
- [x] Codec tests pass - `expected: 11 green | actual: 11 tests, 0 failures, 0 errors`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] No manifest gained `READ_CONTACTS` or `CALL_PHONE` - `grep` over every `AndroidManifest.xml` in `app_v2/src` returns nothing, which is the whole premise of the feature.
- [x] `check_strings_localized.ps1 -KeyPrefix "launcher_contact_"` exits 0, both new keys present in en/ru/uk.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Every contact branch resolves its receiver before starting it and returns false otherwise, so a dead cell reports rather than doing nothing; no log line carries a name, number or lookup key.

---

## Handoff Notes to Next Phase

The `contact:` encoding is the storage format from the moment Phase 02 pins one. Phase 02 must produce targets through this codec, never by string concatenation.

---

## Rollback Plan

Revert the phase commit - no cell carries a `contact:` target until Phase 02, and `decode` returning null for an unknown prefix already makes older builds tolerant.
