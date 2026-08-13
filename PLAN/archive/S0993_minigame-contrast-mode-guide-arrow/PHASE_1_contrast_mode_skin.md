# PHASE 1 - Contrast mode skin plumbing

**Ticket:** S0993
Goal: register the third `CONTRAST` visual mode end to end (enum, theme resolver, picker label, help fallback, localized name) without touching game logic.

## Steps

1. `domain/game/GameModels.kt` - add `CONTRAST` as a third entry to `enum class GameMode` (after `KRYVAVITSA`). Leave `fromStorageName` untouched (already falls back to `CLASSIC` for unknown names, and now resolves `CONTRAST`).
   - Verification: `GameMode.entries.size == 3`; `GameMode.fromStorageName("CONTRAST") == GameMode.CONTRAST`.

2. `ui/game/helpers/GameBoardTheme.kt` - extend the theme with presentation attributes (keep constructor private, add fields with defaults so CLASSIC/KRYVAVITSA branches stay valid):
   - `val filledActors: Boolean = false` - actor drawn as a solid cell fill instead of primitive/drawable.
   - `val filledExitColor: Int? = null` - when non-null, exit cell is a solid fill of this colour instead of the framed square / door drawable.
   - `val steppedMove: Boolean = false` - move animation uses discrete stepped frames + source ghost (Phase 2).
   - Add contrast colour constants (inherit existing hues at full value): floor `#FFFFFFFF`, wall `#FF000000`, exit `#FF2E7D32`.
   - Add `GameMode.CONTRAST` branch in `forMode`: `floorColor` = contrast floor, `wallColor` = contrast wall, `stomp = false`, all `*Drawable = null` (actors are solid cubes, not sprites), `filledActors = true`, `filledExitColor = contrast exit`, `steppedMove = true`.
   - Verification: `GameBoardTheme.forMode(ctx, GameMode.CONTRAST).filledActors` is true and `filledExitColor != null`.

3. `ui/game/helpers/GameModeMenuManager.kt` - add `GameMode.CONTRAST -> R.string.game_mode_contrast` to `labelRes` `when`. Picker itself iterates `GameMode.entries`, so the new item appears automatically.
   - Verification: `when` over `GameMode` is exhaustive (compiles without else).

4. `ui/game/GameHelpActivity.kt` - the binary `kryvavitsa = mode == GameMode.KRYVAVITSA` sends CONTRAST down the `else` (classic) branch. That is the intended default (the coloured-ball legend matches solid cubes). Add a short EN-only `// S0993:` comment noting CONTRAST reuses the classic help intentionally. No behavioural change.
   - Verification: help screen opened in CONTRAST shows the classic (coloured) legend; compiles.

5. Strings - add `game_mode_contrast` across EN/RU/UK `strings_game.xml`:
   - EN (`values/strings_game.xml`): `<string name="game_mode_contrast">Contrast</string>` next to `game_mode_kryvavitsa`.
   - RU (`values-ru/strings_game.xml`): `На контрасте`.
   - UK (`values-uk/strings_game.xml`): `На контрасті`.
   - Use `scripts/utils/set-android-string.ps1 -Action add -Key game_mode_contrast -En "Contrast" -Ru "На контрасте" -Uk "На контрасті"` (parity-enforced across the three).
   - Verification: `scripts/check_strings_localized.ps1 -KeyPrefix game_mode_contrast` exits 0.

## Done when

- Standard debug compiles (`.\a.ps1 fk`).
- CONTRAST appears in the mode picker with a localized name and persists across restarts.
