---
name: mainactivity-loc-ceiling
description: MainActivity sits at the Rule-2 1500-LOC cap; fold new main-window feature wiring into its Main*Manager, don't add a fresh MainActivity field+construction
type: feedback
---

`ui/main/MainActivity.kt` is permanently pinned just under the Rule-2 1500-LOC limit (~1489-1499). Any new main-window feature whose wiring is a fresh `private lateinit var` field + a multi-line construction block in `setupViews()` will push it over.

**Why:** S0777 added an inline-audio coordinator. Wiring it as a separate MainActivity field + construction block landed MainActivity at 1508 (over cap). Folding the coordinator's *ownership* into the existing `MainStreamsPanelManager` (the panel already owns the channel taps) - MainActivity just passes it `activityBinding = binding` + the host callbacks - dropped MainActivity to ~1499 and grew the helper instead. There is no mechanical LOC gate (`measure-hotspots.ps1` is advisory only), but Rule 2 is explicit and MainActivity is a known S0002 decomposition hotspot.

**How to apply:** before wiring a main-window feature, `wc -l MainActivity.kt`. If near 1500, give ownership to the feature's existing `ui/main/helpers/Main*Manager` rather than a new MainActivity field: the manager can take `ActivityMainBinding` (to reach activity-level views like a bottom mini-control), construct the sub-helper, and implement `DefaultLifecycleObserver` for its own onStop/onDestroy (no host lifecycle overrides needed). MainActivity then contributes only the `@Inject` deps and the callback lambdas it must supply. Trim your own comments before resorting to extracting unrelated code (which is riskier scope creep). See also [[player-family-glue-mirroring]] for the separate-inline-audio-manager pattern ([[stream-radio-vs-video-player-split]]).
