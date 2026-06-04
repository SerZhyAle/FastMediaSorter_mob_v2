---
name: string-tools-main-res-only
description: check_strings_localized.ps1 and set-android-string.ps1 only operate on src/main/res; flavor source-set strings (src/<flavor>/res) are invisible to them
metadata:
  type: feedback
---

The string localization helpers operate on `src/main/res` only — they do NOT see flavor source-set string files under `src/<flavor>/res/values*/strings.xml`.

**Why:** Observed on S0336 (2026-06-03). `set-android-string.ps1 -Action add -Module app_v2` wrote the new keys to `src/main/res/.../strings.xml`. `check_strings_localized.ps1 -KeyPrefix "nolegal_diag_"` reported "No keys matching .. found in any locale" even though the 7 keys existed (and compiled) in `src/noLegal/res/values{,-ru,-uk}/strings.xml`. The same audit DID find `system_info_*` keys that lived in `src/main/res`. So both tools are scoped to the main source set.

**How to apply:**
- For a flavor-only string (noLegal / vr / lite / photos / legacy), hand-edit the three `src/<flavor>/res/values{,-ru,-uk}/strings.xml` files (structural XML add is an allowed hand-edit case).
- Do NOT rely on `check_strings_localized.ps1` to prove EN/RU/UK parity for flavor strings — it returns a false "not found". Verify parity with a direct `Grep -c 'name="<prefix>'` across the three flavor files instead (expect equal counts).
- A successful flavor build only proves the default `values/` (EN) keys resolve — a missing RU/UK translation does NOT fail the build, so parity still needs the grep check.
- Relates to [[reference_strings_tool]] and [[feedback_check_existing_tooling]].
