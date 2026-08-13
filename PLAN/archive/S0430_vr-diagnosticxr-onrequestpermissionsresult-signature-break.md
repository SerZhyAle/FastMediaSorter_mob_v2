# S0430 - vr DiagnosticXrActivity onRequestPermissionsResult signature break

**Ticket:** S0430
**Status:** Archived
**Priority:** 60
**Date:** 2026-06-15
**Tier:** 2 - Simple (ad-hoc bugfix)

## Goal

Полный (clean / `--rerun-tasks`) Kotlin-компайл флавора noLegal должен проходить. Латентный compile-break в `vr` source set, маскируемый инкрементальной компиляцией, ломал `:app_v2:compileNoLegalDebugKotlin` на чистой пересборке. Цель тикета - устранить mismatch сигнатуры override и подтвердить чистый компайл.

## 1. Problem

- `:app_v2:compileNoLegalDebugKotlin` падал на полной пересборке: `onRequestPermissionsResult` override variance не совпадал с текущей версией `androidx.activity.ComponentActivity`.
- Инкрементальные сборки маскировали дефект (файл не перекомпилировался), поэтому он всплывал только на `--rerun-tasks` / clean build.
- Область: `vr` source set, влияет на `noLegal` (включает `vr`). standard / lite / photos / legacy не затронуты.

## 2. Resolution

- Override-параметр объявлен как инвариантный `permissions: Array<String>` (не `Array<out String>`), совпадая с текущей сигнатурой `ComponentActivity.onRequestPermissionsResult`; `super.onRequestPermissionsResult(..)` вызывается консистентно.
- Anchor: [DiagnosticXrActivity.kt:236-241](app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt#L236-L241).
- Fix уже присутствовал в источнике на момент обработки тикета (применён обычной правкой без `S0430:`-маркера, поэтому drift-check по маркеру дал CLEAN). Тикет подтверждает корректность и закрывает верификацией.

## Phases

### Phase 01 - Confirm fix coverage and verify clean compile

1. Grep `vr` + `noLegal` source sets for other `onRequestPermissionsResult` / `Array<out String>` platform-override mismatches.
   - Verification: only one `onRequestPermissionsResult` in `vr` (already `Array<String>`); none in `noLegal`; no `Array<out String>` override mismatch anywhere. PASS.
2. Run a clean `:app_v2:compileNoLegalDebugKotlin --rerun-tasks` (no incremental masking).
   - Verification: `BUILD SUCCESSFUL` with the `:app_v2:compileNoLegalDebugKotlin` task actually executed. PASS (1m 28s, 2026-06-15).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** parked during S0419 (browse checkbox work); out of scope for S0419. No code dependency.

## Last Audit

**Date:** 2026-06-15
**Mode:** compact (spec-all Simple path)
**Outcome:** Verified
**Counts:** PASS 2 · WARN 0 · FAIL 0 · MANUAL 0

> The latent `onRequestPermissionsResult` override-variance compile break described in the raw capture is resolved in the current `vr` source: the override parameter is the invariant `Array<String>` matching the current `androidx.activity.ComponentActivity` signature. Grep confirms no other override mismatch in `vr`/`noLegal`. Clean `:app_v2:compileNoLegalDebugKotlin --rerun-tasks` returned `BUILD SUCCESSFUL` (1m 28s) with the compile task executed (not UP-TO-DATE) - the previously-masking incremental path was forced to recompile. No device verification required; the criterion is a clean compile only.
