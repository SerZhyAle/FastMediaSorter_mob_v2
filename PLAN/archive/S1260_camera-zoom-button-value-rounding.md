# Спецификация: S1260 - Округление значений на кнопках зума камеры

**Ticket:** S1260
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-28
**Tier:** 1 - Quick Win (ad-hoc, primitive)

---

## Problem

Кнопки пресетов зума на экране съёмки показывают эквивалентную кратность с точностью 0.1 («8.1», «16.4», «1.6») - цифры замусоривают ряд кнопок. Владелец задал правило: значения меньше 5 округлять к ближайшему шагу 0.5, значения от 5 и выше - к ближайшему целому (1.6 -> 1.5, 3.1 -> 3, 4.9 -> 5, 8.1 -> 8, 16.4 -> 16). Округляется подпись кнопки; применяемый нативный зум остаётся точным, чтобы кнопки нативного минимума и максимума (S1189) продолжали давать реальные границы оптики.

## Approach

- `app_v2/../ui/cameracapture/model/CameraRuntimeCapabilities.kt`: функция округления отображаемой кратности по правилу владельца (< 5 -> шаг 0.5, иначе шаг 1); дедупликация пресетов, чьи подписи после округления совпадают (приоритет у нативных границ).
- `app_v2/../ui/cameracapture/helpers/CameraZoomControlsManager.kt`: подпись и contentDescription кнопки берут округлённое значение; живое значение у слайдера не трогается.
- `app_v2/../ui/cameracapture/model/CameraRuntimeCapabilitiesTest.kt`: тесты правила округления и дедупликации совпавших подписей.

## Done criteria

- Тест: `roundEquivalentForDisplay` даёт 1.6 -> 1.5, 3.1 -> 3, 4.9 -> 5, 8.1 -> 8, 16.4 -> 16.
- Тест: два пресета с совпавшей после округления подписью схлопываются в один, нативная граница выживает.
- На кнопках зума нет значений с шагом 0.1: ниже 5 только шаг 0.5, от 5 - целые (проверка на устройстве).

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 27 times on the multi-lens device the status note asked for. Three distinct label rows appeared: `[1.0, 3.0, 5.0, 8.0, 10.0, 20.0, 30.0]`, `[1.0, 3.0, 5.0, 10.0, 17.0, 20.0, 30.0]`, `[2.5, 3.0, 5.0, 10.0, 20.0, 30.0]`.
- The rounding rule holds in all three: no 0.1-step value anywhere, the only sub-5 fractional label is the half step 2.5, everything from 5 upward is whole, and no row contains a duplicate label.
- Not covered: the amber native min/max pills are a visual check and no screenshot in the bundle shows the zoom row.
