# Спецификация (compact bugfix): S1864 - Префлайт скипает тикет, чей блокер уже в BlockNeedUserTest

**Ticket:** S1864
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-21
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-21

**Захвачено во время:** S1861

**Текст:**

Preflight auto-skips a ticket whose blocker is at BlockNeedUserTest, contradicting the documented owner ruling.

EVIDENCE (verbatim, gathered 2026-08-21 during /spec-do):

scripts/spec_catalog/preview.ps1:231
    $unverifiedBlockers = @($dependsOn | Where-Object { $_.status -ne 'Verified' -and $_.status -ne 'Archived' })
and :239-243
    elseif ($unverifiedBlockers.Count -gt 0) {
        # S1775: directed spec link is truth. Any unverified blocker excludes the ticket from
        # automatic selection regardless of lifecycle status; status remains descriptive.
        $autoSkip = 'blocker-not-verified'

So only Verified and Archived release a dependent. A blocker sitting in BlockNeedUserTest counts as unverified and the dependent is auto-skipped, then skip-cached with a 7-day TTL.

PLAN/RELEASE_QUEUE.md states the opposite as an owner ruling:
    "A ticket dependency releases as soon as the blocker reaches **BlockNeedUserTest** - the code is in the tree by then and only the owner's device pass is left. Never make a dependent wait for Verified (owner ruling 2026-08-07)."

MEASURED IMPACT, same session: S1860 (bugfix-wear-bridge-service-dies-mid-request) reached BlockNeedUserTest on 2026-08-21 with its code in the tree and 3 live probes. S1846 and S1697 were parked the same day with a literal `Blocker: S1860` token. Under the current predicate both are now auto-skipped as blocker-not-verified for up to 7 days, although the owner ruling releases them the moment S1860 hit BlockNeedUserTest. All three are release-package 33.

Existing skip-cache evidence that this is not hypothetical - temp/spec-next-skip-cache.json holds:
    "S1714": "blocker-not-verified: Depends on S1704(BlockNeedUserTest)"

RESEARCH NEEDED (do not guess): which statuses should release a dependent - BlockNeedUserTest only, or every status at or above Implemented (Implemented, Verified, BlockNeedUserTest, Archived)? The tests scripts/spec_catalog/preview.tests/Run-Tests.ps1 cases B4, F4 and G1 encode the current predicate and must be re-judged, not merely edited to pass. S1775 introduced the current line and its rationale must be read before changing it, so the fix does not reopen what S1775 closed.

CONFIRMED BY REPRODUCTION, same session, 2026-08-21 01:25 - this is an observation, not a reading of the code. After both tickets were parked, `preview.ps1` was run on each:

    pwsh -NoProfile -File scripts/spec_catalog/preview.ps1 -Id S1846
      depends_on: [{'id': 'S1860', 'status': 'BlockNeedUserTest'}]
      auto_skip:  blocker-not-verified | Depends on S1860(BlockNeedUserTest)

    pwsh -NoProfile -File scripts/spec_catalog/preview.ps1 -Id S1697
      depends_on: [{'id': 'S1860', 'status': 'BlockNeedUserTest'}]
      auto_skip:  blocker-not-verified | Depends on S1860(BlockNeedUserTest)

The directional token parses correctly and the blocker's status is read correctly as BlockNeedUserTest - the ticket is skipped anyway. So the defect is purely the release predicate, not the parsing, and a fix does not need to touch the token channels.

Dedup done via scripts/spec_catalog/search.ps1 single tokens: blocker -> S1775 (Verified, introduced this line), preflight -> S1800 (Verified, unrelated), dependency -> no records, depends -> no records. No open ticket covers it.

---

## 1. Проблема / симптом

Предпросмотр тикета считает блокер «незавершённым», пока тот не дошёл до `Verified` или `Archived`. Владелец же постановил обратное: зависимость освобождается, как только блокер достиг `BlockNeedUserTest`, потому что код уже в дереве и остаётся только его проверка на устройстве (`PLAN/RELEASE_QUEUE.md`, строка 24, решение владельца 2026-08-07).

Измерено 2026-08-21 воспроизведением: S1846 и S1697 получают `auto_skip: blocker-not-verified | Depends on S1860(BlockNeedUserTest)`, хотя код S1860 в дереве. Живая запись того же класса лежит в кэше пропусков: `"S1714": "blocker-not-verified: Depends on S1704(BlockNeedUserTest)"`.

Вторая половина симптома - кэш. `spec-next-preflight.ps1` выбрасывает закэшированный id из списка **до** предпросмотра, поэтому вердикт о чужом статусе живёт до семи суток после того, как блокер освободился. Это ломает выборку независимо от предиката: даже по старому правилу тикет остаётся пропущенным ещё неделю после того, как блокер дошёл до `Verified`.

---

## 2. Корневая причина

Предикат освобождения записан в `scripts/spec_catalog/preview.ps1` списком из двух статусов, набранным на месте: `$_.status -ne 'Verified' -and $_.status -ne 'Archived'`. Определение «код готов» в проекте уже существует и живёт в другом месте - `Test-ReleaseReadyStatus` в `scripts/spec_catalog/_lib.ps1` возвращает `Implemented`, `Verified`, `BlockNeedUserTest`, и по нему же `RELEASE_READY.md` отделяет готовое от незавершённого. Два независимых определения одного понятия разошлись.

S1775 здесь не переоткрывается. Он решал другой вопрос - учитывается ли разобранный блокер у тикета, которому никто не менял статус руками, и его решение (истина в направленной связи, а не в статусе) сохраняется полностью. Сам S1775 назвал этот предикат точкой расширения в §5.4 и в побочном наблюдении §6.1: «третий случай - тикет, ждущий пятерых, каждый из которых ждёт проверки на устройстве. Это не блокировка работой, а блокировка вашим временем; правило, которое их различает, в проекте пока не записано». S1864 записывает это правило.

Причина второй половины - кэширование производного вердикта. `blocker-not-verified` - единственная причина пропуска, которая говорит о статусе **чужого** тикета: она устаревает от чужой работы, не касающейся закэшированного тикета. Предпросмотр пересчитывает её на каждом прогоне, поэтому кэш не экономит ничего и только удерживает устаревший ответ.

---

## 3. Исправление

Освобождающие статусы - `Implemented`, `Verified`, `BlockNeedUserTest`, `Archived`: множество `RELEASE_READY` плюс архив. Ответ на вопрос из §0 «BlockNeedUserTest только или всё от Implemented и выше» - всё от `Implemented` и выше, потому что обоснование владельца («код в дереве») истинно для `Implemented` в той же мере, а разделение очереди по этому же множеству уже записано в `CLAUDE.md` §4 и в `SCHEMA.md`.

Определение не дублируется третий раз: оно выносится в общий leaf-файл по образцу `_research-items.ps1` (S1621), который `_lib.ps1` и `preview.ps1` оба подключают. `preview.ps1` намеренно не подключает `_lib.ps1` целиком - там `Set-StrictMode -Version Latest`, под которым чтение `$rec.statusNote` падает на каждой записи без заметки.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1775, S1860, S1846, S1697, S1714, S1621, S1482
- **Owner decision (derived, not asked):** the release set is `RELEASE_READY` plus `Archived`. Sourced from the ruling in `PLAN/RELEASE_QUEUE.md` line 24 and the file split in `CLAUDE.md` section 4 - both already written, so no new owner input is required.

---

## 4. Проверка

- `preview.ps1` на тикете, чей блокер в `BlockNeedUserTest`, возвращает `auto_skip: null`.
- `preview.ps1` на тикете, чей блокер в `Draft`/`Approved`/`BlockQuestions`, по-прежнему возвращает `blocker-not-verified`.
- `preview.tests/Run-Tests.ps1` завершается без FAIL, включая пересуждённые B4/F4/G1 и новый случай H.
- Закэшированный `blocker-not-verified` больше не удаляет кандидата из ранжированного списка до предпросмотра.

---

## 5. Фазы

### Phase 01 - Shared release predicate

#### Step 01.1 - Extract the release-ready status set into a shared leaf file

**Files:** `scripts/spec_catalog/_status-sets.ps1` (new), `scripts/spec_catalog/_lib.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/_status-sets.ps1` holding `Test-ReleaseReadyStatus` (moved from `_lib.ps1`, its comment included) and a new `Test-BlockerReleasedStatus` that returns true for a release-ready status or `Archived`. Keep the file strict-mode safe: declared parameters, no unassigned variable reads. Dot-source it from `_lib.ps1` in place of the local function definition.

**Why:**

Two independent definitions of "the code is done" already diverged - `preview.ps1` counted `Verified`/`Archived` while `_lib.ps1` counted `Implemented`/`Verified`/`BlockNeedUserTest` - and a leaf file shared by both is the mechanism this repository already used for the research-item parse in S1621, so the verdict one script reports is the verdict the other enforces.

**Verification:**

- `Glob` - `scripts/spec_catalog/_status-sets.ps1` exists.
- `Grep` - `function Test-ReleaseReadyStatus` matches exactly once across `scripts/spec_catalog/`.
- `Grep` - `_status-sets.ps1` appears in `_lib.ps1`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1775 -Format json` - exit 0, proving `_lib.ps1` still loads under StrictMode.

**Status:** `[ ]` not done

---

#### Step 01.2 - Replace the hardcoded predicate in preview.ps1

**Files:** `scripts/spec_catalog/preview.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Dot-source `_status-sets.ps1` beside the existing `_research-items.ps1` line and rewrite the `$unverifiedBlockers` filter to reject only a blocker for which `Test-BlockerReleasedStatus` is false. Update the S1775 comment above the `blocker-not-verified` branch to name the releasing statuses and cite both S1775 and S1864, without claiming S1775 was wrong.

**Why:**

This line is the defect: a blocker at `BlockNeedUserTest` has its code in the tree and the owner ruling of 2026-08-07 releases the dependent at that moment, yet the filter holds it for up to seven more days.

**Verification:**

- `Grep` - `Test-BlockerReleasedStatus` present in `preview.ps1`; the literal `-ne 'Verified'` absent from it.
- Run `preview.ps1 -Id S1846` - `auto_skip` is null.
- Run `preview.ps1 -Id S1697` - `auto_skip` is null.
- Run `preview.ps1 -Id S1717` - `auto_skip` is still `blocker-not-verified`, its blockers being in `BlockQuestions`.

**Status:** `[ ]` not done

---

### Phase 02 - Stale cached verdict

#### Step 02.1 - Stop consulting cached blocker verdicts in preflight

**Files:** `scripts/spec_catalog/spec-next-preflight.ps1`
**Depends on:** Phase 01

**Prompt for developer:**

> In the step that drops skip-cached ids from the ranked list, ignore any cache entry whose reason begins with `blocker-not-verified` and let the live preview decide instead. Record the ignored ids in the emitted JSON so the operator sees the cache was overridden rather than silently bypassed. Keep the script read-only - do not evict the entry.

**Why:**

A cached `blocker-not-verified` is the only skip reason that reports another ticket's status, so it goes stale from work that never touches the cached ticket, and preflight drops the id before previewing it - which kept S1714 out of selection while its blocker S1704 already sat at `BlockNeedUserTest`.

**Verification:**

- `Grep` - `blocker-not-verified` present in `spec-next-preflight.ps1`.
- Run `spec-next-preflight.ps1` - exit 0, and `S1714` no longer appears in `skip_cached_ids`.

**Status:** `[ ]` not done

---

#### Step 02.2 - Stop persisting blocker verdicts from /spec-next

**Files:** `.claude/commands/spec-next.md`, `.claude/reference/spec-next.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the Stage 1 instruction that persists an auto-skip to the cache, exclude `blocker-not-verified` and say why in one clause. In `.claude/reference/spec-next.md`, correct the sentence claiming a blocker releases only when it is `Verified` so it names the release set, and correct the `auto_skipped[]` reason description if it repeats the old rule.

**Why:**

The reference file states the superseded rule as fact, and a rule contradicting both the owner ruling and the shipped code is read by every future session as the specification of correct behaviour.

**Verification:**

- `Grep` - the phrase `unless the named blocker is currently` absent from `.claude/reference/spec-next.md`.
- `Grep` - `blocker-not-verified` appears in the Stage 1 skip-cache instruction of `.claude/commands/spec-next.md`.

**Status:** `[ ]` not done

---

### Phase 03 - Tests and documentation

#### Step 03.1 - Re-judge the three existing blocker cases and add the release case

**Files:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1`
**Depends on:** Phase 01

**Prompt for developer:**

> Re-judge B4, F4 and G1 against the new predicate before editing anything: each builds its fixture from a blocker in `Approved` or `Draft`, both of which stay unreleased, so all three keep their verdicts unchanged - record that in a comment naming S1864 rather than silently leaving them. Add case H: a probe whose `**Depends on:**` names a blocker currently in `BlockNeedUserTest` gets `auto_skip = null`, printed as skipped when the catalog holds no such blocker.

**Why:**

The captured material required these three cases to be re-judged rather than edited to pass, and without a case asserting the release the predicate can silently revert to the old two-status list.

**Verification:**

- Run `pwsh -NoProfile -File scripts/spec_catalog/preview.tests/Run-Tests.ps1` - zero FAIL.
- `Grep` - `S1864` present in `Run-Tests.ps1`.

**Status:** `[ ]` not done

---

#### Step 03.2 - Record the release predicate in SCHEMA.md

**Files:** `scripts/spec_catalog/SCHEMA.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> State in `SCHEMA.md` which statuses release a dependent, that the set is one definition shared through `_status-sets.ps1` by `_lib.ps1` and `preview.ps1`, and that it is the set `RELEASE_READY.md` uses plus `Archived`. Extend the existing S1621 leaf-file sentence rather than opening a second passage about leaf files.

**Why:**

The predicate lived only in code and the one document describing it stated the opposite, which is how the divergence survived from S1775 to S1864 unseen.

**Verification:**

- `Grep` - `_status-sets.ps1` present in `SCHEMA.md`.
- Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.

**Status:** `[ ]` not done

---

## 6. Открытые вопросы / Research items

1. **Какие статусы освобождают зависимость**
   - **Вопрос:** только `BlockNeedUserTest` или всё, начиная с `Implemented`?
   - **Статус:** Resolved
   - **Ответ (2026-08-21):** `Implemented`, `Verified`, `BlockNeedUserTest`, `Archived`. Обоснование владельца - «код в дереве» - истинно для `Implemented` не меньше, чем для `BlockNeedUserTest`, а это множество уже записано дважды: `Test-ReleaseReadyStatus` в `_lib.ps1` и разделение `RELEASE_QUEUE`/`RELEASE_READY` в `CLAUDE.md` §4. Выведено из записанных решений, не спрошено у владельца.
2. **Переоткрывает ли исправление S1775**
   - **Вопрос:** не отменяет ли смена предиката то, что закрыл S1775?
   - **Статус:** Resolved
   - **Ответ (2026-08-21):** нет. S1775 решал, влияет ли разобранный блокер на выбор у тикета, которому не меняли статус руками; это решение сохраняется. Предикат освобождения S1775 сам назвал точкой расширения (§5.4) и отдельно отметил неразличение «блокер делается» и «блокер ждёт человека» в §6.1.

---

## 10. Связи с другими спеками

- S1775 - ввёл текущую строку; его решение сохраняется, меняется только предикат, названный там точкой расширения.
- S1621 - образец leaf-файла, общего для `_lib.ps1` и `preview.ps1`.
- S1860, S1846, S1697, S1714 - измеренные жертвы дефекта.
