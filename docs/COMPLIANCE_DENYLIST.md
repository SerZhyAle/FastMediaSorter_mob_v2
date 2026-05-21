# Compliance Deny-List

This document defines the S0286 build-time guard for market flavors and the public feature inventory.

## Scope

- The task name is `verifyNoPlatformNames`.
- It runs from `app_v2/build.gradle.kts` and is wired into `preBuild`.
- It scans `app_v2/src/main`, `src/legacy`, `src/lite`, `src/photos`, `src/vr`, plus `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md`.
- It does not scan `app_v2/src/noLegal` or `docs/FEATURES_noLegal*.md`.

## Source Files

- Deny-list tokens live in `app_v2/compliance/platform-name-denylist.txt`.
- Temporary reviewed legacy suppressions live in `app_v2/compliance/platform-name-baseline.txt`.

## How To Add A Token

1. Add one new token per line to `app_v2/compliance/platform-name-denylist.txt`.
2. Run `./gradlew.bat :app_v2:verifyNoPlatformNames`.
3. Fix new violations, or add a reviewed temporary baseline entry only when the literal must stay for now.

## Baseline Format

- One entry per line.
- Format: `relative/path<TAB>trimmed line`.
- Use the baseline only for reviewed legacy debt that cannot be removed in the current ticket.
- When a file is sanitized, delete its baseline line in the same change.

## Inline Suppression Marker

When a market-file literal is truly unavoidable, add `allow-platform-literal:` on the same line or the previous non-empty line.

Examples:

```kotlin
// allow-platform-literal: legacy account label kept until Sxxxx follow-up
val label = "Instagram"
```

```xml
<!-- allow-platform-literal: legacy folder label kept for backward compatibility -->
<string name="folder_instagram">Instagram</string>
```

Use inline suppressions sparingly. Prefer removing the literal or moving the behavior into `noLegal` when the feature is site-specific.