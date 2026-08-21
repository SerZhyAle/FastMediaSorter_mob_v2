---
name: check-main-app-before-declaring-a-non-goal
description: Before writing a spec Non-goal that excludes a case, check how the main app already handles that case - a gate found in one code path is not a product-wide limitation.
metadata:
  type: feedback
---

Never turn "the code path I just read refuses this" into a spec Non-goal without first checking how the main app already handles that same case elsewhere. A restriction found on one path is evidence about that path, not about the product.

**Why:** on S1884 (2026-08-21) I read `OpenPhoneResourceChannelUseCase.isDeliverable()` = `type == ResourceType.LOCAL`, and wrote "sending a file from a network or cloud resource is out of scope" into the spec's Non-goals. The owner pushed back immediately - "если мы не можем играть файл из сети, мы копируем его тихонечко к себе и играем от себя, ты не в курсе? посмотри как работает основная программа". He was right: the app's standing answer to a remote source is to download a local cache copy first, and the «Send to..» dispatch applies it to every receiver before the receiver runs. The `LOCAL` gate belonged to the *other* direction - the path where the watch names the address - and said nothing about sending from the phone. The wrong Non-goal then propagated: it had already pulled a constraint into §3.2, a risk into §7, and an ADR that picked the wrong transport.

**How to apply:** when a gate, a filter or a capability check looks like it bounds a feature, ask "what does the app do for this case today, elsewhere?" before writing the limitation down. Search for the general mechanism, not the specific refusal - the app usually has one. Then, if the exclusion really is real, say which path it belongs to, because a reader cannot tell a path-local gate from a product rule once it is phrased as a Non-goal. A wrongly-scoped Non-goal is expensive because the rest of the spec is written to obey it.

Related: [[feedback_research_over_owner_question]], [[feedback_no_owner_questions_when_architecture_already_answers]].
