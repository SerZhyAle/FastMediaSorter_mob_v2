# Draft: S0450 - Orphaned screenCapturePlay source set not mounted

**Ticket:** S0450
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-15

> **Scope:** STRATEGIC draft skeleton. Captured out-of-scope finding during S0425 research. Needs own research + triage.

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-15 во время исследования S0425 (read-only research agent).

**Симптом:**

Source set `app_v2/src/screenCapturePlay/` существует на диске и содержит готовый код:
- `ScreenGestureOverlayControllerImpl` (Play-only путь через MediaProjection, без accessibility)
- `ScreenCaptureModule` (Hilt DI wiring, `@Binds @IntoSet`)

Но в `app_v2/build.gradle.kts` (район L547-610) этот source set НЕ примонтирован ни в один флавор (`standard`, `photos`, `vr`, `lite`, `legacy`). Общая машинерия `screenCapture` монтируется только в `noLegal`.

**Контекст:**

Похоже на незавершённую подготовительную работу для S0418 (перенос краевого жеста-скриншота в standard). S0418 сейчас в статусе `Archived` со статус-нотой, описывающей рабочий тест на standard-сборке через MediaProjection - то есть либо S0418 был реализован другим путём (минуя `screenCapturePlay`), либо заброшен. Нужно установить:

1. Был ли S0418 реализован, и если да - каким source set/механизмом (раз не `screenCapturePlay`)?
2. `screenCapturePlay` - это мёртвый код (удалить) или несмонтированный задел (примонтировать)?

**Доказательства:**

- `app_v2/build.gradle.kts:547-610` - sourceSets без `screenCapturePlay`.
- `app_v2/src/screenCapturePlay/` - два `.kt` файла на диске.
- S0418 status = `Archived`.

**Вложения:** нет.

## 1. Действие

Триаж: прочитать резолюцию S0418, затем либо удалить orphaned source set (dead-weight hygiene, CLAUDE.md Rule 20), либо примонтировать его в целевой флавор.

## 2. Резолюция (2026-06-16)

**Решение: удалить.** `screenCapturePlay` - мёртвый код, не задел.

Цепочка установленных фактов:

- S0418 (Archived) реализовал Play-путь захвата (MediaProjection-only контроллер) и монтировал `screenCapture` + `screenCapturePlay` в `standard`/`photos`; on-device тест (S0418 §13) подтвердил рабочий путь на standard.
- S0423 (release scope) откатил всю фичу screencapture из store-флаворов из-за Play-review-риска (`SPECIAL_USE`/`SYSTEM_ALERT_WINDOW`). Сейчас `screenCapture` монтируется только в `noLegal`, а `screenCapturePlay` - никуда (см. комментарии `app_v2/build.gradle.kts` L558-560, L601-602).
- `screenCapturePlay` зависит от `OverlayHostService` из `screenCapture` (noLegal-only), а `noLegal` имеет собственный `ScreenCaptureModule` + a11y-контроллер. Значит source set не компилируется ни в одном флаворе - некомпилируемый мёртвый код.
- Ни один открытый тикет его не использует: S0425 - noLegal-only и явно паркует его как orphan (это и есть данный S0450).
- История git (commits e6a4a7a8, 01276d3d) сохраняет код для возможной будущей Play-фазы; восстановление дешёвое.

Выполнено:

- Удалён source set `app_v2/src/screenCapturePlay/` (оба `.kt`).
- Удалён source root `screenCapturePlay` из `dev/CATALOG/scripts/scan.ps1`.

Комментарии S0423 в `build.gradle.kts` («screencapture stays noLegal-only for now») остаются актуальными - фича действительно остаётся noLegal-only; они не ссылаются на `screenCapturePlay` и правки не требуют.
