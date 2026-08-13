# Стратегическая спецификация: S0719 - Аудит производительности и корректности release/R8

**Ticket:** S0719
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-26
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - дочерний тикет S0714 (принятие Code Audit Protocol)
**Umbrella:** S0714

> **Scope:** STRATEGIC. Цели и объём аудит-прохода. Конкретные горячие пути - на этапе `/spec-tech` и в fix-тикетах находок.

---

## 0. Источник

Прохождение Layer 6 (Performance) и Layer 7 (Release-build and R8 correctness) протокола `docs/CODE_AUDIT_PROTOCOL.md`. Статическая часть; измеримая часть (Macrobenchmark/Baseline Profiles) поставляется S0722.

## 1. Проблема

Горячие пути (плеер, просмотр больших библиотек, адаптеры) не проверялись на churn и повторные дорогие операции. Параллельно: зелёная debug-сборка не доказывает корректность отгружаемого минифицированного артефакта - reflection/DI/манифесты могут падать после R8 по всей матрице флейворов (связано с Rule 20).

## 2. Цели

**Статическая производительность:**

1. Повторные аллокации в горячих циклах; повторный парсинг путей/декод/сортировка/фильтрация, где результат переиспользуем.
2. Churn коллекций в адаптерах и плеер-хелперах; примитиво-оптимизированные коллекции (`SparseArray`/`LongSparseArray`/`ArrayMap`/`ArraySet`) против автобоксинга; `value class` для доменных идентификаторов.
3. Гигиена RecyclerView: стабильные id, `setHasFixedSize`, общий `RecycledViewPool`, `DiffUtil` payloads вместо полного ребайнда и `notifyDataSetChanged`.

**Корректность release/R8:**

4. Release-сборка затронутых флейворов компилируется, пакуется и проходит затронутый поток.
5. Keep-правила покрывают reflection/сериализацию (Gson/Moshi/Room/reflection-либы); нет неожиданных `R8: missing class`.
6. Dead-code shrink не удалил нужную в рантайме точку входа; поведение идентично по матрице (standard/lite/photos/legacy).

**Non-goals:** измеримые бенчмарки и Baseline Profiles (S0722); исправления - fix-тикетами.

## 3. Объём и ограничения

- Модули `app_v2/` и `wear/`.
- Проверка R8 - строго на минифицированной release/целевой сборке, не на debug.
- Аудит-проход не меняет поведение; правки - fix-тикеты.

## 4. Критерии приёмки

- [ ] Горячие пути просмотрены на churn/повторные операции; находки зафиксированы.
- [ ] Адаптеры просмотрены на гигиену RecyclerView.
- [ ] Release-сборка каждого флейвора матрицы собрана и прогнана по затронутому потоку.
- [ ] Лог минификации проверен на `missing class`/удалённые точки входа; keep-правила подтверждены для reflection/DI.
- [ ] Находки классифицированы P0-P3; нетривиальные запаркованы; отчёт в `## Last Audit`.

## 5. Связанные тикеты

- S0714 (зонтик).
- S0722 (Macrobenchmark/Baseline Profiles - измеримая часть).
- S0717 (Room - стоимость горячих запросов).
- S0737, S0738 (fix-тикеты по находкам этого аудита).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 1

Статический проход Layer 6+7 (perf + release/R8) завершён. Отчёт: `PLAN/S0719_performance-r8-release-audit/AUDIT_FINDINGS.md` (19 проверено, 2 опровергнуто, **17 подтверждено - 0 P0, 1 P1, 2 P2, 14 P3**; frequency-gating понизил suspected P1/P2 как viewport-bounded).

- Горячие пути и адаптеры просмотрены: `MediaFileAdapter` - ListAdapter+DiffUtil с payload для favorite/metadata, `onViewRecycled` Glide.clear; коллекции корректны (String-ключи, мелкие Int-id мапы); автобоксинг только в cold GIF-экспорте.
- Находки сведены в fix-тикеты: **S0737** (P1 Gson keep-rules, prio 60) - **Verified**; **S0738** (P2 + ~12×P3 adapter perf-гигиена, prio 35) - запаркован (Draft), измеренного jank нет.
- Keep-правила reflection/сериализации подтверждены в `app_v2/proguard-rules.pro`: `domain.usecase.Backup**` (L25), `data.model.TrashMetadata` (L26), `domain.game.**` (L27) - закрывают находку S0737 (Gson-модели без `@SerializedName`, риск потери данных при кросс-версионном restore).

### Manual / on-device

- [ ] Full-matrix release build (lite/photos/legacy) + проверка лога минификации на `missing class`/удалённые точки входа. Standard release собрана в проходе; полная матрица - на release-гейте (`/spec-prerelease` -> `/skill-release`). R8-корректность Gson-правок доказана через S0737 (Verified).
