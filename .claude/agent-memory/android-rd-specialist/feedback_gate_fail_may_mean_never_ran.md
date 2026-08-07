---
name: gate-fail-may-mean-never-ran
description: A gate's FAIL can mean its underlying check never executed - read the JUnit report or the gradle tail before believing the message and hunting a defect
metadata:
  type: feedback
---

When a `post-change` gate reports a specific defect, confirm the check actually ran before acting on
the message. Several gates infer their verdict from an artefact's presence or freshness, so a crashed
or skipped test becomes "found a discrepancy" rather than "could not verify".

**Why:** on 2026-08-07 (S1401) `settings-doc-sync` reported `committed settings-manifest.json differs
from the live scan` three runs in a row. The manifest was byte-identical. The JUnit report said
`tests="1" skipped="1" failures="0"` - `SettingsManifestExportTest` died in Robolectric teardown and
its assertion never executed, so nothing was ever compared. Roughly 40 minutes went into looking for a
manifest drift that did not exist. Parked as S1464 (since fixed); the sibling S1462 is the same shape
in `agent-lock.ps1`. CLAUDE.md section 12 already separates exit 1 "a gate failed" from exit 2 "could
not verify" - these gates were collapsing the two.

**How to apply:**
- Gate names a test-backed artefact (settings manifest, icon inventory, device-profile matrix) and
  fails -> open `app_v2/build/test-results/**/TEST-<class>.xml` first. `skipped="1"` or `tests="0"`
  means it never ran; only `failures>=1` is a real finding.
- Same failure text repeating in seconds rather than minutes is a signal: a real comparison costs a
  full Robolectric run.
- Before blaming your own change, check whether the gate passed earlier in the same session on the
  same files - a `PASS` in an earlier `post-change` tail is strong evidence the tree, not the change,
  moved.
- Report it as "could not verify", never as "passed" and never as "my change broke it". See
  [[project_spec_all_concurrent_tree_red]] for why a shared tree makes this common.
