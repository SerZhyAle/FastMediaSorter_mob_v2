# Стратегическая спецификация: S1807 - Быстрые проверки модуля часов в a.ps1

**Ticket:** S1807
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-19
**Tier:** 3 - Tactical (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при планировании S1710, 2026-08-19

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-19

**Симптом.** У модуля часов в `a.ps1` есть ровно одна быстрая цель - `fw` (компиляция Kotlin, `check-standard-fast.ps1 -Module wear -Mode Code`). Целей для ресурсов и для юнит-тестов часов нет. При этом соседние короткие цели `fr` (ресурсы) и `fu` (юнит-тесты) существуют и молча проверяют `app_v2`, а не `wear`.

**Почему это дефект, а не неудобство.** Проверка называется так, что читается как «проверить то, что я сейчас менял». Написав в плане `.\a.ps1 fu` для тикета, который трогает только модуль часов, получаешь зелёный результат, не запустив ни одного теста часов. Отказа при этом нет: цель существует, отрабатывает и выходит нулём.

**Измерено 2026-08-19 при написании тактического плана S1710.** Первая версия плана из восьми фаз ставила `.\a.ps1 fk`, `fr` и `fu` предикатами проверки для файлов модуля часов - то есть одиннадцать проверок, ни одна из которых не смотрела бы на изменённый модуль. Ошибку поймал самопроверочный проход планировщика, а не гейт.

**Что уже есть.** `scripts/builders/check-standard-fast.ps1` принимает `-Module wear` и режимы `Code`, `CodeAndResources`, `Unit`, и сам отказывается от `-Flavor` для часов. То есть механизм готов целиком, не хватает только коротких имён и, возможно, отказа у существующих.

---

## 1. Проблема

Короткое имя проверки не называет модуль, а модуля теперь два. Автор плана или агент, работающий с часами, набирает привычную цель и получает подтверждение о чужом модуле.

---

## 2. Цели

1. У модуля часов есть короткие цели для ресурсов и для юнит-тестов, как у телефонного.
2. Проверка, запущенная не для того модуля, который менялся, перестаёт выглядеть как подтверждение.

**Non-goals:**

- Переименование существующих телефонных целей - они называют самый частый случай и ломать их не за что.
- Новый гейт, сверяющий изменённый модуль с запущенной целью, - это отдельное решение, если коротких имён окажется мало.

---

## 3. Решение

Три коротких имени вместо одного: `fw` (код), `fwr` (ресурсы), `fwu` (юнит-тесты) - тот же `check-standard-fast.ps1 -Module wear`, который уже принимает все три режима. Плюс баннер проверки называет модуль вслух, чтобы вывод чужого модуля читался как чужой, а не как подтверждение.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1496 (добавил `fw`), S1710 (находка), S1730 и S1781 (измеренные промахи)

---

## 6. Открытые вопросы / Research items

1. **Достаточно ли новых имён**
   - **Вопрос:** решают ли две новые цели проблему, или нужен отказ от `fu`/`fr` при изменённом модуле часов?
   - **Статус:** Resolved (2026-08-19)
   - **Ответ:** новых имён достаточно; отказ гейта пока не оправдан.
   - **Измерение (2026-08-19, поиск по `PLAN/` за телефонной целью в тикетах, трогающих `wear/src`):** пять случаев в трёх тикетах. S1710 - одиннадцать проверок в первой версии тактического плана, промах поймал самопроверочный проход планировщика. S1781, фазы 01 и 02 - в плане стоит `.\a.ps1 fk`, а в логе шага записано, что вместо неё запущен `:wear:compileDebugKotlin`, потому что плановая цель собирает `app_v2` и эти файлы не покрыла бы. S1730, фазы 01 и 02 - в плане стоит `.\a.ps1 fu`, а в логе шага записан `check-standard-fast -Mode Unit -Module wear`.
   - **Почему это ответ:** во всех пяти случаях промах поймал человек, и ни один не дошёл до записанного зелёного результата. Промах при этом всегда на стороне автора плана, а не исполнителя: исполнитель знал верную команду и набирал полное имя скрипта - короткого имени просто не существовало. Отказ гейта лечил бы то, что пока никого не подвёл, а короткое имя лечит ровно то место, где промах возникает.
   - **Что оставлено за границей:** если после появления `fwr`/`fwu` промах повторится и дойдёт до записанного зелёного результата, отказ становится оправданным - это уже non-goal из §2, отдельное решение с собственным тикетом.

---

## 7. Фазы

### Phase 01 - Wear fast-check targets

**Objective:** the watch module gains `fwr` and `fwu` next to the existing `fw`, all three are discoverable from `a.ps1` help and the ops docs, and every fast check names the module it actually checked.

**Files Touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `a.ps1` | Modified | <= 340 |
| `scripts/builders/check-standard-fast.ps1` | Modified | <= 220 |
| `docs/DEV_OPS.md` | Modified | - |
| `docs/BUILD_TEST_FAST_PATH.md` | Modified | - |
| `CLAUDE.md` | Modified | - |
| `AGENTS.md` | Modified | - |

---

#### Step 01.1 - Add `fwr` and `fwu` to the `a.ps1` script map

**Files:** `a.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `$scripts` hashtable, add `fwr` pointing at `scripts\builders\check-standard-fast.ps1` with `Args = @{ Mode = 'Resources'; Module = 'wear' }` and `fwu` with `Args = @{ Mode = 'Unit'; Module = 'wear' }`. Place both directly after the existing `fw` entry and give each a trailing `# S1807:` comment naming what it checks.

**Why:**

Goal 1 in section 2 requires the watch module to have the same short resource and unit-test targets the phone module already has, and section 0 records that `check-standard-fast.ps1` already accepts `Resources` and `Unit` for `-Module wear` - only the short names are missing.

**Verification:**

- `Grep` - `'fwr'` and `'fwu'` each match exactly once in the `$scripts` map.
- `pwsh -NoProfile -File ./a.ps1 fwr` exits 0.
- `pwsh -NoProfile -File ./a.ps1 fwu` exits 0.

**Status:** `[x]` done

---

#### Step 01.2 - Name all three wear targets in both `a.ps1` help blocks

**Files:** `a.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fw`, `fwr` and `fwu` lines to the `.DESCRIPTION` comment-based help and to the unknown-command `Write-Host` listing. List `fw` too: S1496 added that target and neither help block ever mentioned it.

**Why:**

Section 1 states the problem is that a short name does not name its module, and a name absent from both help listings is a name nobody reaches for at all - which is how `fw` stayed unused while the miss in section 0 kept happening.

**Verification:**

- `Grep` - `fw`, `fwr` and `fwu` each appear in the `.DESCRIPTION` block and in the unknown-command listing.
- `pwsh -NoProfile -File ./a.ps1 zzz` prints all three and exits 1.

**Status:** `[x]` done

---

#### Step 01.3 - Make the fast-check banner name the module

**Files:** `scripts/builders/check-standard-fast.ps1`
**Depends on:** - independent of 01.1

**Prompt for developer:**

> Change the `Fast $checkLabel check..` banner so the label names the module: `app_v2/<Flavor>` for the phone module and `wear` for the watch one. Nothing reads this line mechanically - it is the line a human pastes into a step log as proof.

**Why:**

Goal 2 in section 2 requires a check run against the wrong module to stop looking like a confirmation, and the banner is exactly what gets copied into a step log, so naming the module there is what makes a foreign verdict read as foreign.

**Verification:**

- `Grep` - the banner line references `$Module`.
- `pwsh -NoProfile -File ./a.ps1 fw` prints a banner containing `wear`.
- `pwsh -NoProfile -File ./a.ps1 fk` prints a banner containing `app_v2`.

**Status:** `[x]` done

---

#### Step 01.4 - Document the three targets in the ops docs

**Files:** `docs/DEV_OPS.md`, `docs/BUILD_TEST_FAST_PATH.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fw`, `fwr` and `fwu` rows to the `a.ps1` command table in `docs/DEV_OPS.md`. In `docs/BUILD_TEST_FAST_PATH.md`, replace the raw `.\gradlew.bat :wear:assembleDebug` recommendation in the "Wear-only change" section, in the command inventory and in the routing table with the three short targets, keeping the assemble call only where packaging proof is the point.

**Why:**

Section 1 states that the author of a plan types the habitual target, and a document that tells the reader to call gradle directly for the watch module is where that habit comes from; a raw `gradlew` call also takes no `BUILD.LOCK`.

**Verification:**

- `Grep` - `fwr` and `fwu` appear in `docs/DEV_OPS.md`.
- `Grep` - `fw`, `fwr` and `fwu` appear in the "Wear-only change" section and in the routing table of `docs/BUILD_TEST_FAST_PATH.md`.
- `Grep` - every surviving `:wear:assembleDebug` in that document carries an explicit packaging-proof qualifier, so it is no longer the default proof for a wear change.

**Status:** `[x]` done

---

#### Step 01.5 - Add the wear targets to the two agent rule files

**Files:** `CLAUDE.md`, `AGENTS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the "Checks" list in CLAUDE.md Rule 9 and the matching fast-proof line in `AGENTS.md` with `fw`, `fwr` and `fwu`, naming the module each covers.

**Why:**

Section 1 states the reader who picks a check is often an agent, and the rule files are the list an agent reads first - a target missing there is a target it will not choose.

**Verification:**

- `Grep` - `fwr` and `fwu` appear in both `CLAUDE.md` and `AGENTS.md`.
- `pwsh -NoProfile -File scripts/post-change.ps1` closes with `post-change: PASS`.

**Status:** `[x]` done

---

**Phase Done Criteria**

- [x] Every `Step 01.*` above is `[x]` done.
- [x] `pwsh -NoProfile -File ./a.ps1 fw`, `fwr` and `fwu` each exit 0.
- [x] Dev log entry added via `scripts/post-change.ps1` - `post-change: PASS (Tooling, 5634 ms)`.

---

---

## 8. Implementation State

**2026-08-19 - Phase 01 done.**

- `a.ps1`: `fwr` (`-Mode Resources -Module wear`) and `fwu` (`-Mode Unit -Module wear`) added next to `fw`; all three now named in the `.DESCRIPTION` help and in the unknown-command listing, `fw` included - S1496 shipped it without either.
- `scripts/builders/check-standard-fast.ps1`: the banner label names the module - `app_v2/<Flavor>` for the phone module, `wear` for the watch one.
- Docs: `docs/DEV_OPS.md` command table, `TEST & VERIFY` block and validation ladder; `docs/BUILD_TEST_FAST_PATH.md` command inventory, section 9, the routing table and the anti-patterns list, where the raw `:wear:assembleDebug` recommendation is now kept only for packaging proof.
- Rules: CLAUDE.md Rule 9 and the matching `AGENTS.md` line name the three targets and state that the phone ones do not cover the watch module; the Rule 6 foreground list gains them with measured times.

**No user-visible impact.** This ticket ships developer tooling only - two launcher targets, a banner label and documentation. Nothing reaches the app, so there is no `ALL_FEATURES` record to write.

**Measured, warm daemon:** `.\a.ps1 fw` exit 0 in 2 s, `.\a.ps1 fwr` exit 0 in 0.9 s, `.\a.ps1 fwu` exit 0 in 11 s (`assert-test-suite-complete: PASS`, 20 reports for 20 `*Test.kt`). `.\a.ps1 fk` prints `Fast app_v2/Standard check..`, `.\a.ps1 fw` prints `Fast wear check..`. `.\a.ps1 zzz` lists all three and exits 1.

## 10. Связи с другими спеками

- S1710 - тикет, при планировании которого находка обнаружена; сам он обходится полным именем скрипта.
- S1496 - тикет, добавивший `fw`; эта спека продолжает его для оставшихся двух режимов.

---

## Last Audit

**Date:** 2026-08-19
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Second iteration. The single WARN of the first pass - step 01.4's Verification predicate contradicting its own Prompt - was corrected in the spec, and the third `:wear:assembleDebug` in the command inventory of `docs/BUILD_TEST_FAST_PATH.md` gained the packaging-proof qualifier the rewritten predicate requires.

Evidence re-run 2026-08-19: `.\a.ps1 fw` exit 0, `.\a.ps1 fwr` exit 0, `.\a.ps1 fwu` exit 0 with `assert-test-suite-complete: PASS`; `.\a.ps1 fk` prints `Fast app_v2/Standard check..` and `.\a.ps1 fw` prints `Fast wear check..`; `.\a.ps1 zzz` lists all three and exits 1; `check-open-items-carried.ps1 -Id S1807` exit 0; zero `Timber.d("S1807:` hits across `.kt`; `post-change: PASS`.

### Manual / on-device

- none - the ticket ships developer tooling only; every predicate is a static or script-exit check and all were run.

**Parked during this ticket:** S1809 - `guard-fire-and-forget.ps1` lists no wear target, so backgrounding `fw`/`fwr`/`fwu` is not refused. The hook is a canon plugin file outside this repository.
