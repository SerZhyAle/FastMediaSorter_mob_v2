---
name: enumerate-all-layout-variants-not-just-land
description: Rule 11 names only the layout-land counterpart, but screens here also have layout-w600dp (and other) variants - enumerate every variant of a layout before editing, or ViewBinding turns the miss into a nullable field
metadata:
  type: feedback
---

Before editing any `res/layout/<name>.xml`, enumerate **every** variant of that exact filename
(`Glob app_v2/src/main/res/layout*/<name>.xml`) and edit all of them in the same step - not just the
`-land` one CLAUDE.md Rule 11 names.

**Why:** Rule 11's text says "editing `res/layout/*.xml` requires an equivalent edit in
`res/layout-land/*.xml`", so following it literally reads as "there are two". `activity_streams.xml`
has three: `layout/`, `layout-land/` and `layout-w600dp/` (S1473, 2026-08-08). ViewBinding folds every
qualifier variant into one binding class, and a view present in some variants but not all becomes a
**nullable** field - so the miss surfaces as `Argument type mismatch: actual type is 'ImageButton?'`
at a call site far from the layout, not as anything that reads like "you forgot a layout". If the new
view had only been read (not passed), it would have compiled and silently rendered nothing on wide
screens.

**How to apply:**

- Glob the variants first; the answer is one cheap call and it is never guessable from the rule text.
- Plan-time: a tactical step's `Files Touched` must list all of them, or the plan is wrong before
  implementation starts.
- Reading the compile error backwards is the slow path - a nullable ViewBinding field almost always
  means "this id is missing from at least one layout variant", not "the view is optional".
- Distinct from [[res-sw-qualifier-beats-land]], which is about a `values-*` bucket silently shadowing
  another for the same key. This one is about a `layout-*` variant being missed entirely.
