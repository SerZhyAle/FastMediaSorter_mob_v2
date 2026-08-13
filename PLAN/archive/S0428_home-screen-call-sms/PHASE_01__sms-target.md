# Phase 01 - SMS target end to end

**Strategic spec:** [`../S0428_home-screen-call-sms.md`](../S0428_home-screen-call-sms.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 6 / 6
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Add `LauncherContactAction.SMS` and carry it through pick, storage and execution, so a stored SMS cell hands off to the system messaging app via `ACTION_SENDTO`. No editor or UI change yet.

---

## Prerequisites

- [ ] Strategic §4 items 1-4 are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `CODE.LOCK` acquired before the first source edit (`scripts/utils/enter-code-lock.ps1 -Reason "S0428 phase 01"`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherContactTarget.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/ContactSnapshotDataSource.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/PickContactShortcutUseCase.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 260 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt` | Modified | ≤ 180 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> Every file in this phase is under 300 LOC, so neither the Rule 5 backup line nor the Rule 2 split ceiling is in play.
>
> **Plan correction, 2026-08-06.** The last three rows and Steps 01.5-01.6 were added during execution: the `.\a.ps1 fk` run after Step 01.4 failed on three exhaustive `when` expressions over `LauncherContactAction` that the plan had not inventoried - two in `LauncherContactPickManager` and one in `ResolveLauncherCommandLabelUseCase`, a consumer the plan never listed at all. Adding an enum entry is not a leaf change, and the phase does not compile without closing all three.

---

## Steps

### Step 01.1 - Add the SMS action to the contact target model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherContactTarget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `SMS` as the fourth entry of `LauncherContactAction`, after `MESSAGE`, and give it a branch in `LauncherContactTarget.isUsable` that requires a non-empty `phoneNumber`, the same predicate `DIAL` uses. Append the entry, never reorder the existing three. Update the class KDoc: it currently states the app holds no contacts permission at all, which S1335 made untrue - say instead that the permission is declared and optional, and that the snapshot model still stands.

**Why:**

The stored cell format encodes the action by name (`LauncherCellCommand.Contact`), so appending an entry is the only change the persistence format needs and reordering would silently repoint every saved cell - the strategic §3.3 note that no migration is required depends on this being an append.

**Verification:**

- `Grep` - `SMS,` present in the `LauncherContactAction` enum body in that file.
- `Grep` - `LauncherContactAction.SMS ->` present in the `isUsable` `when`.
- `Grep` - `the app holds no contacts permission at all` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.2 - Open the phone-number picker for an SMS pick

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/ContactSnapshotDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `pickIntent`, route `SMS` to the same `ACTION_PICK` on `CommonDataKinds.Phone.CONTENT_URI` that `DIAL` uses. Give `readDialTarget` a second parameter `action: LauncherContactAction = LauncherContactAction.DIAL` and build the target with it instead of the hardcoded `DIAL`, so one read serves both number-based actions; rename it `readPhoneTarget` and update the KDoc to say it serves both.

**Why:**

Strategic §3 puts the number in the `tel:`/`smsto:` URI itself, so an SMS cell needs exactly the number row a DIAL cell needs, and the phone-number picker is what makes the user pin the number they meant rather than one the app guessed.

**Verification:**

- `Grep` - `LauncherContactAction.DIAL, LauncherContactAction.SMS ->` present in `pickIntent`.
- `Grep` - `fun readPhoneTarget(` present with an `action` parameter.
- `Grep` - `readDialTarget` returns zero hits in that file. (The last call site lives in `PickContactShortcutUseCase`, which Step 01.3 repoints - the project-wide predicate moved there, where it can actually hold.)

**Status:** `[x]` done - `action` is a required parameter, not the defaulted one the prompt named: both call sites pass it explicitly, so a default would be dead weight under Rule 20.

---

### Step 01.3 - Resolve an SMS pick into a ready target

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/PickContactShortcutUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add an `SMS` branch to the `invoke` `when` calling `contacts.readPhoneTarget(picked, LauncherContactAction.SMS).asOutcome()`, and point the existing `DIAL` branch at `readPhoneTarget` too. Update the class KDoc sentence that counts three actions.

**Why:**

`invoke` is an exhaustive `when` over the action enum, so Step 01.1 breaks compilation here until this branch exists; routing both number actions through one read keeps the "a dial target is complete after the pick" property the KDoc states.

**Verification:**

- `Grep` - `LauncherContactAction.SMS ->` present in that file.
- `Grep` - `three actions` returns zero hits in that file.
- `Grep` - `readDialTarget` returns zero hits across `app_v2/src`.
- `.\a.ps1 fk` exits 0.

**Status:** `[~]` in progress

---

### Step 01.4 - Hand an SMS cell to the system messaging app

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add an `SMS` branch to `contactIntent` returning `Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", target.phoneNumber, null))`. Declare the scheme next to the existing `TEL_SCHEME` constant rather than inline. Do not set `sms_body`: the cell pins a recipient, not a message. Leave the `resolveActivityCompat` guard in `launchContact` untouched - it already hides a dead cell on a device with no messaging app.

**Why:**

Strategic §3 fixes the Play-safe level at `ACTION_SENDTO` on `smsto:` with no `SEND_SMS`, and §8 records that passing the number through `EXTRA_PHONE_NUMBER` instead of the URI silently no-ops on some handlers.

**Verification:**

- `Grep` - `ACTION_SENDTO` present exactly once in that file.
- `Grep` - `SMS_SCHEME` present as a private const with value `smsto`.
- `Grep` - `sms_body` returns zero hits across `app_v2/src`.
- `Grep` - `Log\.d\(` returns zero hits in every file this phase modified.
- `.\a.ps1 fk` exits 0 (run once at the end of Step 01.6, which closes the compile errors this edit surfaced).

**Status:** `[x]` done - source edit complete; the compile predicate is satisfied by the Step 01.6 run, since Steps 01.5-01.6 exist precisely because this edit did not compile on its own.

---

### Step 01.5 - Add the strings the SMS action needs

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add two keys, each in one lockstep `scripts/utils/set-android-string.ps1 -Action add` call. `launcher_contact_action_sms`: "Send SMS" / «Отправить SMS» / «Надіслати SMS». `launcher_contact_a11y_sms`: "Send SMS: %1$s" / «Отправить SMS: %1$s» / «Надіслати SMS: %1$s», matching the "label: name" shape its three siblings use so no locale has to decline the name. Check both against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

`ResolveLauncherCommandLabelUseCase` gives every contact action its own spoken label, so an action with no string of its own cannot render an accessible cell - and strategic §3 makes D-pad and remote operation a requirement, which is what the spoken label serves.

**Verification:**

- `Grep` - both keys present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_contact_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.6 - Close every exhaustive `when` over the action enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> Add the `SMS` branch to all three `when` expressions the compiler named: `spokenLabelRes` maps it to `launcher_contact_a11y_sms`, `labelOf` to `launcher_contact_action_sms`, and `unavailableMessage` to the existing `launcher_contact_no_number` - an SMS pick fails for exactly the reason a call pick does, a contact with no number. Add no `else` branch anywhere: the exhaustive `when` is what made the compiler point at all three sites, and an `else` would silence the next action instead.

**Why:**

Strategic §3.3 keeps every contact action reachable and accessible, and a missing branch is a compile error rather than a defect only because these `when` expressions were written exhaustively - preserving that property is what makes the next action's cost visible.

**Verification:**

- `Grep` - `LauncherContactAction.SMS ->` present in both files.
- `Grep` - `else ->` returns zero hits inside the three `when` expressions.
- `Grep` - `Log\.d\(` returns zero hits in every file this phase modified.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0. The validation ladder's compile-only rung is the right one here (CLAUDE.md §12): this phase changes symbols and strings, and the fk run merged resources too, so a full `dq` would prove nothing extra.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - the public signature of `ContactSnapshotDataSource` changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`). Layer 1 only: no lifecycle, coroutine, listener or Room surface was touched. Layer discipline holds - the UI manager still reaches the data source through `PickContactShortcutUseCase`, and `readPhoneTarget` kept its `Dispatchers.IO` context.
- [x] `CODE.LOCK` released by `post-change.ps1`.

---

## Step Log

- 2026-08-06 - Steps 01.1-01.4 executed. Verification PASS except the compile predicate, which failed: `.\a.ps1 fk` exit 1 on three exhaustive `when` expressions over `LauncherContactAction` the plan had not inventoried (`LauncherContactPickManager.labelOf`, `.unavailableMessage`, `ResolveLauncherCommandLabelUseCase.spokenLabelRes` - the last in a file the plan never listed). Plan corrected in place rather than hard-stopped: Steps 01.5-01.6 and three `Files Touched` rows added.
- 2026-08-06 - Step 01.2 deviated from its prompt: `action` is a required parameter of `readPhoneTarget`, not the defaulted one the prompt named. Both call sites pass it explicitly, so the default would have been dead weight (Rule 20).
- 2026-08-06 - Steps 01.5-01.6 executed. Verification 6/6 PASS. `.\a.ps1 fk` exit 0. Files: `LauncherContactTarget.kt`, `ContactSnapshotDataSource.kt`, `PickContactShortcutUseCase.kt`, `ExecuteLauncherCommandUseCase.kt`, `ResolveLauncherCommandLabelUseCase.kt`, `LauncherContactPickManager.kt`, three `strings.xml`. `post-change: PASS` (Mixed, scoped), dev log recorded, catalog regenerated.

---

## Handoff Notes to Next Phase

`LauncherContactAction` now has four entries and every layer handles all four. Nothing reaches the SMS action from the UI yet - Phase 02 supplies the editor entry point. The stored format is unchanged, so a build from this phase reads and writes every S1176 cell as before.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed.
