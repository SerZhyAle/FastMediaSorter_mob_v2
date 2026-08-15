# 04 - Page 2: Network Toggles over S0391

Strategic item: S0395 §6.4. Phase: 02, step 02.3.

## Question

In what form do the S0391 remote-source toggles surface on an onboarding page, and how does the page-2 dev ticket depend on S0391's implementation?

## Sources

- `PLAN/S0391_remote-source-runtime-toggles.md` (read in full; status Approved, no tactical folder, NOT implemented)
- `research/01__current-flow-inventory.md` (flavor gate table)
- `app_v2/build.gradle.kts` (SUPPORT_CLOUD per flavor)

## Findings

- S0391 introduces six per-source boolean toggles: SMB, SFTP, FTP (network protocols) and Google Drive, OneDrive, Dropbox (cloud providers).
- Architecture: a single runtime availability gate - source is available only when (build supports it) AND (user toggle on). Toggle state lives in the shared user-settings layer, modeled on the existing background-sync switch. No BuildConfig reads in shared code.
- Default is ON for every build-supported source - an upgrade changes nothing without user action (S0391 §2.5, §11.5).
- Settings UI (S0391 pillar 4) groups toggles by network protocols vs cloud providers; a toggle is visible only when the source is build-supported.
- S0391 open items (fate of already-added resources, cancelling in-flight sync, lite cloud-block presentation) are deferred to S0391's own `/ui-clarify` - the onboarding page inherits whatever S0391 decides; S0395 must not fork those decisions.
- Flavor surface: SUPPORT_CLOUD is false only in lite → lite's page 2 would carry only the three protocol toggles; photos/legacy/standard/noLegal carry all six.
- Nothing of S0391 exists in code today: no settings keys, no gate, no settings screen. Every onboarding rendering of these toggles consumes S0391 artifacts.

## Options

Onboarding granularity for "what kinds of networks do you plan to use":

- A. Six individual toggles - mirrors settings 1:1; max control; six rows plus copy is heavy for a first-run page, and protocol names (SMB/SFTP/FTP) mean little to non-technical users at install time.
- B. Two group toggles - "Network folders (SMB/SFTP/FTP)" and "Cloud drives (Google Drive/OneDrive/Dropbox)" - each bulk-writes its member per-source toggles; one line of helper copy points to Settings for per-source fine-tuning. Two decisions, honest naming, fits any form factor.
- C. One umbrella question ("use online sources?") - too coarse: contradicts S0391's owner-wished granularity and collapses two unrelated consent domains (LAN vs third-party cloud OAuth).

Skip/default behavior: leaving the page untouched keeps S0391 defaults (all supported sources ON) - skip costs the user nothing and preserves upgrade-parity semantics.

## Conclusion

Recommend option B: two group toggles bulk-writing the six S0391 per-source states, default ON, with settings deep-link copy for fine-tuning. The page renders from the same persisted settings S0391 owns (no parallel store). Hard dependency: the page-2 dev ticket consumes S0391's storage and gate and MUST be declared `BlockByOtherTask` on S0391 until S0391 is implemented; if the onboarding skeleton ships first, page 2 is simply absent (page-list collapse rule, artifact 09) - no placeholder page.

## Impact on recommendation

- Target structure keeps page 2 but marks it conditional on S0391 delivery; skeleton must treat the page list as data so page 2 plugs in later without rework.
- Dev-ticket split (artifact 12): separate ticket for page 2, `BlockByOtherTask` → S0391; the rest of the redesign must not wait for it.
- SYNTHESIS should surface granularity (A vs B) as a confirmed-deviation item for the owner: the owner draft said "choose what kinds of networks" - option B matches that wording better than six protocol rows.
