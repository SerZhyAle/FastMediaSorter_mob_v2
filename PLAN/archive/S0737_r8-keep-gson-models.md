# Спецификация (fix): S0737 - R8 keep-правила для Gson-моделей без @SerializedName

**Ticket:** S0737
**Status:** Archived
**Priority:** 60
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0719 (Layer 7, P1 release data-loss)
**Umbrella:** S0714

> **Scope:** Release-only баг: R8 переименует поля Gson-моделей → тихая потеря данных при кросс-версионном восстановлении. Найдено статически (S0719).

---

## 0. Источник

Три R8-находки аудита S0719 (`PLAN/S0719_performance-r8-release-audit/AUDIT_FINDINGS.md`): 1×P1 + 2×P3. Общая природа: Gson-сериализуемые модели без `@SerializedName` и без keep-правила; в release R8 (`proguard-android-optimize`) переименовывает приватные backing-поля → JSON-ключи = обфусцированные имена → несовпадение при чтении дефолтит поля молча (включая version-gate).

## 1. Находки и правка

1. **P1 - `domain/usecase/BackupData.kt` (`BackupPayload`/`BackupSettings`/`BackupResource`/`BackupNetworkCredential`/`BackupWebAuthSession`/`BackupCookie`).** Backup в Google Drive; кросс-версионное восстановление (заявленная фича) и debug→release молча теряют settings/resources/credentials (`ImportSettingsUseCase` ловит только `JsonSyntaxException`, несовпадение ключей → дефолты).
2. **P3 - `data/model/TrashMetadata.kt`.** `metadata.json` в `.trash/`; кросс-версионный restore деградирует (`requireNotNull(originalPath)` → `Result.failure`).
3. **P3 - `domain/game/GameStateSnapshot.kt` (+ nested `Game*`).** Снапшот игры; кросс-build restore дефолтит данные, version-gate (`schemaVersion` с дефолтом) молча обходится.

**Fix (внесён):** в `app_v2/proguard-rules.pro` добавлены `-keep class ... { *; }` для `domain.usecase.Backup**`, `data.model.TrashMetadata`, `domain.game.**` (рядом с существующим keep `domain.model.**`). Форма `-keep {*;}` (не `allowobfuscation`, т.к. без `@SerializedName` имена полей обязаны сохраниться).

## 2. Статус

Реализовано в этом тикете (тривиально-безопасно: keep-правила только сохраняют, ничего не ломают; чинят реальную потерю данных в release). Требует подтверждения release-сборкой (R8 компилируется; в идеале - `mapping.txt` показывает сохранённые имена полей).

## 3. Критерии приёмки

- [x] Keep-правила добавлены для трёх типов (классы существуют: `BackupData.kt` - 8 классов, `TrashMetadata.kt`, `domain/game/*`).
- [x] R8 зелёный с новыми правилами: `minifyStandardReleaseWithR8` BUILD SUCCESSFUL (signing-таск пропущен - keystore-файл не в дереве; R8 не зависит от подписи).
- [x] `mapping.txt`/`seeds.txt` подтверждают сохранение имён: `BackupPayload`/`TrashMetadata`/`GameStateSnapshot` отображены в свои FQN, поля (`version`/`settings`/`createdAt`/`networkCredentials`/..) сохранены под оригинальными именами = ключи Gson-JSON стабильны.

## 4. Связанные тикеты

- S0719 (аудит-источник), S0714 (зонтик), S0731 (release-безопасность БД - смежная).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [ ] Full `assembleStandardRelease` (signed AAB) green - validated by `/skill-release` worktree (keystore lives there, not in the working tree). R8 stage itself already proven green here.
