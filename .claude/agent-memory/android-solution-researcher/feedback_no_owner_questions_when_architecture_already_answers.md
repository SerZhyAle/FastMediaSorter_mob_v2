---
name: no-owner-questions-when-architecture-already-answers
description: Don't ask owner scope/business questions whose answer is already mechanically determined by the epic's architecture; treat the architecture as already-given truth and skip the question
metadata:
  type: feedback
---

Не задавай владельцу вопросы про scope или business choices, если ответ уже жёстко вытекает из ранее зафиксированной архитектуры эпика. Если иерархия флэйворов / inheritance / contracts уже механически дают ответ - пропускай вопрос, не подавай как «выбор».

**Why:** в S0240 (2026-05-18 02:46) при `OWNER_QUESTIONS_DRAFT` я задал Q3 «scope cloud features в VR-сборке: все провайдеры или ограниченный набор?» - но иерархия `standard` ⊂ `vr`, зафиксированная самим владельцем за 30 минут до этого, уже механически отвечала «все». Владелец прочитал вопрос как «значит, плохо разобрался в структуре задачи». Это маркер - я раздуваю интерфейс вопросов искусственно созданными choices там, где архитектура уже закрыла обсуждение.

**How to apply:**

- В разделе «Open Questions for Spec Author» research-отчёта - выводи только те вопросы, на которые код, докуменация и зафиксированная архитектура эпика **не отвечают**. Перед каждым кандидатом проверь: не отвечает ли flavor hierarchy / contracts / inheritance / scope-границы механически.
- Если отвечает - сформулируй ответ как «Следствие из §X.Y / архитектуры эпика: …» в соответствующей секции отчёта (Affected Scope, BuildConfig Flags, или Data Flow), и НЕ выноси в Open Questions.
- Технические caveats (например, Google OAuth на Quest) идут в research-факты с цитатой источника, а не в owner-question.
- Если в исходном задании от родительского агента уже зафиксирована иерархия - повторное «открытие» этого выбора как вопроса владельцу = регрессия.

Это правило сильнее принципа «лучше спросить чем угадать»: если ответ зафиксирован архитектурой - это не догадка, а чтение спеки.
