# Phase 02 - Механический гейт

**Strategic spec:** [`../S1612_add-new-maestro-features.md`](../S1612_add-new-maestro-features.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Поставить проверку `assert-maestro-oracle.ps1`, отвергающую три запрета конвенции оракула в YAML-флоу обоих наборов, встроить её в пакет быстрых статических проверок и привести три нарушающих файла в соответствие.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - конвенция непротиворечива и существует в одном экземпляре.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-maestro-oracle.ps1` | New | ≤ 220 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 400 |
| `maestro/device_only/3d-video-sbs.yaml` | Modified | ≤ 100 |
| `maestro/device_only/3d-video-switching.yaml` | Modified | ≤ 140 |
| `docs/DEV_OPS.md` | Modified | ≤ 900 |

---

## Steps

### Step 02.1 - Написать проверку конвенции оракула

**Files:** `scripts/quality/assert-maestro-oracle.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Написать проверку, читающую `*.yaml` и `*.yml` из `maestro/` и `scripts/devtest/maestro/` и отвергающую три нарушения: `optional: true`, привязанный к `assertVisible` или `assertNotVisible`; регулярное выражение в значении `id:` или `text:` (признак - последовательность `.*`); `point:` в качестве селектора `tapOn`. Исключить из проверки `_shared/permissions.yaml` - конвенция разрешает необязательные утверждения на системных диалогах разрешений; исключение задать списком путей в шапке скрипта, а не признаком в имени файла. Флаг `-Quiet` для встраивания в пакет. Коды возврата: 0 - нарушений нет, 1 - нарушения найдены, 2 - не удалось проверить (каталог отсутствует, файл нечитаем). Каждое нарушение печатать одной строкой с путём, номером строки и названием нарушенного правила.

**Why:**

Ни один существующий гейт не читает YAML-флоу, поэтому нарушение конвенции сегодня ловится только ревью глазами, а цель 3 стратегической спеки требует механического обнаружения.

**Verification:**

- `Glob` - `scripts/quality/assert-maestro-oracle.ps1` существует.
- Запуск на текущем дереве до Step 02.3 - exit 1, в выводе названы `3d-video-sbs.yaml` и `3d-video-switching.yaml`.
- Запуск с `-Quiet` - вывод не содержит строк при exit 0.
- `Grep` - шапка скрипта перечисляет коды возврата 0, 1, 2 (CLAUDE.md Rule 7, S1070).

**Status:** `[x]` done

---

### Step 02.2 - Проверить контракт кодов возврата

**Files:** `scripts/quality/assert-maestro-oracle.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Убедиться, что каждый заявленный в шапке код возврата достижим. Под `$ErrorActionPreference = 'Stop'` голый `Write-Error` бросает исключение, и следующий за ним `exit N` не выполняется - процесс возвращает 1. Там, где нужен код, отличный от 1, писать `Write-Error $msg -ErrorAction Continue` перед `exit N`.

**Why:**

Недостижимый код возврата делает вердикт гейта неотличимым от общей ошибки, а вызывающая сторона обязана различать «нашёл дефект» и «не смог проверить» (CLAUDE.md Rule 7, S1070).

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 02.3 - Привести нарушающие флоу в соответствие

**Files:** `maestro/device_only/3d-video-sbs.yaml`, `maestro/device_only/3d-video-switching.yaml`
**Depends on:** Step 02.2

**Prompt for developer:**

> ПЕРЕСМОТРЕНО 2026-08-13 по ходу выполнения. Исходный текст требовал взять точные идентификаторы из разметки плеера. Проверка показала, что их не существует: `PlayerSettingsManager.showPlayerSettingsDialog()` не имеет ни одной точки вызова, а строка кнопок `custom_player_controls.xml` не содержит ни кнопки настроек, ни кнопки полноэкранного режима. Диалог «Playback Settings», который драйвят оба флоу, недостижим из интерфейса - оба флоу не могли пройти никогда. Находка вынесена в S1618.
>
> Поэтому: снять `optional: true` с двух утверждений-доказательств (это исправимо и корректно независимо от S1618), а шаги, драйвящие недостижимый диалог, не чинить вслепую. Внести оба файла в список исключений гейта с указанием причины и ссылкой на S1618, и пометить в шапке каждого флоу, что он заблокирован S1618. Исключение снимается вместе с закрытием S1618.

**Why:**

Измерение в §6.3 стратегической спеки показало, что все нарушения сосредоточены в этих двух файлах, поэтому база отсчёта не нужна и гейт включается строгим.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-maestro-oracle.ps1` - exit 0.
- `Grep` - `optional: true` не привязан ни к одному `assertVisible` в обоих файлах.
- `Grep` - шапка обоих файлов называет S1618 как блокер.
- `Grep` - список исключений гейта называет оба файла и причину.

**Status:** `[x]` done

---

### Step 02.4 - Встроить гейт в пакет быстрых проверок

**Files:** `scripts/quality/assert-fast-gates.ps1`, `docs/DEV_OPS.md`
**Depends on:** Step 02.3

**Prompt for developer:**

> Добавить `assert-maestro-oracle.ps1` в список пакета с аргументом `-Quiet`, по образцу соседних записей, и в перечень гейтов в шапке скрипта. Описать гейт в `docs/DEV_OPS.md` рядом с прочими статическими проверками: что проверяет, почему исключён `_shared/permissions.yaml`, как читать вывод.

**Why:**

Гейт вне пакета быстрых проверок остаётся негейтированным правилом, а такие правила в этом репозитории срабатывают в единицах процентов случаев (стратегическая §1, третий абзац; CLAUDE.md §3 о гейтированных и негейтированных правилах).

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit 0, вывод содержит строку гейта.
- `Grep` - `assert-maestro-oracle` присутствует в `scripts/quality/assert-fast-gates.ps1` и в `docs/DEV_OPS.md`.
- `pwsh -NoProfile -File scripts/quality/assert-gate-hints-sync.ps1` - exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `assert-maestro-oracle.ps1` - exit 0 (37 файлов, 3 исключения с причинами).
- [x] `assert-fast-gates.ps1` - гейт зарегистрирован и PASS (320 ms). Пакет в целом FAIL по трём предсуществующим гейтам вне набора изменений: assert-no-ticket-logs (16 устаревших проб чужих тикетов), assert-unreferenced-strings, assert-memory-budget.
- [ ] Dev log entry added via `scripts/post-change.ps1 -ChangeType Tooling`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Проверена главная развилка гейта на двух пробных флоу: `optional: true` на `tapOn` разрешения пропущен, тот же `optional: true` на утверждении-доказательстве отвергнут, чистый флоу прошёл. Пробы удалены.

---

## Handoff Notes to Next Phase

После этой фазы каждый новый флоу проверяется механически до запуска на устройстве. Phase 04 обязан прогонять гейт на каждом созданном файле, а не только в конце фазы.

---

## Rollback Plan

Revert phase commit(s). Гейт новый, ни один существующий гейт не изменён; правка двух флоу отменяется вместе с ним.
