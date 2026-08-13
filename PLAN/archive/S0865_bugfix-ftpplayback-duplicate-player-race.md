# Спецификация (compact bugfix): S0865 - FtpPlaybackHelper - гонка duplicate-player на TS-probe suspension

**Ticket:** S0865
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1 (2026-07-02, dedicated skeptic). FAMILY-WIDE: identical release->probe->assign window verified in all four protocol helpers - FtpPlaybackHelper (:46/:96/:101), SmbPlaybackHelper (:44/:110/:115), SftpPlaybackHelper (:43/:105/:110), CloudPlaybackHelper (:35/:61/:66); exoPlayer is an unguarded internal var (VideoPlayerManager.kt:211); every playVideo() spawns an untracked managerScope.launch (VideoPlayerManager.kt:620, scope :337) with NO Job storage/cancel/Mutex at any layer (PlayerMediaLoaderManager :190 cooldown is failure-only, playVideoWithResourceType :1039-1113 has no single-flight). Trigger: network .m2ts/.m2t (M2TS_TS_CANDIDATE route makes the probe window seconds-long) + swipe to next file during probe -> loser player unreachable, keeps playing audio + holds MediaCodec + pooled connection permit until process death. SAME ROOT CAUSE as S0854 (unserialized playVideo coroutine): one serialization fix (tracked Job cancel at entry or Mutex) closes both tickets and all four helpers; belt-and-braces: re-check-and-release existing exoPlayer at the assignment point after suspension.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt:101** - Duplicate-player race: network TS-probe suspension between releasePlayer() and exoPlayer assignment lets an interleaved load orphan a prepared, playing ExoPlayer (never released)
  - Evidence: FtpPlaybackHelper.kt:46 'releasePlayer()' .. :95-99 'val format = if (routeHint == NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE) { (dataSourceFactory as DataSource.Factory).detectTsFormatSuspend(ftpUri) }' .. :101 'exoPlayer = ExoPlayer.Builder(context)..'. detectTsFormatSuspend is a real suspension doing network I/O (BdTsPlaybackHelper.kt:39-63: withContext(Dispatchers.IO) { source.open(spec); source.read(..) } over FTP/SMB/SFTP/cloud - seconds on a slow link). The same release->suspend->assign window exists in SftpPlaybackHelper.kt:43/:104-110, SmbPlaybackHelper.kt:44/:108-115 and CloudPlaybackHelper.kt:35/:60-66. playVideo dispatches each request as an independent managerScope coroutine with no cancellation of the previous one (VideoPlayerManager.kt:620-683; managerScope = CoroutineScope(Dispatchers.Main + Job()) at :337), and PlayerMediaLoaderManager.playVideoWithResourceType calls videoPlayerManager.playVideo per file display with no serialization (:1049-1112). Concrete interleaving: user opens network file A ('.m2ts'/'.m2t' per NetworkPlaybackContainerHint.fromPath) -> coroutine A passes releasePlayer() and suspends in detectTsFormatSuspend -> user swipes to file B -> coroutine B's releasePlayer() finds exoPlayer == null (releases nothing), builds player PB, prepares and plays it -> coroutine A resumes and executes 'exoPlayer = PA', 'currentPlayerView?.player = PA'. PB is now unreachable and is NEVER released: it keeps playing file B's audio (double audio, no surface), holds MediaCodec decoders, its buffered data and its pooled FTP/SMB/SFTP connection (semaphore permit) until the process dies, while the stale file A wins the screen.
  - Fix hint: Track the in-flight load Job in VideoPlayerManager and cancel it at playVideo entry (or guard with a Mutex), and/or re-check-and-release any existing exoPlayer at the assignment point after suspension.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

FtpPlaybackHelper - гонка duplicate-player на TS-probe suspension. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

Во всех четырёх сетевых протокольных хелперах (`FtpPlaybackHelper`, `SmbPlaybackHelper`, `SftpPlaybackHelper`, `CloudPlaybackHelper`) между вызовом `releasePlayer()` в начале функции и присвоением нового `exoPlayer = ExoPlayer.Builder(..).build()` в конце существует окно с реальной приостановкой корутины (`detectTsFormatSuspend` - сетевой TS-probe для `M2TS_TS_CANDIDATE` маршрута, секунды на медленном канале). Если за это время пользователь свайпнул на другой файл, второй вызов `playVideo()` успевает полностью пройти свой путь (его `releasePlayer()` находит `exoPlayer == null`, т.к. первый уже обнулил поле, поэтому не освобождает ничего чужого) и присвоить/запустить свой `ExoPlayer`. Когда первая корутина возобновляется после приостановки, она безусловно перезаписывает поле `exoPlayer` СВОИМ плеером - плеер второго вызова становится недостижимым и никогда не освобождается: продолжает играть звук, удерживает `MediaCodec`-декодер и permit пула сетевых подключений до смерти процесса.

