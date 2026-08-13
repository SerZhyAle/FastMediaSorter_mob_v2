# Стратегическая спецификация: S0546 - Удалить устаревший VR_QUALITY_DEBUG лог

**Ticket:** S0546
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-19
**Tier:** 1 - Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc - авто-захват во время S0543

> **Scope:** STRATEGIC. Skeleton, заполнить через `/spec-update` или `/spec-tech`.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-19 (авто-захват из аудита инвентаря S0543, агент Video Player)

**Текст:**

`VideoPlayerTracksObserver.kt:37` содержит постоянный лог `Timber.d("VR_QUALITY_DEBUG: selected track format=%s", videoFormat)` с KDoc-комментарием «Remove after root cause is confirmed (Phase 2 of S0041 investigation)». Тикет **S0041 уже Archived** (закрыт 2026-05-09), значит лог - осиротевший отладочный след, который должны были удалить при закрытии расследования. Нарушает дисциплину постоянных логов (CLAUDE.md §2: постоянные логи не несут отладочных investigation-меток) и Rule 20 (neuroslop).

**Эвиденс:**

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerTracksObserver.kt:37`
- S0041 `debug-vr180-fisheye-quality-regression` - Archived.

**Вложений нет.**

---

## 1. Проблема

Осиротевший отладочный лог в шиппинг-коде после закрытия S0041. Удалить строку (и связанный KDoc-комментарий, если он только про этот лог). Проверить, нет ли других `VR_QUALITY_DEBUG` следов того же расследования.

---

## 11. Критерии готовности

1. Ни одного `VR_QUALITY_DEBUG` в `app_v2/src`. - выполнено (grep: 0 совпадений).
2. Сборка проходит. - выполнено (`a.ps1 fk`: BUILD SUCCESSFUL).

Заодно удалён осиротевший KDoc-буллет про probe в шапке `VideoPlayerTracksObserver`.
