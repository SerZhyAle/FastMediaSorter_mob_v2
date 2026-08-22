# Phase 01 - Gadget Value Autosizing Layouts

**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 02
**Steps done:** 2 / 2

## Objective

Update primary metric gadget layouts (Altitude, Speed, Steps, Satellites, Weather, Technical) so value views use weighted height and larger autoSizeMaxTextSize for smooth proportional scaling on multi-cell desktop resize.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_altitude.xml` | Modified | - |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_speed.xml` | Modified | - |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_steps.xml` | Modified | - |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_satellites.xml` | Modified | - |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_weather.xml` | Modified | - |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_technical.xml` | Modified | - |

## Steps

### Step 01.1 - Update Altitude, Speed, and Steps gadget layouts

**Files:** `gadget_launcher_altitude.xml`, `gadget_launcher_speed.xml`, `gadget_launcher_steps.xml`

**Prompt for developer:**

> Set `layout_height="0dp"`, `layout_weight="1"`, `autoSizeMaxTextSize="96sp"`, `gravity="center_vertical"` on value TextViews to allow continuous font scaling when cells stretch across multiple grid cells.

**Verification:**

- Layout XMLs compile without errors.

**Status:** `[x]` done

### Step 01.2 - Update Satellites, Weather, and Technical gadget layouts

**Files:** `gadget_launcher_satellites.xml`, `gadget_launcher_weather.xml`, `gadget_launcher_technical.xml`

**Prompt for developer:**

> Set `layout_height="0dp"`, `layout_weight="1"`, `autoSizeMaxTextSize="96sp"`, `gravity="center_vertical"` on main value/temperature TextViews.

**Verification:**

- Layout XMLs compile without errors.

**Status:** `[x]` done
