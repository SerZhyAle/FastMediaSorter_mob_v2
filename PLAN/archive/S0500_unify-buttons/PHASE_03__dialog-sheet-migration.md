# Phase 03 - Dialog & sheet button migration

**Strategic spec:** [`../S0500_unify-buttons.md`](../S0500_unify-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-18
**Completed:** 2026-06-18

**Step Log:**

- 2026-06-18 - 03.1 borderless -> Button.Text in stream_offload_offer, bottom_sheet_permission_rationale, card_google_account. 03.2 plain `<Button>` -> MaterialButton + role family in all listed dialogs/sheets. 03.3 literals "Grant"/"Not now" repointed to existing keys @string/perm_action_grant + @string/auth_offer_dialog_skip (no new keys, no fabricated translations). 03.4 build PASS.
- 2026-06-18 - SCOPE EXTENSION (strategic §2 goal 2 / §11 criterion 2 "рассинхронизированные стили"): research under-counted the real "zoo" - the MC/M3 split lives mostly on MaterialButtons (108 `@style/Widget.MaterialComponents.Button.*` refs). Applied a repo-wide role-preserving remap (MC OutlinedButton->Button.Outlined, TextButton->Button.Text, Button/Button.Icon->Button.Filled) across 28 non-exempt layouts (incl. activity_add_resource, activity_welcome +sw480/sw720, dialog_folder_selection +land, dialog_scheduled_operation +land, player_crop_overlay +land, player_text_viewer +land, fragment_duplicates, item_scheduled_operation, item_permission_entry, dialog_webview_auth, dialog_folder_browser, dialog_capture_keybinding, activity_keybinding_remap, dialog_link_autodownload_progress). Exempt kept MC: camera (fork 1) + calculator (own taxonomy).

---

## Objective

Migrate plain `<Button>` widgets in dialogs and bottom sheets to `MaterialButton` + the `Widget.FastMediaSorter.Button.*` family by role, replace `borderlessButtonStyle` with `Button.Text` (strategic §6 fork 5), and extract any hardcoded button strings to EN/RU/UK.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_stream_offload_offer.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/bottom_sheet_permission_rationale.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/card_google_account.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/dialog_translation_settings.xml` + `layout-land/` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/dialog_network_discovery.xml` + `layout-land/` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/dialog_file_operation_progress.xml` + `layout-land/` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/dialog_file_copy_progress.xml` + `layout-land/` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/dialog_integration_test.xml` | Modified | ≤ 300 |
| `app_v2/src/main/res/values/strings.xml` + `values-ru/` + `values-uk/` | Modified | n/a |

> **Landscape parity (Rule 11):** `dialog_translation_settings`, `dialog_network_discovery`, `dialog_file_operation_progress`, `dialog_file_copy_progress` have `layout-land/` twins - edit both. `dialog_stream_offload_offer`, `bottom_sheet_permission_rationale`, `card_google_account`, `dialog_integration_test` have no `layout-land/` twin (portrait only) - correct.

---

## Steps

### Step 03.1 - Replace borderlessButtonStyle with Button.Text (fork 5)

**Files:** `dialog_stream_offload_offer.xml`, `bottom_sheet_permission_rationale.xml`, `card_google_account.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In these three files, replace `style="?android:attr/borderlessButtonStyle"` (plain `<Button>`) and `style="?attr/borderlessButtonStyle"` (already-`MaterialButton` in `card_google_account.xml`) with `style="@style/Widget.FastMediaSorter.Button.Text"`. Where the host element is still a plain `<Button>`, change the widget class to `com.google.android.material.button.MaterialButton` in the same edit. Keep id and text.

**Verification:**

- `Grep` - zero `borderlessButtonStyle` hits in the three files.
- `Grep` - `Widget.FastMediaSorter.Button.Text` referenced in each of the three files.

**Status:** `[x]` done

---

### Step 03.2 - Migrate remaining plain `<Button>` by role

**Files:** all dialog/sheet layouts in "Files Touched" (portrait + land)
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace each remaining plain `<Button>` with `com.google.android.material.button.MaterialButton` and assign a family style by its existing emphasis:
>
> - confirm / positive primary action -> `@style/Widget.FastMediaSorter.Button.Filled`.
> - secondary emphasis -> `@style/Widget.FastMediaSorter.Button.Tonal`.
> - secondary outline -> `@style/Widget.FastMediaSorter.Button.Outlined`.
> - cancel / dismiss / low-emphasis -> `@style/Widget.FastMediaSorter.Button.Text`.
>
> Preserve id, text, click bindings, and layout constraints. Do not alter button count or order. Match the role to the current visual weight (a previously filled-default button stays Filled; a previously borderless one becomes Text).

**Verification:**

- `Grep` (`-oE "<Button\b"`) - zero plain `<Button>` elements remain in any file in "Files Touched".
- `Grep` - every migrated button references a `@style/Widget.FastMediaSorter.Button.*` style.

**Status:** `[x]` done

---

### Step 03.3 - Extract hardcoded button strings to EN/RU/UK

**Files:** dialog/sheet layouts above, `values/strings.xml` (+ ru/uk)
**Depends on:** Step 03.2

**Prompt for developer:**

> Grep the touched layouts for literal `android:text="..."` on buttons (not `@string/`). Known cases: `bottom_sheet_permission_rationale.xml` ("Grant" / "Not now"), and any literal found in `dialog_stream_offload_offer.xml`. For each, add a key across the three locales in one lockstep call `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"`, then point the button `android:text` at `@string/<key>`. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist).

**Verification:**

- `Grep` - zero literal `android:text="[A-Za-z]` on any button in the touched layouts (all `@string/`).
- `Grep` - each new key present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<key_prefix>"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 03.4 - Compile gate

**Files:** (none - build only)
**Depends on:** Steps 03.1-03.3

**Prompt for developer:**

> Build standard debug.

**Verification:**

- `/build` -> `standard debug` PASS.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] No plain `<Button>` and no `borderlessButtonStyle` remain in the touched files.
- [ ] String locale audit passes for any new keys.
- [ ] Dev log entry added for the touched file batch (may defer to Phase 05 batch).

---

## Handoff Notes to Next Phase

- Dialogs and sheets now use the `Button.*` family. Phase 04 covers the remaining non-dialog surfaces (welcome, permissions fragment, list items, activity bars) and the `item_destination_button.xml` hex fix.

---

## Rollback Plan

Revert the phase commit(s). Layout + string-resource change only; no behaviour change. New string keys may stay (harmless) or be removed via `set-android-string.ps1 -Action remove`.
