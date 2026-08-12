---
name: post-change-kotlin-skips-doc-pin-gate
description: post-change -ChangeType Kotlin skips the doc-pin and document-registry gates, so a pinned value bumped in Kotlin alone lands with stale docs and a green close
metadata:
  type: feedback
---

`post-change.ps1 -ChangeType Kotlin` **skips** `doc-pin-drift` and `document-registry` outright
("not applicable for ChangeType Kotlin"). A change that bumps a value the docs pin - Room
`@Database(version)`, an SDK level, a dependency version - therefore closes green while
`dev/TECH_REQUIREMENTS.md` still shows the old number.

**Why:** S1378 phase 02 bumped the Room schema 44 -> 45 and closed `PASS` as `Kotlin`. The drift
only surfaced two phases later, when phase 04 happened to remove a string key and so closed as
`Mixed`, which does run the doc gates. Nothing about phase 02 looked wrong at the time.

**How to apply:** when a Kotlin-only change moves a number the docs pin, either close it as
`-ChangeType Mixed` (naming the doc file in `-Files`) or run
`pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` by hand before claiming the close.
Closing as `Mixed` additionally arms the `document-registry` gate, which then holds the close until
the registered doc's siblings are read - answer it with `-RegistryAck '<area>'` once you have
checked them and can say why they need no edit.

Related: [[feedback_detekt_baseline_signature_resurface]], [[feedback_write_detekt_clean_first_time]].
