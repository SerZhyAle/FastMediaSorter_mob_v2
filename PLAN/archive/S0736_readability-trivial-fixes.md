# Спецификация (fix): S0736 - Тривиальные правки читаемости: NPE-`!!` + named-args (S0718)

**Ticket:** S0736
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0718 (Layer 1)
**Umbrella:** S0714

> **Scope:** Две тривиально-безопасные правки. Найдено статически (S0718).

---

## 0. Источник

Две находки аудита S0718 (`PLAN/S0718_code-readability-audit/AUDIT_FINDINGS.md`, #1 + #7).

## 1. Находки и правки

1. **P2 (латентная NPE) - `ui/share/auth/WebViewAuthDialogFragment.kt:125` `shouldInterceptRequest`.** `if (harvestMode && !request?.isForMainFrame!!)` парсится как `!((request?.isForMainFrame)!!)` → `KotlinNullPointerException` на фоновом потоке при null-`request` (контракт WebViewClient допускает null; sibling `onReceivedError:155` уже использует безопасную форму `== true`). **Fix:** `if (harvestMode && request?.isForMainFrame == false)` - null трактуется как «не суб-фрейм», без краша.
2. **P3 - `ui/browse/loading/BrowseLoadingManager.kt` 5 call-sites `updateState`.** Сигнатура `updateState(mediaFiles, usePagination: Boolean, loadingProgress: Int, totalFileCount: Int, isScanCancellable: Boolean)`; вызовы передавали голые позиционные литералы (`updateState(files, false, files.size, files.size, true)`). **Fix:** именованные аргументы во всех 5 вызовах (199/209/251/259/266).

## 2. Статус

Реализовано в этом тикете (тривиально-безопасно, политика inline). `compileStandardDebugKotlin` - зелёный.

## 3. Критерии приёмки

- [x] `!!`-NPE на null-`request` устранена (поведение на non-null сохранено: «не главный фрейм»).
- [x] Все 5 вызовов `updateState` с именованными аргументами.
- [x] Компиляция зелёная.

## 4. Связанные тикеты

- S0718 (аудит-источник), S0714 (зонтик).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0

Обе тривиальные правки S0718 подтверждены в коде:

- #1 `WebViewAuthDialogFragment.kt:126` - `if (harvestMode && request?.isForMainFrame == false)`; латентная `!!`-NPE на null-`request` устранена (sibling `:156` использует безопасную `== true`).
- #2 `BrowseLoadingManager.kt` - все 5 вызовов `updateState` (199/209/251/259/266) с именованными аргументами.

`compileStandardDebugKotlin` зелёный (main собран в проходе S0732). Заголовок файла был stale (`Draft` при catalog=`Implemented`) - синхронизирован на `Verified`.
