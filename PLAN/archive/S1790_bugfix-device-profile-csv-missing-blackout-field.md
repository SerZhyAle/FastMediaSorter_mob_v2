# Спецификация (compact bugfix): S1790 - Поле launcherScreenBlackoutTimeoutSeconds отсутствует в CSV профилей устройств

**Ticket:** S1790
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-17
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-17

**Текст:**

Найдено при закрытии правки документации в ходе ревью S1786: на чистой базе гейт падал на любом строгом закрытии, хотя правка не была связана с профилями устройств.

```text
pwsh -NoProfile -File scripts/post-change.ps1 -File "docs/AGENT_COST_PLAYBOOK.md" ... -ChangeType Doc
  AppSettings fields MISSING from CSV rows (1): launcherScreenBlackoutTimeoutSeconds
check-device-profile-presets: FAIL - see the violations above.
Write-Error: assert-device-profile-matrix: FAIL - see the violations above.
  [device-profile-matrix-gate] FAIL (1937 ms) - child exit code 1
      repro: pwsh -NoProfile -File scripts/quality/assert-device-profile-matrix.ps1
```

Исследование текущего дерева показало, что незавершённая параллельная правка уже добавила строку `launcherScreenBlackoutTimeoutSeconds`, но оставила все 11 ячеек профилей пустыми. Поэтому текущий гейт покрытия проходит, однако профиль не задаёт поведение: пустая ячейка сохраняет кодовое значение `0` (`Off`).

Значение таймаута затемнения экрана для каждого профиля устройства - продуктовое решение, а не механическое устранение красного гейта.

---

## 1. Проблема / симптом

На чистой базе отсутствовала строка CSV для нового поля `AppSettings`, из-за чего строгий `assert-device-profile-matrix.ps1` падал. В текущем общем рабочем дереве строка существует, но все профили наследуют `0` и автоматическое затемнение не включается ни для одного из них.

---

## 2. Корневая причина

Поле и обработчик добавлены в незавершённой реализации S1741: `AppSettings` документирует `0 = Off`, а `DeviceProfilePresetApplier` принимает неотрицательное целое. CSV-строка была добавлена в общем незакоммиченном наборе правок, поэтому `git log -S` не может установить отдельный коммит-источник. Проверка покрытия защищает наличие строки и корректность парсинга, но намеренно не может вывести продуктовые значения профилей.

---

## 3. Исправление

После решения владельца задать значение для каждого из 11 профилей в существующей CSV-строке. Значение `0` означает явно отключённое затемнение; пустая ячейка означает отсутствие override и не заменяет решение для нового профиля. Не изменять модель, апplier, настройки или UI: они уже поддерживают поле.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** Existing launcher settings row remains the sole control; this ticket adds no screen, dialog, layout, or string.
- **Accessibility:** No interactive surface changes; existing touch, keyboard, mouse, and D-pad behavior remains unchanged.
- **Validation level:** Strict device-profile matrix gate plus a review of all 11 explicit profile values.
- **Owner sign-off:** Required: choose the timeout in seconds for personal smartphone, home tablet, TV/media box, car head unit, media player, photo frame, video player, audio player, e-book reader, VR headset, and Other.
- **Related tickets:** S1741, S1786.
- **Owner decision:** Use the balanced preset: personal smartphone `0`, home tablet `0`, TV/media box `300`, car head unit `60`, media player `300`, photo frame `0`, video player `300`, audio player `60`, e-book reader `300`, VR headset `60`, Other `0`.

---

## 4. Проверка

- The chosen 11 values are recorded in `device_profile_presets.csv`; `Other` is explicitly decided rather than left blank by accident.
- `pwsh -NoProfile -File scripts/quality/assert-device-profile-matrix.ps1` exits 0.
- `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0.

## 5. Блокирующий вопрос

Какой таймаут затемнения экрана в секундах должен получать каждый из 11 профилей устройств? Без этого выбора нельзя отличить намеренное `0` (Off) от отсутствующего override и нельзя безопасно менять общий CSV параллельной незавершённой правки.

### Quiz decisions (2026-08-18)

- Which profile timeout set should be used? → Balanced (A). Personal and general-purpose profiles stay Off; dedicated playback and reading profiles use 300 seconds; car, audio and VR profiles use 60 seconds.

---

## Last Audit

**Date:** 2026-08-18
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0
