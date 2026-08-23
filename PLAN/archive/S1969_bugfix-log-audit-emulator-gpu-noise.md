# Спецификация (compact bugfix): S1969 - Аудит лога валит прогон на графическом шуме эмулятора

**Ticket:** S1969
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-22
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-22

**Захвачено во время:** релизной кампании v033 (`/release`, шаг 2 - `/spec-prerelease`)

**Текст:**

Pre-release log audit fails the sweep on emulator graphics-stack noise. On the v033 sweep (2026-08-22) the only actionable cluster was a single "E/FrameEvents: addRelease: Did not find frame." line, which flipped prerelease-verdict.ps1 to exit 1 despite Maestro 22/22 pass, 0 toasts, 0 crashes and 0 ANRs. Evidence: the v033 sweep capture, line 99599, extracted to `S1969_bugfix-log-audit-emulator-gpu-noise/evidence/frameevents_window.txt` - the line is bracketed by "D EGL_emulation: app_time_stats: avg=154.08ms max=532.75ms" one millisecond earlier and "E SurfaceFlinger: Out of order buffers detected" 375 ms later, all during a WindowManagerShell CLOSE transition animation while Maestro drove the UI. FrameEvents appears nowhere in app_v2 or wear sources - it is native libgui, running in-process, so the audit's pid attribution correctly keeps it while its origin is the emulator's software renderer. Reference .claude/reference/spec-prerelease.md section 4.1 already classes "emulator GPU noise" as benign, but the allowlist does not carry this tag, so every sweep on a software-rendered emulator can fail on it. Care needed: do not blanket-silence the tag, because on a physical device a frame-release miss could be a real defect - the allowlist entry should be conditioned on the emulator/software-render context (adjacent EGL_emulation evidence) rather than on the tag alone.

---

## 1. Проблема / симптом

Прогон `/spec-prerelease` на эмуляторе получает машинный вердикт FAIL при полностью чистом прогоне.

Наблюдалось на v033 (2026-08-22, `emulator-5554`, API 35):

- `prerelease-verdict.ps1` -> exit 1, `log.actionableErrors = 1`.
- Все остальные ветки вердикта зелёные: Maestro 22/22, perf pass, `toastCount = 0`, `FATAL EXCEPTION` 0, `ANR in` 0.
- `prerelease-log-audit.ps1` -> exit 1, `attribution: "pid"`, `actionableCount: 1`, единственный кластер `E/FrameEvents: addRelease: Did not find frame.` (count 1 на 105 МБ лога).

Эвиденс - [`evidence/frameevents_window.txt`](S1969_bugfix-log-audit-emulator-gpu-noise/evidence/frameevents_window.txt), окно из захвата v033 вокруг строки 99599:

```
22:34:01.136 11774 11814 D EGL_emulation: app_time_stats: avg=154.08ms min=25.38ms max=532.75ms count=5
22:34:01.137 11774 11836 E FrameEvents: addRelease: Did not find frame.
22:34:01.512   453   453 E SurfaceFlinger: Out of order buffers detected for RequestedLayerState{com.sza.fastmediasorter.debug/...}
```

Всё это - внутри анимации закрытия окна (`WindowManagerShell ... t=CLOSE`), пока UI ведёт Maestro.

Тег `FrameEvents` не встречается ни в `app_v2/src`, ни в `wear/src` - это нативный `libgui`, исполняемый внутри процесса приложения. Поэтому pid-атрибуция аудита справедливо оставляет строку, хотя источник - программный рендерер эмулятора.

---

## 2. Корневая причина

Подтверждено на захвате v033 (105 МБ, прогон аудита 2026-08-23); вердиктонесущее окно сохранено в [`evidence/frameevents_window.txt`](S1969_bugfix-log-audit-emulator-gpu-noise/evidence/frameevents_window.txt):

