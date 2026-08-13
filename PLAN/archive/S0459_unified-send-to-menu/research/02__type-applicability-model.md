# Research 02 - Modelling type-applicability on ShareTarget

**Strategic spec:** [`../../S0459_unified-send-to-menu.md`](../../S0459_unified-send-to-menu.md) §6 (item 2), ADR-3
**Status:** Resolved
**Date:** 2026-06-16
**Method:** read S0452 infra - `ShareTarget`, `ShareTargetRegistry`, `ShareTargetAvailabilityResolver`, `IsShareTargetEnabledUseCase`, `ShareTargetModule`, `ShareTargetRegistryTest`, existing `MediaType` enum.

---

## Question

How to add "applicable to current file type" to `ShareTarget` so the menu list rule becomes `enabled AND available AND applicable`, without breaking S0452 registrations or the settings group.

## Findings

- `ShareTarget` is a plain data class held in a Hilt `Set<@JvmSuppressWildcards ShareTarget>` multibinding; the registry keys it `associateBy { it.id }`.
- Existing capability dimensions are modelled as **enums** (`ShareTargetDefault`, `ShareTargetAvailability`), resolved in `ShareTargetAvailabilityResolver`.
- Device-capability availability (`isAvailable`) takes only the target - no per-file input. Type-applicability is per-invocation (depends on the current file), so it is a different axis.
- The only current construction sites are `ShareTargetRegistryTest` (named-argument construction) and future registrations - a new field **with a default** is source-compatible (the registry empty today).
- A domain taxonomy already exists: `domain/model/Models.kt` `enum class MediaType { IMAGE, VIDEO, AUDIO, GIF, TEXT, PDF, EPUB, BINARY_*, OFFICE_DOCUMENT }`.

## Options weighed

- **Predicate `(MediaType) -> Boolean`** - rejected: a lambda field breaks data-class structural equality/`hashCode`, which the `Set<ShareTarget>` multibinding relies on; also non-declarative.
- **Raw MIME-glob set (`"image/*"`)** - rejected: introduces a second matching layer and a taxonomy parallel to `MediaType`; the menu builder already has the file's `MediaType`.
- **`Set<MediaType>` (chosen)** - declarative, equality-safe, consistent with the existing enum fields, reuses the one taxonomy.

## Decision

- Add one additive field:
  - `val applicableTypes: Set<MediaType> = emptySet()` - `emptySet()` is the sentinel for **"applies to any type"** (preserves today's un-gated behaviour for any target that omits it).
- Add a pure extension (no resolver change - keep `ShareTargetAvailabilityResolver` device-only):
  - `fun ShareTarget.appliesTo(type: MediaType): Boolean = applicableTypes.isEmpty() || type in applicableTypes`
- Menu builder composes three gates: `IsShareTargetEnabledUseCase(id, settings)` AND `resolver.isAvailable(target)` AND `target.appliesTo(currentType)`.
- The **settings toggle is NOT type-gated** - a target is configured on/off globally; `applicableTypes` filters only the per-file menu list. The settings group keeps iterating `registry.all()` unchanged.

## Per-receiver applicability map

- System Share, Telegram, Email, "Open in.." → `emptySet()` (any).
- WhatsApp → any (accepts image/video/audio/doc).
- Print → `{IMAGE, GIF, PDF, TEXT, OFFICE_DOCUMENT}` (no audio/video/binary).
- Google Lens → `{IMAGE, GIF}`.
- Keep-text → `{TEXT}`.
- Keep-drawing → `{IMAGE}`.
- Instagram → `{IMAGE, VIDEO, GIF}` (Instagram intents accept image/video only - see research 04).

## Multi-select

- Selection type for gating = the representative type of the selection (first file for single-only receivers per ADR-4; for batch receivers, gate on "any selected file applies" - tactical detail).

## Spec impact / blast radius

- Additive field, default `emptySet()` → `ShareTargetRegistryTest` and settings group unaffected; no Room/schema change; risk row in §7 ("S0452 model extension breaks registrations") mitigated to Low.
- Foundation phase 01 adds the field + `appliesTo`; each receiver-registration phase declares its `applicableTypes`; the menu-builder phase wires the third gate.
