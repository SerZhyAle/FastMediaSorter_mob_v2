# Draft: S0476 - Нарушение изоляции флейворов в разделе настроек (BuildConfig в общем коде)

**Ticket:** S0476
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-17
**Tier:** 3 - Moderate (ad-hoc)
**Origin:** auto-captured during S0474 research (out-of-scope finding)

> **Scope:** DRAFT idea inbox. Raw capture, no research/approval/spec-tech chaining.

---

## 0. Raw capture / Evidence

Обнаружено при исследовании S0474 (артефакт-ресёрч архитектуры настроек).

**Симптом:** прямые проверки флейвора через `BuildConfig.IS_*` в общем коде раздела настроек - нарушение CLAUDE.md §14 («No BuildConfig.IS_* flavor guards in src/main/»).

**Evidence:**
- `BuildConfig.IS_NO_LEGAL_FLAVOR` в `OtherMediaSettingsFragment` (~стр. 350, 413, 431) и `OpenSourceLicensesFragment` (~стр. 53, 58).
- (Смежно, для общей картины) флейвор-гейты через `BuildConfig.ENABLE_*` в Audio/Operations-разделах - часть из них может быть легитимной фиче-гейтацией, часть - кандидат на абстракцию.

**Нужно (предварительно):**
- Заменить прямые `BuildConfig.IS_*`-ветвления в общем коде на интерфейс в общем коде + реализации в наборах исходников флейворов (`dev/FLAVOR_DEVELOPMENT_RULES.md`).
- Отделить легитимную фиче-гейтацию (`ENABLE_*`) от флейвор-ветвлений, требующих абстракции.
- Проверка на сборках целевых флейворов (noLegal в т.ч.).

**Связь:** S0474 §5.3 закладывает абстракцию флейвор-видимости разделов настроек - этот тикет можно сделать в её русле.

**Дедуп:** `search.ps1 "flavor"` дал S0403/S0448 - оба про другое (fdroid-foss, photos network sources), не про этот isolation-дефект в настройках (2026-06-17).

---

## 1. Implementation

Reused the existing `CapabilityAvailability` + `@CompiledCapabilities` multibinding instead of inventing a new abstraction.

- Two capability ids added to `CapabilityAvailability`: `CAP_OCR_ENGINE_SELECTION` (build ships PaddleOCR alongside Tesseract → engine/model picker + noLegal-OCR language labels) and `CAP_NEWPIPE` (GPL NewPipe extractor linked → its license card).
- New `NoLegalCapabilityModule` in `src/noLegal/java/.../di/` contributes both ids `@IntoSet`. Every other flavor mounts nothing, so the methods return false and the rows/card stay hidden - no `BuildConfig.IS_*` in shared code.
- `OtherMediaSettingsFragment`: 3 `BuildConfig.IS_NO_LEGAL_FLAVOR` gates → `capabilityAvailability.isOcrEngineSelectionAvailable()`.
- `OpenSourceLicensesFragment`: Hilt-enabled, NewPipe card → `isNewPipeAvailable()`.
- `SearchableLanguagePickerDialog` (settings translation flow, same `NO_LEGAL_OCR` axis): Hilt-enabled, label flag threaded from `isOcrEngineSelectionAvailable()` into the adapter.

Behavior-preserving by construction. Validated: `compileStandardDebugKotlin` (empty set → false → hidden, as before) and `kaptNoLegalDebugKotlin` (Hilt multibinding + new `@AndroidEntryPoint` injections resolve). Flavor-isolation guard: 0 new/touched violations.

The §0 "смежно" note about `BuildConfig.ENABLE_*` in Audio/Operations is out of scope - those are legitimate compile-time feature gates, not flavor branches requiring abstraction.

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (compact)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Build validation per Implementation log (`compileStandardDebugKotlin` + `kaptNoLegalDebugKotlin`) - not re-run in this static audit. Confirmed statically: 0 `BuildConfig.IS_NO_LEGAL_FLAVOR` in `src/main`; `CAP_OCR_ENGINE_SELECTION`/`CAP_NEWPIPE` + `isOcrEngineSelectionAvailable()`/`isNewPipeAvailable()` declared in `CapabilityAvailability` and wired in `OtherMediaSettingsFragment`, `OpenSourceLicensesFragment`, `SearchableLanguagePickerDialog`; `NoLegalCapabilityModule` (src/noLegal) contributes both ids `@IntoSet @CompiledCapabilities`; dev log 5/5 files; 0 debug tags.
