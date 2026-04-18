# VR Player — Phase Overview & Progress Dashboard

**Spec-source:** [../spec_openxr_3d_player.md](../spec_openxr_3d_player.md)  
**Last updated:** 2026-04-18

---

## Статус фаз

Обновляй строку статуса в нужном файле по мере продвижения.

| # | Фаза | Файл | Оценка | Статус |
|---|------|------|--------|--------|
| 1 | Foundation: Build & Scaffold | [01_foundation.md](01_foundation.md) | ~3h | 🔴 Not started |
| 2 | Shared Contracts | [02_shared_contracts.md](02_shared_contracts.md) | ~2h | 🔴 Not started |
| 3 | Standard CTA Integration | [03_standard_cta.md](03_standard_cta.md) | ~3h | 🔴 Not started |
| 4 | VR Engine & Host | [04_vr_engine.md](04_vr_engine.md) | ~6h | 🔴 Not started |
| 5 | Gate Review, Tests & Release | [05_quality_release.md](05_quality_release.md) | ~3h | 🔴 Not started |

**Итого оценка:** ~17h (3-4 рабочих дня + QA на Quest)

---

## Зависимости фаз

```
Phase 1 (Foundation)
    └─► Phase 2 (Shared Contracts)
            └─► Phase 3 (Standard CTA)   ← можно начать параллельно с Phase 4 Шагами 8-9
            └─► Phase 4 (VR Engine & Host)
                    └─► Phase 5 (Gate Review, Tests & Release)
```

Phase 3 и Phase 4 имеют общий блокер — Phase 2 должна быть завершена.  
Phase 3 (standard CTA) не требует работающего Quest-девайса.  
Phase 4 требует физического Quest 3 или Quest Pro начиная с задачи 4.4.

---

## Gate checklist (заполняй перед переходом к следующей фазе)

### После Phase 1 → Phase 2

- [ ] `./gradlew.bat assembleVrDebug` проходит без ошибок
- [ ] `./gradlew.bat assembleStandardDebug` не сломан (регрессия не допускается)
- [ ] `app_v2/src/vr/AndroidManifest.xml` создан, содержит entries для Meta и Google

### После Phase 2 → Phase 3 + Phase 4

- [ ] Все 4 contract-класса компилируются без ошибок
- [ ] Unit test skeletons проходят (`./gradlew.bat testStandardDebugUnitTest`)

### После Phase 3 → Phase 5

- [ ] CTA показывается при открытии SBS/OU файла на `standard` flavor (ручная проверка)
- [ ] Обычные 2D-видео открываются без VR CTA
- [ ] Lint чист: `./gradlew.bat lintStandardDebug`

### После Phase 4 → Phase 5

- [ ] Backend gate review проведён, итог зафиксирован в 05_quality_release.md
- [ ] `assembleVrDebug` собирается для arm64-v8a
- [ ] Ручная проверка на Quest (хотя бы один файл воспроизведён)

### После Phase 5 → Готово к release

- [ ] Все unit tests проходят
- [ ] Manual QA matrix заполнен в 05_quality_release.md
- [ ] `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` обновлены
- [ ] String resources добавлены в EN/RU/UK
- [ ] `dev/CHANGELOG.md` обновлён для всех изменённых файлов

---

## Команды быстрого запуска

```powershell
# Сборка VR debug (после Phase 1)
.\gradlew.bat assembleVrDebug

# Сборка standard (контроль регрессий)
.\gradlew.bat assembleStandardDebug

# Unit tests
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat testVrDebugUnitTest

# Lint
.\gradlew.bat lintStandardDebug
.\gradlew.bat lintVrDebug

# Release сборки для store submission
.\gradlew.bat assembleVrRelease   # APK → Meta Horizon Store
.\gradlew.bat bundleVrRelease     # AAB → Google Play
```
