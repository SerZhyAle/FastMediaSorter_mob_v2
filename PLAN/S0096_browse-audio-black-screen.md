# S0096 — Black Screen in Browse (audio library)

<!-- auto-approved by /spec-all — 2026-05-05 -->

**Status:** Approved
**Priority:** 50

## Goal

Добавить пункт "чёрный экран" в overflow-меню Browse для аудиотеки. Пользователь слушает
музыку из Browse (Play / Play Random / inline-плеер) и хочет выключить экран одним касанием,
не заходя в Player — удобно при езде в машине. Overlay идентичен Player: полноэкранный
чёрный View, тап — скрыть; сбрасывается при onPause Browse.

## Phase 1 — Menu item

**P1.1** Add `action_black_screen` as the **first** item in
`app_v2/src/main/res/menu/menu_resource_ops.xml` using `black_screen_button_title` (string already
exists). `app:showAsAction="never"`.

**Verification:** menu XML compiles; item id `R.id.action_black_screen` resolvable.

## Phase 2 — ResourceOpsMenuManager

**P2.1** Add parameters to `showMenu()` in `ResourceOpsMenuManager.kt`:
```
isAudioOnly: Boolean = false,
onBlackScreenClicked: (() -> Unit)? = null
```

**P2.2** After existing visibility guards, add:
```kotlin
popup.menu.findItem(R.id.action_black_screen)?.isVisible =
    isAudioOnly && onBlackScreenClicked != null
```

**P2.3** In the `when (item.itemId)` block add:
```kotlin
R.id.action_black_screen -> { onBlackScreenClicked?.invoke(); true }
```

**Verification:** All existing call sites compile (new params have defaults).

## Phase 3 — BrowseManagerInitializer

**P3.1** Import `BlackScreenOverlayManager`, `SystemBarsManager`, `java.lang.ref.WeakReference`.

**P3.2** Add field:
```kotlin
internal lateinit var blackScreenManager: BlackScreenOverlayManager
```

**P3.3** In `initialize()`, before `buttonSetupHelper` is created, instantiate:
```kotlin
val sysBars = SystemBarsManager(activity)
blackScreenManager = BlackScreenOverlayManager(WeakReference(activity), sysBars)
```

**P3.4** In the full `showMenu(...)` call (onResourceOpsClicked / btnResourceOps), add:
```kotlin
isAudioOnly = viewModel.state.value.resource?.isAudioOnly() == true,
onBlackScreenClicked = if (BuildConfig.SUPPORT_AUDIO) {{ blackScreenManager.show() }} else null,
```

**Verification:** `blackScreenManager` is `lateinit` and initialized before first menu call.

## Phase 4 — BrowseActivity lifecycle

**P4.1** In `BrowseActivity.onPause()`, inside the `if (::initializer.isInitialized)` block, add:
```kotlin
initializer.blackScreenManager.hide()
```

**Verification:** Overlay is dismissed when navigating away or returning to Player.

## Last Audit

2026-05-05 — auto-verified by /spec-all. Build: PASS. All 4 phases confirmed in code:
P1 menu item present; P2 visibility + handler wired; P3 manager instantiated + callback passed; P4 hide() on onPause.
