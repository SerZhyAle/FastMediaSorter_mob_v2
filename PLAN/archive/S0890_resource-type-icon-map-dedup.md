# S0890 - Dedup ResourceType -> icon mapping onto ResourceTypeIconMap

**Ticket:** S0890
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-02
**Tier:** 2 - Small (ad-hoc)

<!-- discovered by /spec-all S0815 - 2026-07-02 (code-audit spinoff) -->

## 0. Raw capture (inbox)

**Captured:** 2026-07-02, during S0815 icon-registry research (research/01).

The same `ResourceType -> @DrawableRes` mapping (LOCAL / SMB / SFTP / FTP / CLOUD / HTTP_STREAM / RTSP_STREAM) is hand-duplicated in multiple independent places with no shared source - silent-drift risk if one is changed without the others:

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ConnectionBadgeMapper.kt` (~:33-45)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconComposer.kt` (~:79-97)

S0815 phase 1 already extracted the mapping that lived in `ResolveAppLaunchPanelTilesUseCase.resourceIconRes` into a single `object ResourceTypeIconMap { val entries: Map<ResourceType,Int>; fun iconFor(type) }` (`core/panel/ResourceTypeIconMap.kt`). That made the use-case delegate to one source; the remaining two copies above should be pointed at the same `ResourceTypeIconMap` so there is exactly one source of truth.

## 1. Problem / symptom

Three (now two, after S0815) independent copies of a `ResourceType -> icon` table. Changing an icon in one place silently diverges from the others (e.g. a new resource type, or an icon swap). Internal code-quality / maintainability concern - not user-visible.

## 2. Proposed direction

- Point `ConnectionBadgeMapper` and `ResourceIconComposer` at `ResourceTypeIconMap` (reuse `iconFor`/`entries`) instead of their own `when`/map.
- Verify each mapping is byte-equivalent before collapsing (some may intentionally differ, e.g. a badge variant vs a tile icon - confirm, do not blindly merge).
- If a call site legitimately needs a different icon set (badge vs tile), keep it separate but document why (a comment naming the divergence), so it is not mistaken for drift.

## 3. Verification

- `.\a.ps1 fk` green; the App Launch Panel tiles, connection badges, and composed resource icons render the same drawable per `ResourceType` as before (no visual change).

## Related

- S0815 (icon inventory - extracted `ResourceTypeIconMap`, the target of this dedup).

## Last Audit

**Date:** 2026-07-03
**Verdict:** Verified

Scope grew vs the spec draft: grep found 5 live copies, not 2. Dispositions:

- Exact copies -> delegate to `ResourceTypeIconMap.iconFor`: `AppShortcutsManager.iconForType`, `ResourcePickerDialogFragment.resourceIconRes`.
- `ResourceLaunchWidgetProvider.resolveIcon` -> delegate with null-fallback (unparseable type keeps `ic_resource_local`). Behaviour alignment: stream-typed widgets now show `ic_cast` (the old `else` silently mapped them to the local-folder icon) - documented at the call site.
- Legitimate divergences kept and documented (per spec direction): `ConnectionBadgeMapper` (LOCAL -> null badge; CLOUD -> provider glyph), `ResourceIconComposer.legacyIconFor` (CLOUD -> provider glyph); both now delegate every other type to the shared map.
- True duplicate found during equivalence check: the CLOUD provider table was verbatim-identical in badge mapper and composer -> extracted to new `ui/icon/CloudProviderIconMap` (falls back to the map's CLOUD entry).
- NOT copies (skipped): `MainResourceTabsManager` (ResourceTab domain, grouped tabs), `WelcomeActivity` (feature cards, not a type map).

Validation:

- `.\a.ps1 fk` - expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (30s).
- `post-change -ScopeToFile` (Kotlin) - PASS: neuroslop/listener/flavor/pm delta gates 0 new; detekt scoped - 0 findings among changed files; catalog synced.
- Delta gates over the 5 edited files (multi-file `-ChangedFiles`) - expected: 0 growth | actual: 0 (neuroslop exit 0, listener exit 0).
- Raw `ResourceType -> R.drawable` tables remaining in src/main: only `ResourceTypeIconMap` itself + documented divergence sites.
