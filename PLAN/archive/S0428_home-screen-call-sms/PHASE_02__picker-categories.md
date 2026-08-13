# Phase 02 - Four flat contact categories in the cell editor

**Strategic spec:** [`../S0428_home-screen-call-sms.md`](../S0428_home-screen-call-sms.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Replace the single "Contact" category and its action sub-picker with four flat first-level categories, hidden individually on a device that cannot perform the action.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/ContactActionAvailabilityProvider.kt` | New | ≤ 110 |
| `app_v2/src/main/res/drawable/ic_call.xml` | New | ≤ 15 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt` | Modified | ≤ 300 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 800 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt` | Modified | ≤ 170 |

> No `res/layout` file is edited in this phase - the picker reuses `dialog_searchable_option_picker.xml` unchanged, so Rule 11 landscape parity does not apply here.
>
> `LauncherHomeActivity.kt` is 776 LOC - over the Rule 5 backup line, so take a timestamped backup into `temp/S0428/` before editing, and keep this phase's delta to the routing `when` only.

---

## Steps

### Step 02.1 - Add the contact-action availability provider

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/ContactActionAvailabilityProvider.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Singleton class ContactActionAvailabilityProvider @Inject constructor(@ApplicationContext private val context: Context)` with `fun isAvailable(action: LauncherContactAction): Boolean`. `PROFILE` and `MESSAGE` return true unconditionally. `DIAL` requires `FEATURE_TELEPHONY_CALLING` on API 33+ (`FEATURE_TELEPHONY` below) plus `resolveActivityCompat` finding a handler for `ACTION_DIAL` on a `tel:` probe URI. `SMS` requires `FEATURE_TELEPHONY_MESSAGING` on API 33+ (`FEATURE_TELEPHONY` below) plus `resolveActivityCompat` on an `ACTION_SENDTO` `smsto:` probe. Use the existing `queryIntentActivitiesCompat`/`resolveActivityCompat` helpers from `util/PackageManagerCompat.kt`; a raw-int `PackageManager` overload is refused by the Rule 21 gate.

**Why:**

Strategic §2 goal 4 requires the cells to be absent rather than dead on a photo frame or media box, and §3 fixes the check as `hasSystemFeature` plus `resolveActivity` because the broad `FEATURE_TELEPHONY` alone reports true on a data-only radio.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class ContactActionAvailabilityProvider` matches exactly once.
- `Grep` - both `FEATURE_TELEPHONY_CALLING` and `FEATURE_TELEPHONY_MESSAGING` present.
- `Grep` - `resolveActivityCompat` present at least twice.
- `pwsh -NoProfile -File scripts/quality/assert-deprecated-pm-flags.ps1` exits 0.

**Status:** `[x]` done

---

### Step 02.2 - Add the call icon

**Files:** `app_v2/src/main/res/drawable/ic_call.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a vector drawable `ic_call.xml` for the Call category: 24dp viewport, `android:tint="?attr/colorOnSurface"` matching the sibling `ic_contact.xml`, handset path. No hardcoded hex fill - Rule 19 refuses one.

**Why:**

The existing icon set has no handset glyph, and each of the four categories carries a `LeadingVisual.IconRes` because every other row in that picker does; the other three reuse `ic_contact`, `ic_send_phone_chat` and `ic_send_chat`.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/ic_call.xml` exists.
- `Grep` - `="#` returns zero hits in that file.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 02.3 - Reword the category strings across three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Reuse the four `launcher_contact_action_*` keys as category labels; `launcher_contact_action_sms` already exists from Step 01.5. Reword `launcher_contact_action_profile` to "Contact card" / "Карточка контакта" / "Картка контакту" and `launcher_contact_action_message` to "Message in an app" / "Написать в мессенджер" / "Написати в месенджер", one `-Action set` call per key and locale with `-ExpectedOldValue`. Do not remove any key here - `launcher_edit_kind_contact` and `launcher_contact_action_title` are still referenced by code until Step 02.5, which deletes them together with their call sites. Check every reworded string against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist.

**Why:**

The four labels are now first-level menu rows rather than answers to "what should this shortcut do?", so "Open profile" and "Send a message" read as commands in the wrong register, and Rule 20 requires the two orphaned keys to go in the same change.

**Verification:**

- `Grep` - `Contact card`, `Карточка контакта` and `Картка контакту` each present in their locale's `strings.xml`.
- `Grep` - `Open profile` and `Send a message` return zero hits in `values/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_contact_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.4 - List the four categories in the content picker

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt`
**Depends on:** Step 02.1, Step 02.2, Step 02.3

**Prompt for developer:**

> Replace the `CATEGORY_CONTACT` constant and its single row with four: `CATEGORY_CONTACT_PROFILE` (`ic_contact`), `CATEGORY_CONTACT_DIAL` (`ic_call`), `CATEGORY_CONTACT_SMS` (`ic_send_phone_chat`), `CATEGORY_CONTACT_MESSAGE` (`ic_send_chat`), keeping them adjacent and in that order where the old row sat. Inject `ContactActionAvailabilityProvider` the way `LauncherGadgetRegistry` is injected, and drop a row from `categoryOptions()` when its action is unavailable. The `onPicked` mapping needs no change - these ids carry no prefix.

**Why:**

The owner ruled on 2026-08-06 that the editor lists four flat entries with no action sub-picker, because the sub-picker cost a tap in the most common case (strategic §3.3).

**Verification:**

- `Grep` - all four `CATEGORY_CONTACT_` constants present in that file.
- `Grep` - `CATEGORY_CONTACT = ` returns zero hits in that file.
- `Grep` - `contactActionAvailability` present in that file (it reads inside the `contactCategory` helper the four rows go through, not inline in `categoryOptions`).
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 02.5 - Route each category straight to its system picker

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Change `LauncherContactPickManager.start()` to `start(action: LauncherContactAction)`, which sets `pendingAction` and launches the system picker directly. Delete the action-asking picker with its `TAG_ACTION`, `KEY_ACTION` and `labelOf` members; keep the channel picker, the `ActivityNotFoundException` path and the per-action `unavailableMessage` untouched (its `SMS` branch already exists from Step 01.6). In the host, replace the one `CATEGORY_CONTACT` routing arm with four, each calling `contactPickManager.start` with its action. Then remove the two now-orphaned string keys in every locale with `set-android-string.ps1 -Action remove`: `launcher_contact_action_title` (the question this step deletes) and `launcher_edit_kind_contact` (the category Step 02.4 replaced).

**Why:**

With the action chosen in the first list, asking it again is the sub-picker the owner removed; the channel picker stays because choosing between several messengers is a different question the pick itself raises (strategic §3.3).

**Verification:**

- `Grep` - `fun start(action: LauncherContactAction)` present in the manager.
- `Grep` - `TAG_ACTION`, `KEY_ACTION` and `labelOf` return zero hits in the manager.
- `Grep` - all four `CATEGORY_CONTACT_` arms present in `LauncherHomeActivity.kt`.
- `Grep` - `launcher_contact_action_title` and `launcher_edit_kind_contact` return zero hits across `app_v2/src`.
- `Grep` - `Log\.d\(` returns zero hits in every file this phase modified.
- `.\a.ps1 fc` exits 0 - resources changed as well as code, so the compile-only rung is not enough.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0 (code and resources both changed).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `ContactActionAvailabilityProvider` is new.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [x] `CODE.LOCK` released by `post-change.ps1`.
- [x] UI placement decision recorded before shipping - strategic §3.3 carries the owner's ui-clarify ruling of 2026-08-06 for this exact list. Screenshot deferred to the end of Phase 03: an emulator is attached but carries no build of this work, and Phase 03 changes the same flow again, so one install proves both phases instead of two.

---

## Step Log

- 2026-08-06 - Steps 02.1-02.5 executed. Verification 5/5 PASS. `.\a.ps1 fr` exit 0 after the drawable, `.\a.ps1 fc` exit 0 after the code. Plan corrected before execution: Step 02.3 no longer removes the two orphaned string keys - the code referencing them survives until Step 02.5, so removing them earlier would have broken the build between two "done" steps.
- 2026-08-06 - First `post-change` run FAILED on the scoped detekt gate: `SpacingBetweenDeclarationsWithComments` at `LauncherCellContentPickerDialogFragment.kt:247` - a comment introducing the new constants with no blank line above it. Fixed, re-run: `post-change: PASS` (Mixed, scoped), dev log recorded, catalog regenerated.
- 2026-08-06 - Phase-boundary audit, Layers 1-3. No P0/P1. One P3 recorded and accepted: `ContactActionAvailabilityProvider.isAvailable` performs up to two `resolveActivity` binder calls on the main thread while the editor's first list is built. It runs once per dialog open, over at most two intents, and the sibling `OsShortcutCatalog.isResolvable` on the same list already does the same - moving it off the main thread would buy nothing measurable and would make the list asynchronous for no user-visible gain.

---

## Handoff Notes to Next Phase

Every contact action is reachable from the first editor list, and each one goes straight to a system picker. `LauncherContactPickManager.start(action)` is the single entry point Phase 03 wraps with the source choice.

---

## Rollback Plan

Revert the phase commit. Stored cells are unaffected: the categories are editor-side only and no persisted value changed.
