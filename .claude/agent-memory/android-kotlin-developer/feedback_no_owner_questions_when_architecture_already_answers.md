---
name: feedback_no_owner_questions_when_architecture_already_answers
description: Don't ask owner scope/business questions whose answer is already mechanically determined by the epic's architecture; treat the architecture as already-given truth and skip the question
metadata:
  type: feedback
---

Не задавай владельцу вопросы про scope или business choices, если ответ уже жёстко вытекает из ранее зафиксированной архитектуры эпика. Если иерархия флэйворов / inheritance / contracts уже механически дают ответ - пропускай вопрос, не подаваи как «выбор».

**Why:** в S0240 (2026-05-18 02:46) при `OWNER_QUESTIONS_DRAFT` я задал Q3 «scope cloud features в VR-сборке: все провайдеры или ограниченный набор?» - но иерархия `standard` ⊂ `vr`, зафиксированная самим владельцем за 30 минут до этого, уже механически отвечала «все». Владелец прочитал вопрос как «значит, плохо разобрался в структуре задачи». Это маркер - я раздуваю интерфейс вопросов искусственно созданными choices там, где архитектура уже закрыла обсуждение.

**How to apply:**

- При реализации спеки, прежде чем останавливаться и спрашивать владельца про scope (какие флэйворы покрыть, какие классы переопределить, какие провайдеры включить) - перечитай §«Architecture»/«Flavor scope»/«Contracts» самой спеки. Если ответ механически вытекает из VR inclusion hierarchy `standard ⊂ vr ⊂ noLegal` или из подписей интерфейсов, пиши код без вопроса.
- Если в спеке есть `OWNER_QUESTIONS_DRAFT` блок - перед делегацией его обратно владельцу прогоняй каждый bullet через «архитектура уже отвечает?». Дублирующие вопросы убирай в Step Log как «resolved by §X.Y».
- Технические caveats (Google OAuth недоступен на Quest без trampoline, OpenXR runtime пропатчен на arm64-only, etc.) - это research-задача, а не owner-question.
- Допустимо записать «следствие из §X.Y: все провайдеры наследуются автоматически» в нужном месте Step Log без owner-question.

Это правило сильнее принципа «лучше спросить чем угадать»: если ответ зафиксирован архитектурой - это не догадка, а чтение спеки.
