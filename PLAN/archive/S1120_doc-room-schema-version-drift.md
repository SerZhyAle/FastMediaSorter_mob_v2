# S1120 - dev/TECH_REQUIREMENTS.md Room schema version stale and self-contradicting

**Status:** Archived
**Priority:** 35
**Date:** 2026-07-19
**Source:** parked from S1075 implementation (doc-vs-gradle pin sync)

## 0. Raw capture

Found while syncing `dev/TECH_REQUIREMENTS.md` for S1075. This is doc-vs-**code** drift (a Room `@Database` constant), not a Gradle-pin drift, so it is out of S1075's scope.

**Symptom:** the document states the Room schema version in two places with two different, both-wrong values.

**Evidence:**
- `dev/TECH_REQUIREMENTS.md` §4.6: "Room DB version | 19 | Current schema version (see AppDatabase.kt)".
- `dev/TECH_REQUIREMENTS.md` §10 Database: "Room DB version | 41 | Current schema in AppDatabase" and "Migrations | 40 logical steps | through v41".
- Actual `app_v2/.../data/local/db/AppDatabase.kt`: `version = 42`.

So §4.6 (19) and §10 (41) disagree with each other, and neither matches the real value (42).

**Why its own ticket:** the checker `check-doc-vs-gradle.ps1` does not cover schema version (it is not a Gradle pin), so no gate catches it; and a correct fix must also reconcile the adjacent §10 numbers (migration-step count "through v41", entity count) against `AppDatabase.kt`, which needs its own verification pass.

## 1. Next step

Read `AppDatabase.kt` for the authoritative `version`, entity count, and migration count; set §4.6 and §10 to a single consistent set of values; consider whether the Room schema version is worth adding as a doc-vs-source check (separate from the Gradle-pin checker).

## 2. Plan

**Goal (RU):** Привести Room-строки `dev/TECH_REQUIREMENTS.md` в §4.6 и §10 к единому набору значений, совпадающему с живым `AppDatabase.kt`/`DatabaseModule.kt`.

**Authoritative values (live code 2026-07-19):**
- `AppDatabase.kt:36` -> `version = 42`.
- 21 entities (`@Database(entities=..)`, `AppDatabase.kt:13-35`) - doc §10 already correct.
- 41 registered migrations (`DatabaseModule.kt:76-116` addMigrations, lines 76..116 = 41 entries), path through v42.

### Phase 1 - Doc reconciliation

1. §4.6 `Room DB version | 19` -> `42`.
2. §10 `Room DB version | 41` -> `42`.
3. §10 `Migrations | 40 logical steps | .. through v41` -> `41 registered migrations | .. through v42`.
   - Verification: grep shows no stale `19`/`41`/`v41` in the Room rows; all three read `42`/`v42`/`41 registered migrations`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1075 (doc-vs-gradle pin sync, parent of this parked finding).

## Last Audit

**Дата:** 2026-07-19 (`/spec-all` Simple path через `/spec-next`). **Вердикт:** Verified.

- `dev/TECH_REQUIREMENTS.md:127` (§4.6) Room DB version `19` -> `42`.
- `dev/TECH_REQUIREMENTS.md:437` (§10) Room DB version `41` -> `42`.
- `dev/TECH_REQUIREMENTS.md:439` (§10) `40 logical steps / through v41` -> `41 migrations / through v42`.
- §10 entity count `21` подтверждён корректным против `AppDatabase.kt:13-35` (21 entities) - без правки.
- Авторитет: `AppDatabase.kt:36` `version = 42`; `DatabaseModule.kt:76-116` addMigrations = 41 зарегистрированных объектов.
- Verify: grep - все три строки читают 42/v42; stale `19`/`41`/`v41` в Room-строках отсутствуют. `assert-doc-pin-drift.ps1` PASS (без регрессии pin-строк). Doc-only, сборка/устройство не требуются.

**Опциональный follow-up (не в объёме):** §1 предлагает добавить doc-vs-source проверку Room `@Database` version (нынешний `check-doc-vs-gradle` покрывает только Gradle-пины, поэтому этот дрейф гейт не ловил). Отдельная задача - постройка нового `assert-*` гейта, парсящего `AppDatabase.kt` version против двух doc-строк. Не выполнено здесь во избежание scope creep.
