---
name: welcome-layout-has-3-width-variants
description: activity_welcome.xml exists in layout/, layout-sw480dp/, layout-sw720dp/ - a new view id must be added to all three or ViewBinding makes the field nullable
type: project
---

`app_v2/src/main/res` has THREE qualified copies of `activity_welcome.xml`: `layout/`,
`layout-sw480dp/`, `layout-sw720dp/`. There is NO `layout-land/` variant (the activity shell is
portrait-only; only the page fragments have land variants).

**Why:** ViewBinding generates a field as non-null only when the id is present in EVERY configuration
variant of that layout. Adding `@+id/btnEnableAll` to just `layout/` made `binding.btnEnableAll` a
nullable `MaterialButton?`, which fails to compile on a direct `.setOnClickListener {}` /`.visibility`
call ("Only safe (?.) or non-null asserted (!!.) calls allowed on a nullable receiver"). S0409 hit
exactly this - the build surfaced it, not a grep.

**How to apply:** when adding any new view id to `activity_welcome.xml` (or any layout with width/
orientation qualifiers), add it to all qualified copies in lockstep, or the binding field is nullable.
The two `sw*dp` copies use a multi-line element style and `@dimen/welcome_button_min_height` /`36dp`
for `minHeight`; the base `layout/` copy is single-line per element. Generalizes the landscape-parity
rule to width qualifiers. Related: the welcome redesign foundation lives in S0395/S0398/S0399/S0400/S0402.
