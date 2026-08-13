# Research 01 - Cancel color tokens + red semantics (strategic §6.1, §6.2)

**Status:** Resolved (quiz 2026-06-25 + workflow research)

## Decision

Cancel = soft-pink **tonal fill**, a self-contained profile. Saturated red stays reserved for `DialogDestructive` only (ADR-1). Two distinct tones, never one shared red.

## Color tokens (contrast-verified, WCAG 2.x)

| Token | Mode | Hex (ARGB) | Role |
|-------|------|-----------|------|
| `cancel_button_bg` | day | `#FFF4D9DE` | tonal fill (soft rose) |
| `cancel_button_on` | day | `#FF5A1F2A` | text on fill (deep wine) |
| `cancel_button_bg` | night | `#FF5C3A43` | tonal fill (muted mauve) |
| `cancel_button_on` | night | `#FFFFDCE4` | text on fill (pale pink) |

- Text-on-bg contrast: day **9.51:1**, night **7.76:1** - both clear AA (4.5) and AAA (7.0).
- CIEDE2000 distance from destructive red (`delete_button` day `#D32F2F` / night `#EF5350`): day **40.7**, night **33.3**. JND ~2.3, so the cancel fill is an unmistakably different colour from delete.
- Derived from M3 baseline error-container (`#F9DEDC`/`#410E0B` light, `#8C1D18`/`#F9DEDC` dark), nudged toward rose and desaturated so the night fill does not collide with the saturated night delete red.

## Why this resolves the conflict

The strategic conflict (owner wanted "red Cancel", existing S0538/S0611 reserves red for destructive) is resolved by a pink tonal cancel that honours "розовая" while keeping "red = delete" intact. Confirm green, cancel pink, destructive red read as three separable affordances.
