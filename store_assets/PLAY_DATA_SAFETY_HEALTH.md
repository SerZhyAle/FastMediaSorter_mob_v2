# Play Console - Data safety & Health apps section (S2995)

Source of truth for the **Health apps and health data declarations** in Play Console
(`App content -> Health apps` and `App content -> Data safety -> Data types -> Health info`), for both form factors.

---

## 1. Play Console Declaration

Declaration made in Play Console (`App content -> Health apps`):
**"You told us that your app does not have any health features"**

This document records the engineering guarantee that the `standard` product flavor (the build distributed through Google Play) strictly complies with this declaration across all surfaces on both Phone and Wear OS.

---

## 2. Health Capabilities Matrix

| Feature / Surface | Module / Flavor | Available in `standard` (Play Store) | Available in `noLegal` (Sideload) | Notes |
| --- | --- | --- | --- | --- |
| **Blood Pressure Journal** (`BLOOD_PRESSURE`) | `wear` | **No** (withheld via `WearRestrictedCapabilities`) | Yes | Manual entry & history UI |
| **Motion Monitor** (`MOTION_MONITOR`) | `wear` | **No** (withheld via `WearRestrictedCapabilities`) | Yes | Sensor motion tracking |
| **Body Sensor / Heart Rate** (`BODY_SENSOR`) | `wear` | **No** (withheld via `WearRestrictedCapabilities`) | Yes | Heart rate sensor diagnostics |
| **Steps Gadget** (`StepsGadget`) | `app_v2` | **No** (withheld via `isAvailable()`) | Yes | Desktop step counter widget |
| **Tourist Dashboard Steps** | `app_v2` | **No** (disabled in `ObserveTouristDashboardUseCase`) | Yes | Steps tile & telemetry |

---

## 3. Merged Manifest Declarations

Neither `app_v2/src/main/AndroidManifest.xml` nor `wear/src/main/AndroidManifest.xml` declares any health or body sensor permissions.

All health permissions (`BODY_SENSORS`, `health.READ_HEART_RATE`, `ACTIVITY_RECOGNITION`) are declared exclusively in `src/noLegal/AndroidManifest.xml` for both modules and never leak into the `standard` store build.

---

## 4. Verification

- `StandardWearRestrictedCapabilities`: `offersHealthFeatures = false`, `offersBodySensorDiagnostics = false`.
- `NoLegalWearRestrictedCapabilities`: `offersHealthFeatures = true`, `offersBodySensorDiagnostics = true`.
- `WearAppCatalog`: filters out health apps in `standard`.
- `ResolveWearLaunchAddressUseCase`: refuses destination resolution for health targets in `standard`.
- `MainActivity`: omits health screen routes from the navigation graph in `standard`.
- `StepsGadget`: `isAvailable() = false` in `standard`.
- `ObserveTouristDashboardUseCase`: step telemetry collection skipped in `standard`.
