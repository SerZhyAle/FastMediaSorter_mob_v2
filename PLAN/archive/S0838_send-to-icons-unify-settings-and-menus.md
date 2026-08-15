# S0838 - Send-to receiver icons unified across Settings and menus

**Ticket:** S0838
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 3 - Moderate
**Source:** User request 2026-07-01 (`/spec-draft`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

Подсистема «Отправить в..» (`ShareTargetRegistry`) уже показывает иконки в рантайм-меню, но группа настроек Settings -> Player -> «Команды отправить файл в..» их не показывает: заголовок группы без значка, а строки-тогглеры строятся динамически без вызова `setIcon`. Задача: дать группе узнаваемый `share`-значок в заголовке и показать в каждой строке настроек ту же иконку получателя, что и в меню, чтобы пользователь узнавал одну и ту же операцию и понимал, где её настраивать. Новые ассеты не нужны - все иконки уже есть в реестре.

## 1. Confirmed scope (research 2026-07-01, via android-solution-researcher)

Canonical registry already exists: `core/share/ShareTargetRegistry` (Hilt `@IntoSet` multibinding, `core/share/di/ShareTargetModule.kt`), each `ShareTarget.iconRes` populated for all 10 receivers (`system_share`->`ic_share`, `open_in`, `print`, `email`, `keep_text`, `keep_drawing`, `lens`, `telegram`->`ic_send_plane`, `whatsapp`->`ic_send_chat`, `instagram`->`ic_send_camera`). All 10 drawables present. The messenger/social glyphs are deliberately neutral vectors, not brand logos (S0459 ADR-5 / `verifyNoPlatformNames`); at runtime `ShareTargetIconResolver.resolveIcon` prefers the installed app's launcher icon and falls back to the glyph.

Runtime menus already render icons correctly and need no change:

- Player command panel: `CommandPanelLayoutPlanner` `SEND_TO` already carries `ic_share`.
- Overflow submenu: `SendToMenuManager.buildOverflowSubMenu` sets the parent item `ic_share` (line 112) and each receiver `resolveIcon(target) ?: target.iconRes`.
- Bottom sheet: `SendToBottomSheet` binds per-receiver icons via the same resolver.
- Editor overflow (`EditorActionPanelBinder`) is a plain text-only `PopupMenu` (no item has an icon) - intentionally left untouched.

The only gaps are in Settings (`PlaybackSettingsFragment` + `fragment_settings_playback.xml`):

- Group header `headerSendCommands` has no `app:csh_icon` (sibling `headerBackgroundAudio` sets `ic_audio`).
- `setupSendCommandsGroup()` builds one `SettingsToggleRow` per target but never calls `SettingsToggleRow.setIcon(..)`, so rows are iconless.

Resolved open points: (1) settings rows mirror the menus exactly - `resolveIcon(target) ?: iconRes` - because the owner's goal is cross-surface recognizability; (2) header icon = `ic_share` (owner: "значёк типа share"), both orientations (Rule 11); (3) installed-app label already handled by S0463 (`resolveShareTargetLabel`), unchanged.

## 2. Phase 1 - Group header share icon (portrait + landscape)

In BOTH `layout/fragment_settings_playback.xml` and `layout-land/fragment_settings_playback.xml`, add `app:csh_icon="@drawable/ic_share"` to `headerSendCommands` (before `csh_showHelp`, matching the sibling `headerBackgroundAudio` ordering).

## 3. Phase 2 - Per-row receiver icons in Settings

In `PlaybackSettingsFragment`:

1. Field-inject `ShareTargetIconResolver` (already `@Singleton @Inject constructor` - no new scope).
2. In the row builder, immediately set each row's neutral glyph: `target.iconRes?.let { setIcon(it) }` (no PackageManager, no jank).
3. Extend the existing off-main-thread block (which already resolves installed-app labels, S0474) to also resolve `shareTargetIconResolver.resolveIcon(target)` and, on the main thread, upgrade the glyph to the installed receiver app's launcher icon (`null` keeps the glyph). This keeps every PackageManager lookup off the main thread and matches the menus' icon exactly.

**Verification:** `.\a.ps1 fc` (code + resources) passes; Hilt provides `ShareTargetIconResolver`; both layouts resolve `ic_share`; no receiver-set / gating / order / label change.

## 4. Open points

Resolved (see §1). No new brand assets required; no menu code changes; no string changes.

### 3.3 Owner inputs (Approval gate)

- **Sensitive scope:** UI (Settings + confirms icons already used in menus). Owner explicitly allows brand-representing icons; research confirmed only existing neutral-glyph assets are used, so no copyright action needed.
- **Related tickets:** S0459 / S0463 / S0478 (send-to registry + resolver + per-receiver menu icons), S0840 / S0841 (settings icon family).

## Related

- S0459 / S0463 / S0478 - `ShareTargetRegistry` / labels / per-receiver menu icons.
- S0840, S0841, S0836, S0838 - settings icon tuning/unification family.

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all, research by android-solution-researcher)
**Verdict:** Verified

- Phase 1: `headerSendCommands` gained `app:csh_icon="@drawable/ic_share"` in both `layout/` and `layout-land/fragment_settings_playback.xml` (Rule 11), placed before `csh_showHelp` matching sibling `headerBackgroundAudio`.
- Phase 2, `PlaybackSettingsFragment`: field-injected `ShareTargetIconResolver` (existing `@Singleton @Inject constructor`, no new scope); row builder now sets each receiver's neutral glyph immediately (`target.iconRes?.let { setIcon(it) }`); the existing S0474 off-main-thread label block was extended to also resolve `resolveIcon(target)` and upgrade the glyph to the installed app's launcher icon on the main thread (`null` keeps the glyph) - identical resolution to `SendToMenuManager` / `SendToBottomSheet`.
- Menus unchanged and already consistent: command-panel `SEND_TO` + overflow submenu parent both use `ic_share`; per-receiver icons already resolved in both menu surfaces; editor `PopupMenu` intentionally text-only (no item icons).
- No new assets, strings, receiver-set, gating, order, label, or dependency changes.
- Main-safety: PackageManager icon/label lookups run on `Dispatchers.IO`; only `setTitle`/`setIcon` run on the main thread. No listener added/removed. `ShareTargetIconResolver` already reachable from release (menus) - no new R8 keep surface.
- `a.ps1 fc` (compileStandardDebugKotlin + kapt/Hilt + process resources executed) -> BUILD SUCCESSFUL.
- No `ALL_FEATURES` record: visual consistency polish (icons on existing send-to settings), not a new shippable capability - the «Send to..» feature already shipped (S0459 family).
