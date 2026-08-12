---
name: verify-platform-api-with-javap
description: Confirm a platform API's exact signature with javap against the compileSdk android.jar before planning or speccing around it
metadata:
  type: feedback
---

Before writing a spec, a tactical plan, or code that depends on a platform API you have not called in
this repo before, confirm the exact member exists by decompiling the SDK stub - do not reason from
recollection of the Android docs.

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\javap.exe" `
    -classpath "$env:LOCALAPPDATA\Android\Sdk\platforms\android-36\android.jar" `
    android.graphics.pdf.content.PdfPageTextContent
```

**Why:** on S1276 the whole ticket rested on "the API 35 PDF text content also carries its geometry".
If it had not, the plan would have been built on a hallucinated accessor and the failure would have
surfaced only at the first compile - after the strategic spec, the phase files and the owner-facing
scope had all been written around it. The check took one command and turned an assumption into
`public java.util.List<android.graphics.RectF> getBounds();`. It also cuts the opposite waste:
without it the honest move is to write "verify this accessor exists" as a step and plan a fallback
branch for a capability that was there all along.

**How to apply:** any time a spec sentence would read "the platform exposes X". Cheaper than a build,
far cheaper than a wrong plan. `javap` ships with the Android Studio JBR, which is not on PATH - use
the full path above. Works for any `android.*` class; the compileSdk in `app_v2/build.gradle.kts`
decides which platform directory to point at.

**What javap cannot tell you - and what that costs.** A signature proves a member exists; it says nothing
about the runtime contract behind it. S1471 (2026-08-09): javap confirmed `ShortcutInfoCompat.Builder(
ShortcutInfoCompat)` exists, the code compiled, detekt/neuroslop/the full scoped `post-change` gate all
passed, and a dedicated read-only code audit found four other real defects in the same files - yet
pinning a stream shortcut crashed the app on first try with `NullPointerException: intent's action must
be set`. `ShortcutInfo.Builder.build()` requires the shortcut Intent to carry an action even when its
component is explicit, and nothing in the type system, the docs skim or the audit expressed that. Only
the device run caught it, on the ticket's own primary flow. So: use javap to settle "does X exist", never
to settle "will X work". Any change to a shortcut, widget, notification, foreground-service or
share-target payload - the places where the platform validates a Bundle/Intent at runtime - needs the
gesture actually performed on a device before the ticket is called done.

Related: [[verify-full-evidence]], [[tactical-plan-file-list-may-be-wrong]], [[feedback-phase-boundary-audit]].
