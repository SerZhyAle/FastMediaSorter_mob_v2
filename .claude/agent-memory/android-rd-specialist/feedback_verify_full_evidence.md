---
name: verify-full-evidence
description: Adversarial verify agents must receive/read the FULL original finding evidence, not a summary - compressed claims get refuted on the weakened version
metadata:
  type: feedback
---

When dispatching an adversarial verification agent for an audit finding, never paraphrase or compress the claim in the prompt - point the skeptic at the verbatim evidence (spec section 0 / recovered JSON) and require it to address EVERY mechanism named there.

**Why:** 2026-07-02, S0854: my prompt summarized the finding as "overwrites loop without stop()" and dropped the key mechanism (unserialized managerScope.launch coroutine suspending between preflight-stop and start). The skeptic "refuted" the weakened claim by showing sequential stop-before-start pairing - a strawman verdict that nearly archived a real P0 (confirmed by direct read of VideoPlayerManager.kt:618-671). Original panel skeptics who saw the full evidence voted confirm.

**How to apply:**
- Verify prompts: give file refs + "Read the finding verbatim in <spec/json path>, section 0" instead of my own restatement.
- Require the verdict to explicitly address each mechanism/step of the claimed trigger sequence; a refutation that skips one is not a refutation.
- On a split vote where the refuter did not engage the strongest form of the claim, tie-break by reading the disputed code myself before archiving anything.
