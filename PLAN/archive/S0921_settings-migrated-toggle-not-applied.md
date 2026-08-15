# S0921 - Migrated setting toggle not applied until manual re-toggle

**Status:** Archived
**Priority:** 55

## 0. Raw capture (owner report, on-device)

Found while device-testing S0820 (video-fullscreen-open-option) on Galaxy S21+ (SM-G996U1, Android 15), app upgraded from an older version (data migrated, not a clean install).

Owner report (verbatim, RU):
> S0820 - это сработало при установки галочки руками. Но у меня она уже "как бы стояла" при миграции со старой версии. Галочка бы стояла, а опция не работала - убрал - поставил - заработало.

Symptom:
- After upgrading from an old version, the "Open video files in fullscreen" toggle appeared already ON (persisted value migrated as `true`).
- The feature did NOT actually take effect while the toggle showed ON.
- Toggling it OFF and back ON made it work.

So a migrated/persisted toggle value is present but its side-effect is not applied on first load after upgrade - only a live onChange re-application activates it.

## 1. Scope / open questions (to research)

- Is this specific to the fullscreen-open setting (S0820), or a general pattern for toggles whose effect is applied via an onChange listener rather than read at startup / first use?
- Which settings apply their effect only through a change-listener vs. reading the persisted value at the point of use? Audit the ones added/changed recently.
- Does the settings migration path write the value without triggering the same application code that a manual toggle runs?
- Repro reliability: needs an actual upgrade-over-old-data path (not a clean install) - clean installs and manual toggles both work, which is why it was invisible in the sweep.

## 2. Notes

- S0820 itself was left in BlockNeedUserTest (not archived) because of this defect; the fullscreen feature works on fresh install / manual toggle but the migrated-state application is broken.
- Fresh-install and manual-toggle paths are correct; the gap is the upgrade path applying persisted state at load.

## 3. Root-cause research (from code, 2026-07-03)

The persistence layer is NOT the defect. `SettingsRepositoryImpl` reads `openVideoInFullscreen = preferences[KEY_OPEN_VIDEO_IN_FULLSCREEN] ?: true` (default true) and writes it symmetrically - a migrated/absent key resolves to `true`, so the global toggle value itself survives an upgrade.

The gate is in `BrowseEventHandler` (open-video path):

```kotlin
val enterFullscreenOnOpen = file?.type == MediaType.VIDEO &&
    viewModel.settings.value.openVideoInFullscreen &&
    viewModel.state.value.resource?.showCommandPanel == null
```

Fullscreen-on-open fires only when the resource has NO explicit per-resource `showCommandPanel` choice (`== null`). This is intentional (S0820 comment: "an explicit choice always wins over the new default"). After an upgrade from an older version, existing resources most likely carry a non-null `showCommandPanel` (the old build persisted an explicit panel-visible choice), so the gate's third clause is false and the global toggle has no effect on them - even though the toggle shows ON. That matches the owner's "checkbox was on but nothing happened".

Not fully explained from code alone: the owner reported that toggling the GLOBAL checkbox off/on fixed it. A per-resource override would not be cleared by a global re-toggle, so either the repro resource actually had `showCommandPanel == null` (and a stale `settings.value` cache was the real miss), or `showCommandPanel` persistence interacts with the toggle in a way that needs an on-device upgrade repro to pin down. This is why it can't be closed from code alone.

## 4. Owner decision (resolved 2026-07-05)

Desired behaviour after upgrade, when a resource carries a migrated explicit `showCommandPanel`:

- **A. (CHOSEN)** Keep as-is - explicit per-resource choice wins over the global fullscreen toggle. This is not a bug; document the precedence and close. Preserves the S0820 invariant ("an explicit choice always wins over the new default").
- **B.** On migration, reset `showCommandPanel` to null for resources whose stored value equals the old global default, so the new global toggle governs them again. (Rejected.)
- **C.** Change precedence for VIDEO so the global fullscreen toggle overrides a per-resource panel-visible choice. (Rejected - breaks S0820 invariant.)

### Quiz decisions (2026-07-05)
- Behaviour after upgrade with migrated explicit `showCommandPanel` -> **A. Keep as-is** (explicit per-resource choice wins; document precedence, not a bug).
- Repro resource state (had explicit panel choice vs null) -> **Unsure / needs re-verify** (owner could not confirm from memory).

### Outstanding
- The exact repro resource state is unconfirmed. If a re-check shows the resource had `showCommandPanel == null` (gate should have fired), a second defect exists: a stale `settings.value` cache not re-applied after migration. Decision A only covers the precedence path; a null-state repro would reopen this as a separate cache-refresh bug. Needs an on-device upgrade-over-old-data repro to settle.
- Next actionable work under A: document the precedence rule (per-resource `showCommandPanel` wins over the global fullscreen toggle) in the settings reference / S0820 area, and release S0820 from BlockNeedUserTest on the understanding that migrated explicit choices legitimately suppress the global default.

## 5. Resolution (2026-07-05)

Closed under owner decision A (intended precedence, not a bug). The precedence is now documented in code at the decision point: the `BrowseEventHandler` fullscreen-open gate carries an S0921 note stating that a resource migrated from an older build may keep a non-null `showCommandPanel`, and that migrated explicit choice legitimately suppresses the global fullscreen toggle even while the toggle shows ON.

Residual, deferred (not part of this closure): the hypothetical stale `settings.value` cache-refresh defect only exists if an upgrade-over-old-data repro fails with `showCommandPanel == null`. That needs an on-device upgrade repro (no device this session) and is folded into S0820's pending `BlockNeedUserTest` device verification; if it reproduces it becomes a separate cache-refresh ticket. S0820 is therefore left in `BlockNeedUserTest` (its fullscreen feature still needs its own on-device confirmation).
