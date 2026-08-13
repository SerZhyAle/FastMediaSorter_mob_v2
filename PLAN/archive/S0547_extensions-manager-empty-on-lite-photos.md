# Стратегическая спецификация: S0547 - Скрыть Extensions Manager на lite/photos

**Ticket:** S0547
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-19
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - авто-захват во время S0543

> **Scope:** STRATEGIC. Skeleton, заполнить через `/spec-update` или `/spec-tech`.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-19 (авто-захват из аудита инвентаря S0543, агент Settings)

**Текст:**

На flavor `lite` и `photos` кнопка «Downloadable Extensions» в настройках доступна и кликабельна, но открывает пустой экран. `GeneralSettingsFragment` регистрирует обработчик клика для всех flavor, а `CapabilityAvailability.isExtensionsScreenAvailable()` возвращает `false` для lite/photos (нет OCR/перевода - нечего скачивать). Проверка доступности применяется только на welcome-странице (`WelcomeFunctionalityController`), но не во фрагменте настроек. Итог: пользователь lite/photos жмёт кнопку и видит пустой Extensions Manager (нет `BundledDeliverableSetsModule` для этих flavor).

**Эвиденс:**

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt:252`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/CapabilityAvailability.kt:45`

**Вложений нет.**

---

## 1. Проблема

Кнопка Extensions Manager видна на lite/photos, но ведёт в пустой экран. Скрыть или дизейблить её, когда `isExtensionsScreenAvailable()` ложно - так же, как это уже делается на welcome-странице. Проверить landscape-вариант разметки настроек.

---

## 11. Критерии готовности

1. На lite/photos кнопка Extensions Manager скрыта (или недоступна), на остальных - без изменений.
2. Сборка lite/photos проходит.
