# User Custom Rules for AI Agents

## Request Logging Rule

**Priority**: HIGH  
**Applies to**: All AI agents in this project

### Rule Description
Every user request to any AI agent must be automatically logged to `./temp/ai_prompts.xml` with:
- Request text
- Timestamp (ISO 8601 format with timezone)
- Status (in_progress/completed)

### Implementation
Use the logging script: `.\scripts\log-ai-request.ps1 -Request "text" -DateTime "ISO8601"`

### Example
```powershell
.\scripts\log-ai-request.ps1 -Request "Fix the bug in MainActivity" -DateTime "2026-02-11T02:56:52+01:00"
```

### Log File Format
```xml
<?xml version="1.0" encoding="UTF-8"?>
<ai_prompts>
    <prompt>
        <datetime>2026-02-11T02:56:52+01:00</datetime>
        <request><![CDATA[user request text]]></request>
        <status>in_progress</status>
    </prompt>
</ai_prompts>
```

### Execution Priority
This should execute at the START of each conversation, before any task work begins.

---

**Note**: The AI agent should call this script as the first action when receiving a new user request.
