# Тактический план: Категория C — Организационный уровень

## Охват

Документ детализирует `C1` и `C2` до операционных задач, применимых после стабилизации инженерных quality gates.

---

## Последовательность

1. `C1` Сначала формализовать регламент выпуска и автоматизацию release-операций.
2. `C2` Затем внедрить инструментирование и отчётность по KPI качества.

---

## Task Backlog (готово к постановке)

| Task ID | Инициатива | Задача | Входы | Выходы |
|---------|------------|--------|-------|--------|
| C1-T1 | C1 | Описать release policy и критерии gate перед выпуском | CI и QA процесс | Формальный release policy |
| C1-T2 | C1 | Подготовить шаблон `RELEASE_CHECKLIST.md` | Текущие практики | Единый checklist для релиза |
| C1-T3 | C1 | Автоматизировать version bump | build/versioning config | Скрипт версионирования |
| C1-T4 | C1 | Автоматизировать генерацию changelog | Git history | Скрипт changelog generation |
| C1-T5 | C1 | Автоматизировать RC build/tag + уведомления | CI/CD | Автоматизированный выпуск кандидата |
| C2-T1 | C2 | Добавить instrumentation для scan/auth/startup | App core | Сбор timing KPI |
| C2-T2 | C2 | Добавить success/failure counters для ключевых операций | Domain events | KPI counters |
| C2-T3 | C2 | Настроить baseline capture и хранение | Telemetry storage | Зафиксированный baseline |
| C2-T4 | C2 | Реализовать генерацию metrics report | Metrics data | Автоматический отчёт |
| C2-T5 | C2 | Проверить интеграцию с crash dashboards/alerts | Crashlytics/alerts | Наблюдаемость деградаций |

---

## Артефакты

| Артефакт | Расположение |
|----------|-------------|
| Release Checklist Template | `dev/RELEASE_CHECKLIST.md` |
| Changelog Generator | `scripts/generate_changelog.sh` |
| Metrics Report Generator | `scripts/generate_metrics_report.sh` |
| Version Bump Script | `scripts/bump_version.sh` |

---

## Критерии завершения

- [ ] Процесс выпуска формализован и воспроизводим по checklist.
- [ ] Выпуск кандидата автоматизируется без ручных ad-hoc действий.
- [ ] KPI качества собираются автоматически и попадают в отчёт.
- [ ] Degradation в KPI наблюдаема через отчёт и алерты.