- `prerelease-log-audit.ps1 -Json` -> exit 1, `actionableCount: 1`, единственный кластер `E FrameEvents: addRelease: Did not find frame.` (count 1), `toastCount: 0`, `appPidCount: 35`. Время прогона 13,8 с.
- Строку эмитит нативный `libgui` внутри процесса приложения (pid 11774 - один из 35, восстановленных из `Start proc`), поэтому pid-атрибуция S1859 её справедливо оставляет: по процессу она наша.
- В аудите `$benignPatterns` уже содержит `EGL_emulation` и `ro.sf.lcd_density`, но не `FrameEvents`; ни `$systemTagHint`, ни `$foreignTagPatterns` этот тег тоже не перечисляют. Кластер попадает в actionable, и exit 1 валит обязательный шаг 4.1.
- Тот же промах во втором скрипте, и именно он валит прогон: `prerelease-verdict.ps1` считает ошибки независимо от аудита, через `search-log.ps1 -Errors -AppOnly -Unique`. В формате threadtime `-AppOnly` фильтрует по pid приложения, значит та же строка попадает и в `allErrors`, а `$expectedFallbacks` её не гасит - отсюда `log.actionableErrors = 1` и `pass=false` при 22/22 Maestro, 0 тостов, 0 крэшей.

Чинить надо оба скрипта: аудит владеет шагом 4.1, вердикт владеет PASS/FAIL всего прогона.

---

## 3. Исправление

Условная (не потеговая) benign-запись в обоих скриптах, по образцу S1700-guard для thumbnail-цепочки.

Маркер контекста - присутствие `EGL_emulation` в самом захвате. Этот тег пишет только GLES-транслятор эмулятора: на физическом устройстве его в логе нет ни разу, поэтому одно его наличие доказывает программный рендеринг всего прогона. В логе v033 он встречается 1109 раз.

Оконная близость (± N мс от строки `FrameEvents`) сознательно не используется: она требует разбора времени в каждой строке 105-мегабайтного лога и ничего не добавляет к защите - на реальном устройстве маркера нет ни в одной строке, значит и в окне его не будет.

Ограничение из §0 соблюдено: без маркера программного рендеринга кластер `FrameEvents` остаётся actionable.

### Phase 01 - Guarded emulator GPU-noise suppression

#### Step 3.1 - Guard the audit's benign classification on software-render context

**Files:** `scripts/devtest/prerelease-log-audit.ps1`

**Prompt for developer:**

> Precompute `$softwareRenderedCapture` with one `Select-String -Pattern 'EGL_emulation' -List` pass over the log file, next to the existing `$thumbnailChainHandled` probe. Add `$guardedEmulatorGpuNoise = 'addRelease: Did not find frame'` and fold it into `$isBenign` only when `$softwareRenderedCapture` is true and the cluster's tag is `FrameEvents`. Leave `$benignPatterns` untouched.

**Why:**

Тег `FrameEvents` не встречается ни в `app_v2/src`, ни в `wear/src`, но на физическом устройстве промах освобождения кадра может быть настоящим дефектом (§0), поэтому запись обязана быть обусловлена контекстом программного рендеринга, а не одним именем тега.

**Verification:**

- `Grep` - `softwareRenderedCapture` присутствует в скрипте не менее двух раз (объявление и использование).
- `Grep` - `addRelease: Did not find frame` не добавлено внутрь строки `$benignPatterns`.

**Status:** `[x]` done

---

#### Step 3.2 - Mirror the same guard in the verdict aggregator

**Files:** `scripts/devtest/prerelease-verdict.ps1`

**Prompt for developer:**

> Add `$softwareRenderedCapture` by the same `Select-String -Pattern 'EGL_emulation' -List` probe used for `$thumbnailHandled`, add `$guardedEmulatorGpuFallbacks = 'addRelease: Did not find frame'`, and append it to `$expectedPattern` only when the marker is present. Expose the decision as `softwareRenderedCapture` in `$logBreakdown`, next to `thumbnailTimeoutHandled`.

**Why:**

Вердикт считает ошибки собственным проходом `search-log.ps1 -Errors -AppOnly -Unique` и владеет PASS/FAIL прогона, поэтому починка одного аудита оставит `pass=false` на той же строке (§2).

**Verification:**

- `Grep` - `guardedEmulatorGpuFallbacks` присутствует в скрипте.
- `Grep` - `softwareRenderedCapture` присутствует в строке `$logBreakdown`.

**Status:** `[x]` done

---

#### Step 3.3 - Cover both directions with hermetic fixtures

**Files:** `scripts/devtest/prerelease-log-audit.tests/Run-Tests.ps1`, `scripts/devtest/prerelease-log-audit.tests/fixtures/logcat_emulator_frameevents_sample.txt`, `scripts/devtest/prerelease-log-audit.tests/fixtures/logcat_device_frameevents_sample.txt`

