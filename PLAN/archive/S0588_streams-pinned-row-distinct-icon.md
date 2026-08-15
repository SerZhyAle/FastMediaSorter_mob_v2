**Status:** Archived

# S0588 - Streams: pinned row needs a distinct icon

## Goal (RU)

Закреплённый стрим должен визуально отличаться: у закреплённой строки - заполненная иконка пина с акцентным цветом, у обычной - контурная иконка нейтрального цвета. Переключение закрепления меняет иконку сразу. Работает в светлой и тёмной теме (без хардкода цветов).

## 0. Raw capture

User report (RU, verbatim):
3. Когда я нажимаю "закрепить" - программа закрепляет стрим выше всех, но демонстрирует его с такой же иконкой (кнопкой), как и другие. Иконка должна быть иной, чтобы понимать что это закреплённый стрим.

## 1. Symptom

- Pinning moves the stream to the top (ordering via `StreamSourceEntity.pinned`) but `btnPin` always renders `ic_pin` with `?attr/colorControlNormal` - no toggled state.

## 2. Resolved decisions

- Distinct rendering: pinned -> filled `ic_pin` tinted `?attr/colorPrimary` (accent); unpinned -> new outlined `ic_pin_outline` tinted `?attr/colorControlNormal`. Shape + colour both differ, so the cue is clear even for colour-blind users; both tints are theme attrs (Rule 19).
- Scope: the pin button is the single clear cue; no extra leading-icon accent for now (simplest clear cue first).
- Applied in `StreamSourceAdapter.bind()` from `source.pinned`; `DiffUtil.areContentsTheSame` already compares the full entity, so a pin toggle rebinds the row and repaints immediately.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0565 (Streams screen)
- **UI placement:** existing `btnPin` in `item_stream_source.xml`; no layout move.
- **UI visibility/fallback:** icon+tint swap by pinned state; default row renders the outlined icon.
- **Input support:** unchanged - `btnPin` stays focusable/clickable.

## 3. Acceptance

- A pinned stream row shows a visually distinct pin icon/state from non-pinned rows.
- Toggling pin updates the icon immediately.
- Works in light and dark themes (no hardcoded hex).

## 4. Implementation phases

### Phase 01 - Outlined pin drawable

- Add `res/drawable/ic_pin_outline.xml` (Material push-pin outline, 24dp, `?attr/colorControlNormal` base tint).
- Verification: `a.ps1 fr` passes.

### Phase 02 - Pinned-state rendering in adapter

- In `StreamSourceAdapter.bind()`, set `btnPin` image to `ic_pin` (pinned) or `ic_pin_outline` (unpinned) and tint via `ImageViewCompat.setImageTintList` + `MaterialColors.getColor` (`colorPrimary` vs `colorControlNormal`).
- Set the default `btnPin` src in `item_stream_source.xml` to `ic_pin_outline` to avoid a first-bind flash.
- Verification: `a.ps1 fc` passes; pinned row shows the filled accent icon, others the outline.
