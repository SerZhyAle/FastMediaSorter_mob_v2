# Спецификация (compact bugfix): S1460 - Парити-тест разрешений красный на lite и photos

> **Влит в S1454 и архивирован 2026-08-07 по решению владельца.** Содержательный вклад не потерян:
> половина про `photos` вскрыла незакрытый остаток S1454 (там `READ_CONTACTS` снимался только в
> оверлее `lite`), а наблюдение «`photos` вообще нет в релиз-чеклисте» превратилось в правку
> `docs/RELEASE_READINESS_STANDARD.md` - прогонов парити-теста стало четыре вместо трёх.
> Работа и эвиденс - в `PLAN/S1454_bugfix-lite-declares-unusable-launcher-permissions.md`,
> раздел «Last Audit - добор после влития S1460».

**Ticket:** S1460
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1442

**Текст:**

`PermissionRegistryManifestParityTest` fails on the `lite` and `photos` variants, with two identical assertions on both, and this is not new work - it was already red before S1442 touched anything (S1442 only removed `RECORD_AUDIO` from the photos manifest, and no failure mentions that permission). `standard` is green.

Measured 2026-08-07, `check-standard-fast.ps1 -Flavor <F> -Mode Unit -Tests "*PermissionRegistryManifestParityTest*"`:

- `testStandardDebugUnitTest` - exit 0.
- `testLiteDebugUnitTest` - exit 1, 3 tests completed, 2 failed.
- `testPhotosDebugUnitTest` - exit 1, 3 tests completed, 2 failed.

The two assertions, identical on both flavors:

1. `every declared permission is a registry row or a named exemption` -> `Declared with no registry row and no exemption: [android.permission.READ_CONTACTS, android.permission.POST_NOTIFICATIONS]`.
2. `no exemption outlives the divergence it excuses` -> `Exemptions for rows that no longer exist: [android.permission.BIND_NOTIFICATION_LISTENER_SERVICE]` (the S0429 `rowWithoutDeclaration` entry, whose row is gated off in these flavors).

Why it matters beyond the two entries: `docs/RELEASE_READINESS_STANDARD.md` lists this test's `lite` run as a release gate. A gate that is red and shipped anyway is not a gate - either the checklist is not being run, or a red result is being read as noise. `photos` is not in that checklist at all, which is why nobody saw its half.

Both assertions look like the same root shape: a registry row hidden behind a build gate still leaves its manifest declaration (or its exemption) standing, so the parity check sees a one-sided pair. That is the same class of problem as S1459, but a different defect - S1459 is about one row being gated on the wrong flag, this is about the parity check and the gated-row bookkeeping disagreeing across every flavor where a gate is off.

---

## 1. Проблема / симптом

<Что наблюдается, где (flavor/устройство/экран), эвиденс - лог-строки, stack trace, repro. Без имён классов на этапе захвата.>

---

## 2. Корневая причина

<расследовать>

---

## 3. Исправление

<реализовать>

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1442 - нашёл красноту, снимая разрешение в `photos`; S1459 - соседний дефект того же реестра; S0429 - ввёл запись `rowWithoutDeclaration`, которая падает во второй проверке; S1436 - ввёл сам парити-тест.

---

## 4. Проверка

<определить>
