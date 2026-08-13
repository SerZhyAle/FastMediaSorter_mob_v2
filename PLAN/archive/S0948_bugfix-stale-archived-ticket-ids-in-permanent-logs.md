# Спецификация (compact bugfix): S0948 - Архивные Sxxxx-теги остаются в постоянных логах, гейт их не ловит

**Ticket:** S0948
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-05
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05

**Текст:**

> Stale Archived-ticket ids (S0151, S0166) remain baked into permanent (non-BlockNeedUserTest) Timber log messages across production code, and the ticket-log hygiene gate (scripts/quality/assert-no-ticket-logs.ps1) fails to catch them because its regex only matches when "Timber.d(/.i(/.w(/.e(" and the "Sxxxx" id appear on the SAME source line - it misses the common multi-line call style where the format string sits on the next line.
>
> Evidence (found while triaging remote log archive for an Instagram-download complaint, unrelated to that investigation):
>
> 1. app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt:291 - Timber.d(\n "S0151-diag: host=%s strategy=%s sessionApplied=%s outcome=%s", ...) - S0151 (instagram-threads-link-extraction-and-auth) has been Archived since 2026-05-11.
> 2. Same file, lines 129/309/329/385/437/451/468/655 - "[S0166] ..." bracket-tagged messages; also AuthSessionRepositoryImpl.kt:120, WebViewAuthDialogFragment.kt:167/257, ReceiveShareActivity.kt:305 - S0166 (link-receive-and-auth-storage) has been Archived since 2026-05-12.
> 3. Confirmed these fire in production logs: logs/fastmediasorter_20260704_055031.log, fastmediasorter_20260704_182935.log, fastmediasorter_20260705_002529.log all show "S0151-diag: ..." (D level) and "[S0166] ..." (I level) lines firing on every Instagram/social link-download attempt.
> 4. Ran scripts/quality/assert-no-ticket-logs.ps1 directly - it reports "actual: 0" findings, i.e. it does NOT catch these. Root cause: its per-line regex `Timber\.(?<level>[iwed])\(...S\d{4}...` requires the ticket id substring to appear on the exact same physical line as the `Timber.x(` call opener; the actual call sites split the opener and the format-string literal across two lines, so the id never appears on a line that also contains "Timber.d(" etc.
>
> Two distinct but related defects to fix:
> - (a) Remove the stale Sxxxx references from these permanent log messages (S0151/S0166 both long Archived, well past BlockNeedUserTest).
> - (b) Fix assert-no-ticket-logs.ps1 to detect ticket ids on any line within a Timber.* call's argument list, not just the opener line, so this class of stale tag stops slipping through the gate silently.
>
> Not urgent, not part of the current Instagram-download investigation - parking per CLAUDE.md §3.1.

---

## 1. Проблема / симптом

Постоянные (не `BlockNeedUserTest`) лог-сообщения содержат id давно архивных тикетов `S0151` и `S0166`, нарушая инвариант CLAUDE.md §2 ("Sxxxx в `.kt` тогда и только тогда, когда спека в `BlockNeedUserTest`"). Теги реально стреляют в каждой сессии с Instagram/social link-download (см. `logs/fastmediasorter_20260704_055031.log`, `fastmediasorter_20260704_182935.log`, `fastmediasorter_20260705_002529.log`). Механический гейт `scripts/quality/assert-no-ticket-logs.ps1` их не видит - `actual: 0` при прогоне - из-за построчного regex, требующего id на той же физической строке, что и `Timber.x(`.

Затронутые файлы (минимум):
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` (S0151-diag: строка 291; [S0166]: строки 129/309/329/385/437/451/468/655)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt:120`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt:167,257`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt:305`

(Комментарии со ссылками на S0166 как на объяснение "почему так сделано" - не лог-текст, не подпадают под инвариант, трогать не нужно.)

---

## 2. Корневая причина

Две независимые причины:

- **(a) Лог-текст.** Id архивных тикетов вплавлены прямо в постоянные `Timber.*`-сообщения. Зачистка при архивации не выполнялась: `close.ps1`/`archive.ps1` снимает debug-`Timber.d("Sxxxx:")` probe при выходе из `BlockNeedUserTest`, но не трогает id внутри произвольного лог-текста (`Timber.i`/`w`/`e` и не-probe `Timber.d`).
- **(b) Слепое пятно гейта.** `assert-no-ticket-logs.ps1` сканировал построчно (`[^\r\n]*`), поэтому многострочный вызов `Timber.d(\n  "Sxxxx: ..")` не ловился - id оказывался на строке без `Timber.x(`. Гейт рапортовал `actual: 0` при реально существующих нарушениях.

---

## 3. Исправление

- **(a) Лог-текст.** Убраны id из постоянных сообщений, диагностический текст и семантические категории сохранены:
  - Исходная находка S0151/S0166: `[S0166] X` -> `X`; `S0151-diag: X` -> `link-diag: X`. Файлы: `LinkAutoDownloadCoordinator.kt`, `ReceiveShareActivity.kt`, `LinkAutoDownloadResultPresenter.kt`, `WebViewAuthDialogFragment.kt`, `AuthSessionRepositoryImpl.kt`.
  - Дополнительно вскрыто починенным гейтом (тот же класс, все тикеты Archived): `S0139: X` -> `X`; `S0211 cleanup: X` -> `cleanup: X`; `S0022-CAM: X` -> `CAM: X` в `Timber.i`/`w` (`NetworkCredentialsRepositoryImpl.kt`, `BackfillSmbCredentialShareNameUseCase.kt`, `DedupAuthAccountsUseCase.kt`, `BrowseActivity.kt`, `BrowseCameraCaptureManager.kt`).
  - Три протухших `Timber.d("Sxxxx:")` probe архивных тикетов удалены целиком (S0765/S0640/S0671: `CameraCaptureSessionManager.kt`, `PlayerMediaFilesLoader.kt`, `ScreenCaptureConsentActivity.kt` + чистка ставшего лишним импорта `Timber`).
- **(b) Гейт.** `assert-no-ticket-logs.ps1` переписан со построчного скана на пофайловый span-скан: каждый `Timber.<level>(` разворачивается до парной `)` (учёт строковых литералов, чтобы скобки внутри строк не сбивали глубину); внутри вызова `//` и `/* */` комментарии бланкятся (чтобы `// Sxxxx` rationale-комментарий между аргументами не давал false-positive), а строковые литералы сохраняются; затем свободностоящий `Sxxxx` ищется во всём тексте аргументов независимо от переносов строк. Probe-форма и построчная фильтрация комментариев на строке открытия сохранены.

Комментарии/KDoc со ссылками на тикеты ("почему так") - не лог-текст, не тронуты.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none (S0151/S0166 и вскрытые S0139/S0211/S0022/S0436/S0765/S0640/S0671 - все Archived, независимы от текущей работы)

---

## 4. Проверка

- `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1 -Gate` -> exit 0, `actual: 0` (после (a)+(b)).
- Доказательство (b): починенный гейт до чистки (a) ловил 14 ранее скрытых нарушений (в т.ч. multi-line); один был false-positive от `// Sxxxx` комментария внутри вызова - устранён бланкингом комментариев; после чистки (a) - 0.
- Компиляция: `.\a.ps1 fk` (standard) - затронуты `src/main` + `src/screenCapture`; probe-удаления не оставляют висячих ссылок (импорт `Timber` в `ScreenCaptureConsentActivity` убран).
- Post-change `ticket-log-audit` (тот же гейт) зелёный на всём дереве.
