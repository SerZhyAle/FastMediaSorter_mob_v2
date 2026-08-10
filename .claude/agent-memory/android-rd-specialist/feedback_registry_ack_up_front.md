---
name: registry-ack-up-front
description: Pass -RegistryAck on the FIRST post-change call whenever the changed set touches a registered document, or the closure ends in an advisory and the re-run writes a duplicate dev-log row.
metadata:
  type: feedback
---

Whenever a closure's changed set includes a file matched by a `docs/DOCUMENT_REGISTRY.jsonl` record's
`paths` (privacy policy, settings reference + its manifest/annotations, ALL_FEATURES, README mirrors),
read those records first and pass `-RegistryAck "<id>[,<id>]"` on the **first** `post-change.ps1` call.

**Why:** the `document-registry` step withholds a bare `PASS` until the touched records are
acknowledged - it exits 1 as an advisory, so the run still ends `PASS WITH ADVISORIES` and exit 0. The
only way to a clean verdict is to re-run, and `post-change` writes its dev-log row on every run, so the
second one lands a near-duplicate changelog entry for the same logical change. Hit twice in one session
(2026-08-10): `settings-reference` + `feature-inventory` on S1036, `legal-downloads` on S1546.

**How to apply:** before the closure, `Select-String -Path docs/DOCUMENT_REGISTRY.jsonl` for the file
names in the set, or just grep the registry for the doc's directory. Read the matched records (the loop
requires it anyway), check the sibling `paths` the change did NOT touch for the same edit, then close
once with the ack. Acknowledging is a claim you read them - never pass `all` to silence the step.
