# Стратегическая спецификация: S1745 - Названия разделов не обновляются при смене языка

**Ticket:** S1745
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-16
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-09)
**Tactical spec:** `PLAN/S1745_launcher-section-name-locale-refresh/`

---

## 1. Проблема

При смене языка интерфейса названия разделов рабочего стола остаются на прежнем языке до перезапуска приложения. Дефект: строка, по-видимому, берётся один раз за запуск.

---

## 2. Цели

1. Смена языка интерфейса немедленно отражается на названиях системных разделов рабочего стола без перезапуска.
2. Пользовательские названия разделов (созданные владельцем) при смене языка не трогаются.

**Non-goals:**

- Механика смены языка приложения в целом.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- (нет)

### 3.2 Жёсткие ограничения

- **Flavor:** по `docs/FLAVOR_MATRIX.md`.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Локализация:** системные названия разделов существуют в EN/RU/UK; дефект именно в моменте применения.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-011); S1742 (пользовательские названия разделов - не локализуются).
- **Localization:** поведение при смене локали.
- **Validation level:** смена языка в настройках -> стол показывает разделы на новом языке без перезапуска.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

---

## 11. Критерии готовности (strategic-level)

1. После смены языка системные разделы на столе сразу на новом языке; пользовательские названия сохранены.

---

## Приложение. Записи инбокса (дословно)

- **L-011** - «Когда я меняю язык интерфейса, название групп не меняется. Оно остается прежним, пока не перезапустишь программу.»

---

## Last Audit

- **Date:** 2026-08-17
- **Verdict:** Verified (full contract met)
- **Checks:**
  - `ResolveLauncherDesktopUseCase.kt`: combines `settingsRepository.getSettings()` language emission and passes language to `toUi` (PASS)
  - `ResolveLauncherCommandLabelUseCase.kt`: applies locale to context via `LocaleHelper.applyLocale(context, language)` to resolve localized section/feature/action titles (PASS)
  - `labelOverride` handling: custom user-defined section names take precedence and remain untouched (PASS)
  - Automated tests: `ResolveLauncherDesktopUseCaseTest.kt` passes, verifying system section titles switch between English and Russian while custom section names are preserved (PASS)