**Prompt for developer:**

> Add two threadtime fixtures, each with a `Start proc` announcement and one `E FrameEvents: addRelease: Did not find frame.` line under the app pid. The first also carries `D EGL_emulation: app_time_stats` lines, the second carries none. Add cases asserting the first reports `actionableCount 0` with the cluster counted as benign, and the second keeps `FrameEvents` actionable at exit 1.

**Why:**

Ограничение §0 - не глушить тег целиком - проверяемо только парой фикстур: одна доказывает подавление на эмуляторе, вторая доказывает, что на устройстве без маркера строка остаётся actionable.

**Verification:**

- `pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.tests/Run-Tests.ps1` -> exit 0, `failed: 0`.

**Status:** `[x]` done

---

#### Step 3.4 - Record the conditional rule where the benign classes are documented

**Files:** `.claude/reference/spec-prerelease.md`

**Prompt for developer:**

> Extend section 4.1's conditional-suppression paragraph (the S1700 thumbnail chain) with the S1969 case: `FrameEvents: addRelease: Did not find frame` counts as benign in both the audit and the verdict only while the same capture carries `EGL_emulation`.

**Why:**

Раздел 4.1 уже относит "emulator GPU noise" к benign, и именно это расхождение между текстом и списком тегов дало ложный FAIL (§0); без записи следующий читатель снова не поймёт, почему тег гасится не всегда.

**Verification:**

- `Grep` - `S1969` присутствует в `.claude/reference/spec-prerelease.md`.

**Status:** `[x]` done

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1859 (та же подсистема - аудит лога, pid-атрибуция; закрыт Verified, не блокер), S1700 (образец условного, а не потегового подавления)
- **Sensitive scope:** нет. Изменения только в devtest-скриптах прогона, ни строки рантайма приложения, ни ресурсов, ни разрешений, ни данных пользователя.

---

## 4. Проверка

- Аудит на захвате v033: `actionableCount: 0`, `benignCount: 3`, `toastCount: 0`, exit 0 (было: 1 / 2 / 0 / exit 1).
- Хермет-набор `prerelease-log-audit.tests/Run-Tests.ps1`: exit 0, `failed: 0`, включая новую пару фикстур.
- Фикстура без `EGL_emulation`: `FrameEvents` остаётся в `actionable`, exit 1.
- Вердикт на том же логе: `log.actionableErrors` не считает строку `FrameEvents`, `softwareRenderedCapture: true` в разборе.

---

## Last Audit

**Date:** 2026-08-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 11 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Проверено:

- `prerelease-log-audit.ps1` - `softwareRenderedCapture` объявлен и используется (3 вхождения), `$guardedEmulatorGpuNoiseTag` / `$guardedEmulatorGpuNoise` заведены отдельно; строка `addRelease` внутрь `$benignPatterns` не попала (0 вхождений в блоке).
- `prerelease-verdict.ps1` - `$softwareRenderMarker`, `$guardedEmulatorGpuFallbacks`, условная сборка `$expectedPattern` и поле `softwareRenderedCapture` в `$logBreakdown`.
- Аудит на захвате v033: `actionableCount: 0`, `benignCount: 3`, `toastCount: 0`, `softwareRendered: true`, exit 0 - против exit 1 / `actionableCount: 1` до правки.
- `prerelease-log-audit.tests/Run-Tests.ps1` - `passed: 22 | failed: 0`, exit 0 (было 15 проверок, добавлено 7).
- Вердикт на фикстуре с `EGL_emulation` - `actionableErrors: 0`, `softwareRenderedCapture: true`, `pass: true`, exit 0.
- Вердикт на фикстуре без маркера - `actionableErrors: 1`, `softwareRenderedCapture: false`, `pass: false`, exit 1: ограничение §0 соблюдено.
- `.claude/reference/spec-prerelease.md` §4.1 - правило записано рядом с S1700-прецедентом.
- `post-change.ps1 -ScopeToFile -RegistryAck all` - `post-change: PASS`, одна строка dev-log.
- `check-open-items-carried.ps1` - PASS, открытых вопросов нет.
- `check-evidence-durable.ps1` - PASS, окно лога перенесено в `evidence/frameevents_window.txt`.
- `Timber.d("S1969:` в `.kt` - 0 вхождений, тикет не проходил через `BlockNeedUserTest`.
