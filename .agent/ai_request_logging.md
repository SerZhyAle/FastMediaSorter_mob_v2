# AI Request Logger

## Rule
Every AI request must be logged to `./temp/ai_prompts.xml` with timestamp and request text.

## Implementation
At the start of each conversation, append the user request to the XML log file.

## Format
```xml
<prompt>
    <datetime>YYYY-MM-DDTHH:MM:SS+TZ</datetime>
    <request><![CDATA[user request text]]></request>
    <status>in_progress|completed</status>
</prompt>
```

## Priority
This rule should execute BEFORE any other task work begins.
