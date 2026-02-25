# ТЗ C2: Метрики качества

## Статус: 📋 Запланировано
## Приоритет: 🟡 Средний
## Зависимости: C1 (Release Train)

---

## Описание проблемы

Нет единой измеримой системы KPI качества, из-за чего готовность релиза оценивается субъективно.

## Цель

Автоматический сбор и отчётность ключевых KPI качества для data-driven решений.

---

## Требования

### Ключевые метрики
- Crash-free rate.
- Median scan time.
- Auth success rate.
- Resource save success.
- ANR rate.
- App startup time.

### Сбор данных
- Crash metrics из Crashlytics.
- Timing/counter metrics из приложения.
- CI quality metrics из pipeline.

### Отчётность
- Автоматический отчёт по KPI.
- Сравнение с baseline.
- Алерты при деградации.

---

## Task Backlog (уровень постановки)

### Instrumentation
- [ ] C2-T1: Добавить timing instrumentation для scan/auth/startup.
- [ ] C2-T2: Добавить success/failure counters для auth/resource save.
- [ ] C2-T3: Определить формат хранения и экспорта метрик.

### Baseline & Reporting
- [ ] C2-T4: Зафиксировать baseline для всех KPI.
- [ ] C2-T5: Реализовать генератор metrics report.
- [ ] C2-T6: Реализовать сравнение текущих значений с baseline.

### Monitoring
- [ ] C2-T7: Проверить интеграцию с Crashlytics dashboard.
- [ ] C2-T8: Настроить алерты при деградации KPI.

## Артефакты

- Instrumentation code для KPI.
- Скрипт генерации metrics report.
- Baseline и регулярные отчёты.

---

## Критерии приёмки

- [ ] Все KPI собираются автоматически.
- [ ] Отчёт по метрикам генерируется в воспроизводимом формате.
- [ ] Baseline зафиксирован и используется для сравнения.
- [ ] Деградации KPI детектируются и сигнализируются.

## Проверка полноты

- [ ] Для каждого KPI указан источник, способ расчёта и владелец.
- [ ] Метрики доступны без ручного сбора данных.
