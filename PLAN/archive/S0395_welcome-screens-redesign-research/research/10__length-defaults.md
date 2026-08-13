# 10 - Onboarding Length and Defaults Strategy

Strategic item: S0395 §6.10. Phase: 04, step 04.3.

## Question

Is a six-decision-page flow acceptable friction, what defaults apply when the user skips, and can any page default-and-hide instead of asking?

## Sources

- https://www.appcues.com/blog/mobile-onboarding-best-practices - completion drops ~15% per onboarding screen beyond five; keep mandatory onboarding to 3-5 steps
- https://userpilot.com/blog/app-onboarding-best-practices/ - healthy completion 60-80%; >20-30% drop at a single screen is a red flag; bounded "step 3 of 5" flows outperform unbounded
- https://adapty.io/blog/how-to-fix-your-onboarding-flow/ - 2-3 preference questions improve long-term engagement; some utility apps win with longer flows
- https://www.lowcode.agency/blog/mobile-onboarding-best-practices - time-to-first-core-action under 60 s as the guiding metric
- `research/01__current-flow-inventory.md` (current flow shape, Skip semantics), artifacts 02-07 (per-page defaults)

## Findings

- Best-practice envelope: 3-5 screens for mandatory onboarding; every screen beyond five costs ~15% completion; bounded indicators help; a few preference questions are net-positive for engagement - the target flow is preference questions, not marketing, which is the favorable case.
- The current flow is ALREADY 6 pages (5 fixed + conditional default-player) plus a permissions overlay - but four pages are swipe-through info. The target replaces info pages with decision pages of equal count: the page count does not grow; the decision density does.
- The decisive mitigation is that every target page has a safe default and requires NO interaction: language (current locale), theme (AUTO - follows device), profile (detected recommendation, pre-selected), networks (all supported sources ON - S0391 upgrade-parity default), functionality (post-preset values; OCR/translation OFF as today), permissions (grant-all button or proceed without - existing pattern), default app (no-op page). A user can swipe/Next through all six pages in seconds and land in a working app - identical effort to today's flow.
- "Skip all" exists today (Skip routes via default-player page then completes) and must survive. Per-page skip = simply moving on; nothing on a page blocks Next.
- Skip defaults per page = the pre-populated values above; the one semantics fix needed: an explicitly tapped choice must survive Skip (today Skip discards an explicit profile pick - artifact 03).
- Default-and-hide candidates: none of the six pages defaults-and-hides for first-run (each carries a real preference); but pages already collapse per flavor/device (artifact 09), and page 2 is absent pre-S0391 - the flow is 5 pages at launch, 6 only after S0391, exactly at the best-practice boundary with the favorable "preference questions" content type.
- Downloads do not extend the flow: enqueue-and-continue (artifact 07) keeps time-to-app unchanged.

## Options

- Indicator copy: dots only (today) vs "step X of N" caption - bounded-progress evidence favors adding the caption; cheap.
- Functionality page position of the Extensions button: inline on page vs only in settings - keeping it on the page adds discoverability at zero decision cost (it is a button, not a question).
- photos/lite thin pages: keep uniform page vs fold (artifact 09 option) - friction argument slightly favors folding in the thinnest flavor, uniformity favors keeping; low stakes either way.

## Conclusion

Six decision pages are acceptable HERE because every page is skippable with safe defaults, the count equals the current flow, the content type (preferences) is the engagement-positive kind, and at launch (pre-S0391) the flow is five pages - inside the 3-5 envelope. Adopt: per-page safe defaults as listed, swipe-through-ability as an acceptance criterion ("zero-interaction run completes in under a minute and yields a working app"), "step X of N" indicator caption, Skip preserving explicit picks, and no growth beyond six pages without removing one (extension budget rule for §5.3 of the strategic spec).

## Impact on recommendation

- SYNTHESIS adopts "every page skippable with safe defaults" and the zero-interaction acceptance criterion as non-negotiable properties of the recommended structure.
- Dev-ticket split: indicator caption + Skip-semantics fix ride the skeleton ticket.
- Owner decision: none required beyond what other artifacts already raise - length is defensible as designed.
