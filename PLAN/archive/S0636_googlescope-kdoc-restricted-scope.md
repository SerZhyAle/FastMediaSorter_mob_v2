# Compact spec: S0636 - KDoc GoogleScope mislabels restricted Drive scope as non-sensitive

**Ticket:** S0636
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-23
**Tier:** 1 - Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при research S0429 2026-06-23

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** research S0429 (Google-content на домашней поверхности)

**Текст:**

GoogleScope.kt KDoc mislabels restricted Google Drive scopes as non-sensitive. File `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/GoogleScope.kt` line 14 documents DRIVE (full `https://www.googleapis.com/auth/drive`) as "non-sensitive scope, no Security Assessment required" - but the full Drive scope is a RESTRICTED scope per Google, requiring annual CASA security assessment. DRIVE_READONLY (`drive.readonly`) is also restricted. The class-level KDoc (lines 6-8) correctly states only non-restricted scopes are permitted and restricted scopes need a separate ticket + Security Assessment, so the line-14 comment contradicts the class invariant and could mislead a future dev into adding restricted scopes assuming no assessment is needed. Evidence: Google OAuth scopes doc lists `auth/drive` and `auth/drive.readonly` under restricted scopes (CASA). Found while researching S0429 (Google-content) on 2026-06-23. Fix: correct the KDoc to label DRIVE/DRIVE_READONLY as restricted scopes requiring CASA, consistent with the class invariant; verify no code path actually requests these scopes on a published build.

**Доказательство (на месте, рабочее дерево 2026-06-23):**

`GoogleScope.kt:14` -> `/** Full read-write access to user's Drive (non-sensitive scope, no Security Assessment required). */`
`GoogleScope.kt:6-8` (инвариант класса) -> "only non-restricted Google scopes are permitted.. Restricted scopes (Gmail, Photos, YouTube user-data) require an explicit separate ticket and Google Security Assessment".

**Вложения:**

Вложений нет.

---

## 1. Goal (RU)

KDoc в `GoogleScope.kt` фактически неверен: строка 14 помечает full Drive scope (`auth/drive`) как "non-sensitive scope, no Security Assessment required", а class-level KDoc заявляет инвариант "only non-restricted Google scopes are permitted". Но оба определённых Drive scope - `auth/drive` и `auth/drive.readonly` - по классификации Google являются **restricted** (требуют restricted-scope OAuth verification и ежегодного CASA security assessment). Цель: привести KDoc в соответствие с фактами Google - пометить `DRIVE`/`DRIVE_READONLY` как restricted и согласовать формулировку инварианта класса с реальностью (Drive - сознательно принятое ядро cloud-фичи; прочие restricted scopes - Gmail/Photos/YouTube user-data - по-прежнему за гейтом отдельного тикета и assessment). Чисто документационная правка комментариев, без изменения поведения.

## 2. Verification finding (scope usage reality)

Цель §0 "verify no code path actually requests these scopes on a published build" проверена и **опровергнута**: код запрашивает оба restricted Drive scope на published builds.

- `DRIVE` + `DRIVE_READONLY` входят в основной sign-in набор `GoogleDriveAuthPlugin.DRIVE_SIGN_IN_SCOPES` и в `GoogleDriveAuthCoordinator.driveScopes`.
- `DRIVE_READONLY` также запрашивается thumbnail-загрузчиками (`GoogleDriveThumbnailModelLoader`, `CloudThumbnailModelLoader`).
- Cloud-путь активен на flavor'ах `standard` / `legacy` / `photos` (`SUPPORT_CLOUD=true`, mount `cloudEnabled`); отключён только на `lite` (`cloudDisabled`).

Следствие (compliance) - не входит в объём этой документационной правки: published cloud-сборка подпадает под restricted-scope verification + CASA. Решение "поддерживать CASA против миграции на non-restricted `drive.file`" вынесено в отдельный parked-тикет **S0639**.

## 3. Phase 01 - Correct GoogleScope KDoc

Файл: `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/GoogleScope.kt`. Правка только комментариев (KDoc); сами scope-строки и API не меняются.

- [x] Step 1: Rewrite the `DRIVE` member KDoc (line 14). Remove the false "non-sensitive scope, no Security Assessment required". State: full read-write Drive access; **restricted** scope per Google, subject to restricted-scope OAuth verification + annual CASA security assessment.
  - Verification: Grep DRIVE KDoc no longer contains "non-sensitive" or "no Security Assessment"; contains "restricted".
- [x] Step 2: Update the `DRIVE_READONLY` member KDoc (lines 17-18) to note it is also a **restricted** scope (CASA), not just "read-only access".
  - Verification: Grep DRIVE_READONLY KDoc contains "restricted".
- [x] Step 3: Reconcile the class-level KDoc (lines 6-8). Replace the inaccurate "only non-restricted Google scopes are permitted" with the accurate invariant: the Drive scopes defined here are restricted and deliberately accepted as the core cloud feature (per S0200), so production builds are subject to Google restricted-scope verification + CASA; no **additional** restricted scopes (Gmail, Photos, YouTube user-data) may be added without an explicit separate ticket + Security Assessment.
  - Verification: Grep class KDoc no longer claims "only non-restricted .. permitted"; still gates Gmail/Photos/YouTube behind a separate ticket.
- [x] Step 4: Compile-check (comment-only change).
  - Verification: `.\a.ps1 fk` exit 0.

## 4. Done criteria

1. `GoogleScope.kt` KDoc labels `DRIVE` and `DRIVE_READONLY` as restricted scopes requiring CASA - no surviving "non-sensitive" / "no Security Assessment" wording.
2. Class-level KDoc is internally consistent with its restricted Drive members and still gates other restricted scopes.
3. Kotlin compiles.

## 5. Связи

- Источник находки: S0429 (Google-content research).
- Инвариант non-restricted-scope: S0200 (Archived) - §Non-goals исключает Gmail/Photos/YouTube как restricted; Drive принят как ядро.
- Parked compliance follow-up: **S0639** (restricted-scope CASA vs `drive.file` migration).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0429 (источник находки); S0200 (инвариант non-restricted-scope, Archived)

---

## Last Audit

**Date:** 2026-06-23 | **Verdict:** Verified | **Driver:** /spec-all

Audited against working tree.

- Done criterion 1 (DRIVE/DRIVE_READONLY labeled restricted, no "non-sensitive" / "no Security Assessment"): PASS. `GoogleScope.kt:16,19` KDoc now reads "RESTRICTED scope (per Google): subject to restricted-scope OAuth verification + an annual CASA security assessment"; grep finds zero surviving "non-sensitive" / "no Security Assessment" / "only non-restricted" wording.
- Done criterion 2 (class KDoc internally consistent + still gates other restricted scopes): PASS. `GoogleScope.kt:6-10` states the Drive scopes are restricted (core cloud feature, subject to CASA) and retains the gate "No ADDITIONAL restricted scopes (Gmail, Photos, YouTube user-data) .. without an explicit separate ticket and Security Assessment".
- Done criterion 3 (Kotlin compiles): PASS. `.\a.ps1 fk` -> BUILD SUCCESSFUL (exit 0).

Behavior unchanged (KDoc-only) - no device-test gate, no Timber tags.

Verification finding (out of scope, parked): code requests both restricted Drive scopes on cloud-enabled published builds (standard/legacy/photos). Compliance follow-up parked as **S0639**.
