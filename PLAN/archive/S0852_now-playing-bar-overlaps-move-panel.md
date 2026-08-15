# S0852 - Mini now-playing bar overlaps the "Move to.." panel after re-entering the player

**Ticket:** S0852
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-01
**Tier:** unset

<!-- discovered by /log-reader - 2026-07-01 (user-reported, corroborated by session logs) -->

## 0. Raw capture

User report (RU, verbatim): "если выйти из плеера и зайти в плеер - вниз остаётся панель 'что сейчас играет', которая накрывает собой 'Переместить в..'".

Symptom: with background audio running, exit the player then re-open it (viewing a photo). The mini "Now Playing" bar stays at the bottom and covers / pushes out the destination ("Переместить в.." / Copy-Move) panel, so the move buttons become unusable.

## 1. Evidence (root cause confirmed)

- Portrait ([activity_player_unified.xml:322](app_v2/src/main/res/layout/activity_player_unified.xml#L322)): root is a vertical `LinearLayout`; `mediaContentArea` has `height=0dp weight=1`, then `miniNowPlayingBar`, draw toolbar, `bottomPanelsContainer` stack below it. Overlap is structurally impossible - the bar sits in its own vertical slot ABOVE Copy/Move.
- Landscape ([layout-land/activity_player_unified.xml:233](app_v2/src/main/res/layout-land/activity_player_unified.xml#L233)): root is a `ConstraintLayout`. `miniNowPlayingBar` was constrained `bottom_toBottomOf=parent`; `bottomPanelsContainer` ([layout-land/player_bottom_panels_container_content.xml:3](app_v2/src/main/res/layout-land/player_bottom_panels_container_content.xml#L3)) is ALSO `bottom_toBottomOf=parent`. Both bands claim the same bottom strip.
- `view_mini_now_playing.xml` root carries `elevation=4dp`, so the 56dp bar draws OVER the Copy/Move band despite earlier file order - exactly "накрывает собой 'Переместить в..'". The covered strip is the bottom of the panels: the last destination buttons become untappable.
- `NowPlayingManager.updateBarVisibility` ([NowPlayingManager.kt:129](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt#L129)) shows the bar for photos while `AudioPlaybackService.isRunning` - the re-entry path from the user report (3x `Initializing mini now playing bar listeners` in `fastmediasorter_20260630_100236.log`). The manager logic itself is correct; the bug is the landscape constraint.
- Draw toolbar is NOT a competitor in landscape: it is a right-edge vertical rail ([layout-land/activity_player_unified.xml:297](app_v2/src/main/res/layout-land/activity_player_unified.xml#L297)), so the bottom band is contested only by the bar and Copy/Move.
- Insets are fine: `bottomPanelsContainer` consumes `systemBars.bottom` via `PlayerControlsSetupManager.setupToolbar` ([PlayerControlsSetupManager.kt:566](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt#L566)); no double consumption involved.

## 2. Decisions (answered by existing architecture, no owner fork)

- Precedence: STACK, bar above Copy/Move. Portrait already defines this design (bar and panels coexist, bar above), and S0368 fixed Copy/Move as the bottom-most band with other bands stacked above it. Hiding the bar would drop background-audio controls exactly in the sort flow where they matter, and would diverge from portrait.
- Overlap mechanism: z-order (both bottom-anchored + bar elevation), not an inset problem and not push-out. Confirmed statically; portrait unaffected.

## 3. Fix (implemented)

- `layout-land/activity_player_unified.xml`: re-anchor `miniNowPlayingBar` include to `app:layout_constraintBottom_toTopOf="@+id/bottomPanelsContainer"` (portrait parity). When the container is gone/empty it collapses at the parent bottom, so the bar still rests at the screen edge with no panels shown; bonus - the bar no longer sits under the bottom nav/gesture inset because the container carries the `systemBars.bottom` padding (Rule 17).
- Portrait layout intentionally unchanged (Rule 11: counterpart checked - already stacks correctly).
- No Kotlin behavior change; `NowPlayingManager` visibility rules untouched.

## 4. User test (BlockNeedUserTest)

- Start audio playback, exit the player, re-open a photo in sort mode (Move/Copy panels visible), rotate to landscape: the "Now Playing" bar must sit ABOVE the "Move to.." panel; every destination button stays tappable.
- Same flow in portrait: unchanged (bar between image and panels).
- Hide panels (fullscreen photo, no sort panels): bar rests at the bottom edge, above the gesture/nav inset.
- Debug probe: `Timber.d("S0852: ..")` fires in logcat when the bar becomes visible.

## Related

- S0107 / S0368 (bottom panel ordering: draw band above Copy/Move, both consume `systemBars.bottom`). S0368's land comment documents the ConstraintLayout include-pinning gotcha this fix follows.
