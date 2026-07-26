---
name: weather-gadget-open-meteo-2026-07
description: S0426 desktop weather gadget ships on keyless Open-Meteo with the owner accepting the non-commercial licence risk; met.no is the escape hatch
metadata:
  type: project
---

The launcher desktop weather block (S0426) uses **Open-Meteo** for both the reading and the city geocoding, on **every** flavor that mounts `launcherEnabled` (standard, noLegal), keyless and unpaid. Implemented 2026-07-24, left in `BlockNeedUserTest`.

**Why:** Open-Meteo's free tier is explicitly non-commercial ("You may only use the free API services for non-commercial purposes", 10k calls/day, CC-BY 4.0). The owner decided on 2026-07-24 that FastMediaSorter counts as a non-commercial project and took that Play-store risk knowingly, after I flagged it; the alternative keyless-and-commercial-OK source is **api.met.no** (global, needs an identifying User-Agent + CC-BY, no geocoding of its own) and everything else free needs a key.

**How to apply:**
- The CC-BY attribution is an obligation, not decoration: it renders in the gadget (`launcher_gadget_weather_attribution`) and in FAQ EN/RU/UK. Never "clean up" either.
- If Play ever objects, the swap target is met.no: implement `WeatherProvider` again and rebind it in `core/di/WeatherModule.kt` - nothing above the seam changes. Geocoding would then need its own source (platform `Geocoder` or GeoNames).
- Both endpoint shapes were verified live on 2026-07-24: `current.{temperature_2m,weather_code,is_day}` and `results[].{name,latitude,longitude,admin1,country_code}`.
- Deferred by design: the opt-in coarse-location "use my location" path (strategic §2 goal 3) - the cell param is already a coordinate pair, so it slots in without touching the gadget or the data layer.
