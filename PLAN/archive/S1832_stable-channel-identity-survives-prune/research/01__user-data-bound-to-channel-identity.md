# 01 - What user-authored data is bound to a channel, and how

Research artifact for strategic §6 item 1. Working tree, 2026-08-20.

## Inventory

| Data kind | Where it lives | Keyed by | Survives delete-then-return? | Survives a cosmetic address edit? |
|---|---|---|---|---|
| Favorite channel (S0783) | `favorites` | channel `url`, byte-exact | **yes** | no |
| Learned quality rung (S1511) | `stream_quality_memory` | `StreamUrlNormalizer.normalize(url)` | **yes** | **yes** |
| Pin + position (S0756/S0938) | `stream_sources.pinned` / `.sortIndex`, on the row itself | row `id` | no - dies with the row | no |
| Last play outcome (S1502) | `stream_play_outcome.streamId` | row `id` | no - purged with the row | no |
| Desktop cell (launcher) | `launcher_cells.target` = `stream:<id>` | row `id` | no - resolves to null | no |

Sources: `domain/usecase/FavoritesUseCase.kt:63-102`, `data/local/db/StreamQualityMemoryEntity.kt:18-27`,
`data/repository/StreamSourceRepository.kt:57-74` and `:96-106`, `data/local/db/StreamPlayOutcomeEntity.kt:15-22`,
`domain/model/launcher/LauncherCellCommand.kt:30` and `:168`,
`domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt:99-108`,
`domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt:333-334`.

## The finding that decides the design

Three of the five data kinds are already url-keyed, and both of the ones that survive a prune are the
url-keyed ones. The two that do not survive are exactly the two keyed by the row id, plus the desktop
cell, which is keyed by the row id too. The codebase has therefore already answered this ticket's
question twice, in two separate shipped tickets, and both times the answer was the same: file user data
under the address, not under the row.

`StreamQualityMemoryEntity`'s own KDoc states the reason in the ticket's own words - the row id "is
reissued whenever the same channel is removed and re-imported, which the stream catalog does regularly;
an id-keyed record would silently reset exactly when it is worth the most" (S1511, strategic ADR-5).

## Consequences

1. The work is not "make the id stable". It is "move the three id-keyed kinds onto the key the other two
   already use", and then normalize that key so a cosmetic address edit stops breaking it.
2. Favorites survive a prune but not an address edit, because they compare the address byte-exactly.
   Strategic goal 2 covers them, so they join the same normalized key.
3. `stream_sources.id` never has to change. Nothing user-authored needs to be filed under it once the
   three stragglers move, which removes a primary-key rewrite - and its fan-out into
   `stream_play_outcome` and `launcher_cells` - from the ticket entirely.

## Correction to strategic §6 item 1

The §6.1 answer enumerates "pin with its position, membership in collections, playback history". Two
corrections:

- **"Membership in collections" has no implementation under that name.** The nearest shipped feature is
  the per-channel favorite (S0783), which is what a user would call a collection, and it is already
  safe against the prune. Read as favorites, the item is satisfied by normalizing the key it already
  uses rather than by new storage.
- **The desktop cell is missing from the enumeration.** A cell the user placed on the launcher grid
  binds `stream:<id>` and silently stops resolving after a prune-and-return cycle. It is user-authored
  data bound to a vanished id, so strategic §11 criterion 3 already covers it even though §6.1 does not
  name it.

## Correction to strategic §4

§4 states that the play-outcome row "is not deleted at all and stays attached to an id that no longer
exists (S1826)". S1826 is `Verified` and shipped the opposite behaviour: `mergeCatalog` now calls
`purgeOrphanedPlayOutcomes()` unconditionally in the same transaction as the prune
(`StreamSourceRepository.kt:200-204`), so the row is deleted, not stranded. The user-visible outcome is
the same - the history is gone - but the mechanism named in §4 is no longer the one in the tree, and the
plan has to amend a purge rather than add one.

## Correction to strategic §3.2 (Wear OS)

§3.2 states the catalog is synchronized to the watch, so identity must match on both sides. There is no
such transport: `wear/.../ImportWearStreamCatalogUseCase.kt:24-99` downloads the same public zip itself
and mints its own `UUID.randomUUID()` per row, and `WearStreamChannel` persists no pin, no position and
no history. Nothing on the watch is at risk today, and matching identity there would mean running the
identical derivation on both platforms, not transporting an id.
