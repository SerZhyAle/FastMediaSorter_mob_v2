---
name: probe-measure-poisons-text-centering
description: Manual child.measure() on live views desyncs TextView label centering (getMeasuredHeight) from icon centering (real height) - heal via posted forceLayout+requestLayout
metadata:
  type: project
---

Manual probe `child.measure()` on LIVE views (overflow width checks) overwrites `measuredHeight` with the preferred size (e.g. M3 TextButton min 40dp). TextView centers TEXT against `getMeasuredHeight()` (AOSP TextView getBoxHeight, :8619 in android-36.1) but compound-drawable ICONS against real `mBottom-mTop` (:8950) - labels ride `(realH - staleMh)/2` px high (4px at 48dp, 8px at 56dp buttons). Race-dependent: any later real measure pass heals it, so fresh launches may look fine while long-lived installs (car head units) show it stably. Proven and fixed in S1258 (2026-07-28).

**Why:** the defect survives reboot/reinstall/pm-clear and hides from fresh-install testing - it looks platform- or data-dependent but is neither; three sessions of hypothesis-chasing until runtime probes (log h/mh/baseline) nailed it.

**How to apply:**
- After any manual `measure()` on attached views, heal: `container.post { children.forEach { it.forceLayout() }; container.requestLayout() }`. Post is mandatory - inline heal inside doOnLayout gets superseded by later re-probes mid-pass (proven: inline failed, posted healed mh 40->56).
- Measuring adapter-built DETACHED views (popup width sizing) is harmless.
- Diagnosing "icon vs label vertical offset": log `height/measuredHeight/baseline` of the button - `mh != h` is the smoking gun; delta formula `(h-mh)/2` confirms.
- Grep `makeMeasureSpec` outside onMeasure() to find candidate poisoners. Healed sites: MainLayoutChromeManager.applyControlBarOverflow, MainProgramsPanelManager.applyOverflow, BrowseCommandOverflowManager.measuredWidthOf. Custom ViewGroup onMeasure children = legitimate.
- Related open ticket: S1263 (btnMainDropdownMenu content sinks ~10px low - different mechanism, whole content incl. icon).
- Verification recipe: emulator `wm size 1024x600` + `wm density 160`, pixel-measure band via temp/S1258/measure.py; `adb emu screenrecord screenshot <dir>` renders the logical display 1:1 inside the physical frame (works when `adb screencap` returns 0 bytes after wm churn).