S0854 уже устранил корневую причину гонки на уровне диспетчеризации (`activeLoadJob` в `VideoPlayerManager.playVideo()` отменяет предыдущий load-job перед запуском нового) - в большинстве случаев кооперативная отмена корутины останавливает первую загрузку до того, как она дойдёт до присвоения `exoPlayer`. Но эта гарантия кооперативная: если приостановленный сетевой I/O внутри `detectTsFormatSuspend` не проверяет отмену вплоть до завершения самого блокирующего чтения, окно для гонки формально остаётся. S0865 добавляет independent belt-and-braces защиту прямо в точке присвоения - защиту, которая не полагается на своевременность кооперативной отмены.

---

## 3. Исправление

1. `VideoPlayerManager.releaseIfRacedPlayer()` (новый internal-метод, `VideoPlayerManager.kt`, рядом с `releasePlayer()`): если `exoPlayer != null` на момент вызова - логирует предупреждение и вызывает существующий `releasePlayer()` (тот же полный release-контракт: снятие listener'ов, `player.release()`, обнуление throttle-режима и pending-callback'ов), вместо ручного дублирования логики освобождения.
2. Во всех четырёх протокольных хелперах (`FtpPlaybackHelper.playFtpVideo`, `SmbPlaybackHelper.playSmbVideo`, `SftpPlaybackHelper.playSftpVideo`, `CloudPlaybackHelper.playCloudVideo`) непосредственно перед `exoPlayer = ExoPlayer.Builder(context)..build()` добавлен вызов `releaseIfRacedPlayer()` - если параллельный вызов уже успел присвоить свой `ExoPlayer` за время приостановки текущей корутины на TS-probe, он освобождается перед перезаписью, вместо того чтобы стать недостижимым.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0854 (same root cause - unserialized playVideo coroutine; S0854's `activeLoadJob` cancellation already closes the race in the common case, this ticket adds an independent defense that doesn't rely on cooperative-cancellation timing)

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard) - PASS.
- Статический ре-обзор: все 4 протокольных хелпера вызывают `releaseIfRacedPlayer()` непосредственно перед присвоением `exoPlayer`; `releaseIfRacedPlayer()` переиспользует существующий `releasePlayer()` release-контракт, не дублирует логику освобождения.
- Ручная device-проверка (BlockNeedUserTest, опционально): открыть сетевое видео `.m2ts`/`.m2t` (маршрут `M2TS_TS_CANDIDATE`, многосекундный TS-probe) на медленном/нестабильном соединении и быстро свайпнуть на следующий файл во время загрузки - ожидание: не более одного живого `ExoPlayer`, отсутствие двойного звука, отсутствие "ServiceConnection leaked"/утечки пула соединений в логах.

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `VideoPlayerManager.releaseIfRacedPlayer()` present, reuses existing `releasePlayer()` contract (:762-772) - PASS. `FtpPlaybackHelper.playFtpVideo` calls `releaseIfRacedPlayer()` immediately before `exoPlayer = ExoPlayer.Builder(..)` (:104) - PASS. `SmbPlaybackHelper.playSmbVideo` same guard (:118) - PASS. `SftpPlaybackHelper.playSftpVideo` same guard (:92), plus `buildSftpUri()` extraction keeps the function under the detekt `LongMethod` threshold - PASS. `CloudPlaybackHelper.playCloudVideo` same guard (:69) - PASS. `standard debug` Kotlin compile - PASS. detekt scoped gate (5 files) - PASS. Dev log entries present for all 5 files (S0865 @ 17:00-17:09) - PASS. FEATURES trilingual - EXEMPT (internal race-condition defense, no user-visible capability).

### Manual / on-device

- [ ] Open a network video (.m2ts/.m2t, M2TS_TS_CANDIDATE route with a multi-second TS-probe) on a slow/unstable connection and swipe to the next file mid-probe - expect at most one live ExoPlayer, no double audio, no "ServiceConnection leaked"/connection-pool leak in logs.

