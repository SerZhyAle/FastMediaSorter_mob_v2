# Стратегическая спецификация: S0164 — Ложное предупреждение Glide «Zero disk cache hits»

**Ticket:** S0164
**Status:** Implemented
**Priority:** 15
**Date:** 2026-05-11
**Tier:** 1 — Trivial polish
**Roadmap entry:** Полевой инцидент 2026-05-11, лог `logs/fastmediasorter_20260511_192813.log`

> **Scope:** STRATEGIC.

---

## 1. Проблема

В `logs/fastmediasorter_20260511_192813.log` (строки 1261–1274) при каждом завершении Browse-сессии выводится предупреждение:

```
⚠️ WARNING: Zero disk cache hits with 13 total loads!
This suggests:
  1. First time browsing (cache empty)
  2. Cache keys changed (refreshVersion increment?)
  3. Disk cache was cleared
  4. DiskCacheStrategy is NONE (check config)
```

При этом в той же строке лога:

```
GlideCacheStats: summary total=13 disk=0 memory=13 repo=0 network=0 local=0
Memory cache hits: 13 (100,0%)
Overall cache hit rate: 100,0%
```

Disk cache hits = 0, потому что **все 13 загрузок обслужены из memory cache** — это нормальный и оптимальный сценарий при повторном просмотре тех же файлов в рамках одной сессии. Предупреждение ложноположительно: overall cache hit rate 100%, disk cache не требовался. В production-логах это создаёт шум и маскирует реальные проблемы с кэшем.

---

## 2. Цели

1. Убрать ложноположительное WARNING когда `memory_hits + disk_hits + repo_hits >= total_loads` (overall hit rate ≥ 100% без учёта network/local).
2. Сохранить WARNING для реальных случаев: `disk_hits = 0` **и** `memory_hits < total_loads` (загрузки шли через network или local, а disk cache не работал).

**Non-goals:**

- Изменение логики кэширования Glide.
- Добавление новых метрик.

---

## 3. Предлагаемый подход

В компоненте/хелпере, который эмитирует `GlideCacheStats` и соответствующий WARNING, изменить условие срабатывания:

**Текущее условие:** `disk_hits == 0 && total_loads > 0`

**Новое условие:** `disk_hits == 0 && total_loads > 0 && (network_loads + local_loads) > 0`

Иными словами: WARNING имеет смысл только если были реальные «холодные» загрузки (network или local), которые должны были попасть в disk cache, но не попали. Если все загрузки из memory — disk cache не был нужен, предупреждение не нужно.

---

## 4. Ограничения

- Затрагивает только debug/диагностический код — production-поведение не меняется.
- Одна строка изменения условия.

---

## 5. Критерии готовности

1. При `memory_hits = total_loads` (100% memory cache) в логах нет `WARNING: Zero disk cache hits`.
2. При `network_loads > 0 && disk_hits = 0` предупреждение по-прежнему появляется.

---

## 6. Last Audit

**2026-05-11** — Implemented. Changed condition in `GlideCacheStats.logStats()`:
`disk == 0 && repo == 0 && total > 10` → `disk == 0 && repo == 0 && (network + local) > 0 && total > 10`.
One-line change. Criteria 1 and 2 satisfied by code review.
