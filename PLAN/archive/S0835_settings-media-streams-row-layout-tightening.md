# S0835 - Settings Media streams row layout tightening

**Status:** Archived
**Priority:** 50
**Tier:** 2 - Easy
**Created:** 2026-07-01
**Source:** User request 2026-07-01 (`/spec-draft`)

## Goal

Tighten the "Трансляции" group on Settings -> Media so the three selector rows and the two
maintenance buttons follow a predictable layout. Presentation-only: no setting, label, value, or
action changes.

## 0. Captured request (raw)

**Captured:** 2026-07-01

Настройки. портрет и ландшафт. Страница Media, группа "трансляции". Выбор пунктов "Порядок по
умолчанию", "Что показывать сначала", "Обновление списка каналов.." разместить в портрете каждый
в свою строку. Разместить у них значение и заголовок в одну строку (и ландшафт и портрет).
Ещё Кнопку "Очистить отметки обновления" разместить в одной строке с кнопкой "Трансляции".

**Attachments:** none.

## 1. Resolved open points

- "Трансляции" = the existing `btnStreams` button (`streams_title`).
- "Очистить отметки обновления" = the existing `btnClearPlayStatuses` button (actual label
  `settings_streams_clear_statuses` = "Очистить отметки воспроизведения").
- Portrait and landscape are separate files (`layout/` + `layout-land/fragment_settings_streams.xml`);
  both rows and buttons bind by id in `StreamsSettingsFragment` - no layout-order dependency, no
  Kotlin logic change.
- "Title + value on one line" = the canonical `SettingsDropdownRow` inline mode (`sdr_inline="true"`
  + `sdr_fieldMaxWidth`), same pattern as Language/Color-theme in `fragment_settings_general.xml`
  (S0567). No ellipsize; the field fills remaining width capped at `settings_dropdown_compact_width`.
- Landscape grouping: the raw request scopes "each on its own row" to portrait only ("в портрете"),
  while inline applies to both. So landscape keeps the sort+filter pair 2-up and gains inline; only
  catalog-refresh moves to its own row because the clear button leaves that row.

## 2. Fix

### Phase 1 - Portrait relayout (`res/layout/fragment_settings_streams.xml`)

1. `rowDefaultSort`, `rowDefaultMediaFilter`, `rowCatalogRefresh` each become a standalone
   full-width (`match_parent`) inline row (`sdr_inline="true"`, `sdr_fieldMaxWidth` compact),
   replacing the previous two 2-up horizontal groups.
2. `btnClearPlayStatuses` (left) and `btnStreams` (right) share one horizontal row, each
   `weight="1"`; the old empty `View` spacer is removed.
3. D-pad: `btnClearPlayStatuses` gets `nextFocusRight=@id/btnStreams`, `btnStreams` gets
   `nextFocusLeft=@id/btnClearPlayStatuses`; both point `nextFocusUp=@id/rowCatalogRefresh`.

### Phase 2 - Landscape relayout (`res/layout-land/fragment_settings_streams.xml`)

1. `rowDefaultSort` + `rowDefaultMediaFilter` stay 2-up but switch to inline
   (`sdr_inline="true"`, `sdr_fieldMaxWidth`).
2. `rowCatalogRefresh` becomes a standalone full-width inline row (the clear button left it).
3. Buttons row identical to portrait (clear left, Streams right, focus wiring).

### Phase 3 - Build gate

- `a.ps1 fc` (standard debug, code + resources). Verification: BUILD SUCCESSFUL in 32s.
  [x] PASS, 2026-07-02.

### Phase 4 - Device verification (deferred, device-gated)

1. Settings -> Media -> "Трансляции" (feature enabled). PORTRAIT: "Порядок по умолчанию", "Что
   показывать сначала", "Обновление списка каналов" each on its own row, title left + value right
   on one line; "Очистить отметки" and "Трансляции" on one row; every selector still changes its
   value and persists; clear + open-streams still work.
2. LANDSCAPE: sort + filter 2-up inline, catalog-refresh on its own inline row, both buttons on one
   row; nothing clipped; all selectors reachable and functional.
   - Verification: `/spec-test-device` / `/spec-sweep` when a device is online.

## 3. Owner inputs (Approval gate)

- **Related tickets:** S0827 (same header/button-relayout family), S0567 (introduced the canonical
  inline `SettingsDropdownRow` this reuses), S0659 (added these three selectors + clear button).
- **Flavor scope:** any flavor with the streams feature; no flavor-specific behavior.
- **Settings-manifest impact:** none - presentation-only relayout; no setting added, removed,
  renamed, or moved between sections (Rule 22 regen not required).

## 4. Notes

- Rule 11: `layout/` and `layout-land/` variants both edited in the same change.
- `StreamsSettingsFragment` visibility/focus bindings are id-based; the extra per-button visibility
  toggles remain valid because the ids are unchanged.
