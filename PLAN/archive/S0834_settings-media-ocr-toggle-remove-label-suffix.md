# S0834 - Settings Media: drop redundant "(OCR)" suffix from OCR toggle label

**Ticket:** S0834
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 1 - Quick Win
**Source:** User request 2026-07-01 (`/spec-draft`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

В Settings -> Media, группа «Перевод, оцифровка..», тогглер «Разрешить распознавание текста (OCR)» дублирует аббревиатуру OCR: она уже присутствует в иконке строки (`ic_ocr`). Убрать суффикс «(OCR)» из подписи -> «Разрешить распознавание текста». Только текст подписи; иконка, поведение, порядок, соседние строки - без изменений.

## 1. Confirmed scope (research 2026-07-01)

Toggle title is the shared string `enable_ocr`, referenced once as `app:str_title` in `fragment_settings_other.xml` (row with `app:str_icon="@drawable/ic_ocr"`, MEDIA destination). Both orientations use the same string resource - no inline-duplicated label, so a single string edit covers portrait + landscape (Open point 2 resolved). Values carry the redundant suffix in all three locales:

- EN `values/strings.xml`: "Enable text recognition (OCR)"
- RU `values-ru/strings.xml`: "Разрешить распознавание текста (OCR)"
- UK `values-uk/strings.xml`: "Дозволити розпізнавання тексту (OCR)"

Left untouched (different, correct contexts): the group header `settings_category_other` ("Перевод, оцифровка (OCR)"), `welcome_func_ocr`, and `enable_ocr_summary`.

## 2. Phase 1 - Strip the suffix (EN/RU/UK parity)

Via `scripts/utils/set-android-string.ps1 -Action set` (byte-preserving), remove " (OCR)" from `enable_ocr` in en/ru/uk:

- EN -> "Enable text recognition"
- RU -> "Разрешить распознавание текста"
- UK -> "Дозволити розпізнавання тексту"

## 3. Phase 2 - Rule 22 settings-doc-sync

A setting title changed (naming) -> regenerate `docs/settings/settings-manifest.json` (`SettingsManifestExportTest -Dsettings.manifest.generate=true`) and re-render `docs/SETTINGS_REFERENCE*.md` (`render-settings-reference.ps1`); annotations key `enable_ocr` unchanged (title-only). Gate: `assert-settings-doc-sync.ps1`.

**Verification:** `check_strings_localized.ps1 -KeyPrefix enable_ocr` OK (EN/RU/UK parity); `assert-settings-doc-sync.ps1` green; `.\a.ps1 fr` passes.

## 4. Open points

Resolved (see §1): single shared string, no inline duplication; parity across EN/RU/UK.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0833 / S0832 (sibling Settings -> Media row tweaks).

## Related

- S0833, S0832 - sibling Settings -> Media row/label quick wins.

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all)
**Verdict:** Verified

- `enable_ocr` suffix " (OCR)" removed in all three locales via `set-android-string.ps1` (byte-preserving): EN "Enable text recognition", RU "Разрешить распознавание текста", UK "Дозволити розпізнавання тексту". Byte-verified via Grep; `ic_ocr` icon (carrying "OCR") and the toggle behavior unchanged.
- Single shared string `enable_ocr` (`fragment_settings_other.xml` `str_title`) covers portrait + landscape - no layout edit. Group header `settings_category_other`, `welcome_func_ocr`, `enable_ocr_summary` left intact.
- Rule 22: `settings-manifest.json` regenerated (`SettingsManifestExportTest -Dsettings.manifest.generate=true`) - `titleEn/Ru/Uk` now suffix-free; `SETTINGS_REFERENCE*.md` re-rendered; `enable_ocr` annotation unchanged (title-only).
- `check_strings_localized.ps1 -KeyPrefix enable_ocr` -> EN/RU/UK OK; `assert-settings-doc-sync.ps1` -> OK (catalog complete, manifest fresh, annotations covered, reference + HOW_TO in sync); `a.ps1 fr` -> BUILD SUCCESSFUL.
- No ALL_FEATURES record: redundant-suffix label cleanup on an existing setting, not a new capability.
