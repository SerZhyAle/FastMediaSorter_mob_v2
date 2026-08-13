# Research 01 - Landscape density container mechanism

Resolves strategic §6 item 1 (ADR-2 mechanism).

## Question

Which layout primitive packs settings controls into landscape rows (up to 4), left-aligned, with mixed cell widths (compact toggle vs wider value field)?

## Candidates

- Weighted horizontal `LinearLayout` rows - the existing house pattern in `res/layout-land/fragment_settings_*.xml`.
- `androidx.gridlayout.widget.GridLayout` - `columnCount` + `layout_columnWeight` + `layout_columnSpan`.
- Custom `ViewGroup` auto-packing by a `minColumnWidth`.

## Findings

- The landscape settings fragments already use weighted horizontal `LinearLayout` rows for two-column pairing (general SMB|FTP, images pairs, thumbnail-preload|wifi-only). Pattern is proven in these exact files and known to the codebase.
- Owner composes groupings control-by-control (Language + Color scheme together, All Files keeps a full row, SMB|FTP together). That is explicit per-row composition, not content-agnostic auto-flow - `GridLayout` auto-wrap brings no benefit.
- Scope is landscape-only; width-adaptive reflow (4 → 2 on narrower widths) is not required, which removes `GridLayout`/custom-container's main advantage.
- `GridLayout` weighted columns + spans across nine complex fragments raises risk (ScrollView interaction, per-row weight bookkeeping) with no outcome gain.

## Decision

Use curated weighted horizontal `LinearLayout` rows (house pattern), scaled to up to 4 children per row for toggles and 2 per row for value fields. Left-packed by default, no horizontal centering. Mixed widths handled by per-child `layout_weight`.

`GridLayout` and custom container evaluated and deferred - reconsider only if a future ticket unifies portrait + landscape into one width-adaptive container (strategic §5.3, out of scope here).

## Note to owner

Owner picked direction "B (adaptive grid)" at the discussion stage. Deeper research shows the concrete requirements (curated groupings, landscape-only, left-aligned, up to 4) are delivered identically by the house weighted-row pattern at far lower risk. The General pilot screenshots demonstrate the outcome for final judgment; if cross-row column alignment turns out to be wanted, revisit GridLayout then.
