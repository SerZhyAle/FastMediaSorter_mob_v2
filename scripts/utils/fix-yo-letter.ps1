# fix-yo-letter.ps1
# Restores the letter Ё/ё in Russian text where it is grammatically required.
# Uses a curated dictionary of common Russian words where е/Е should be ё/Ё.
# Safe: only replaces whole-word matches to avoid false positives.

param(
    [string[]]$Paths = @(),
    [switch]$DryRun
)

# Dictionary: е-form → ё-form (lowercase; script handles case)
# Only high-frequency, unambiguous substitutions
$yoMap = [ordered]@{
    # Common verbs and forms
    'берет'      = 'берёт'
    'берем'      = 'берём'
    'берете'     = 'берёте'
    'берешь'     = 'берёшь'
    'ведет'      = 'ведёт'
    'ведем'      = 'ведём'
    'ведете'     = 'ведёте'
    'ведешь'     = 'ведёшь'
    'везет'      = 'везёт'
    'вернет'     = 'вернёт'
    'включает'   = 'включает'   # no change needed
    'войдет'     = 'войдёт'
    'выдет'      = 'выдет'      # skip
    'выйдет'     = 'выйдет'     # skip
    'даем'       = 'даём'
    'дает'       = 'даёт'
    'дает'       = 'даёт'
    'даете'      = 'даёте'
    'даешь'      = 'даёшь'
    'делает'     = 'делает'     # no ё
    'делаем'     = 'делаем'     # no ё
    'идет'       = 'идёт'
    'идем'       = 'идём'
    'идете'      = 'идёте'
    'идешь'      = 'идёшь'
    'ждет'       = 'ждёт'
    'жжет'       = 'жжёт'
    'живет'      = 'живёт'
    'зайдет'     = 'зайдёт'
    'найдет'     = 'найдёт'
    'называет'   = 'называет'   # no ё
    'несет'      = 'несёт'
    'нет'        = 'нет'        # skip — ambiguous
    'отдает'     = 'отдаёт'
    'передает'   = 'передаёт'
    'пишет'      = 'пишет'      # no ё
    'поет'       = 'поёт'
    'показывает' = 'показывает' # no ё
    'придет'     = 'придёт'
    'прием'      = 'приём'
    'приемник'   = 'приёмник'
    'принесет'   = 'принесёт'
    'продает'    = 'продаёт'
    'прочтет'    = 'прочтёт'
    'путем'      = 'путём'
    'разберет'   = 'разберёт'
    'сдает'      = 'сдаёт'
    'следует'    = 'следует'    # no ё
    'создает'    = 'создаёт'
    'узнает'     = 'узнаёт'

    # Nouns / adjectives
    'бережет'    = 'бережёт'
    'везде'      = 'везде'      # no ё
    'дорогой'    = 'дорогой'    # no ё
    'еж'         = 'ёж'
    'елка'       = 'ёлка'
    'емкость'    = 'ёмкость'
    'емкостей'   = 'ёмкостей'
    'емкостью'   = 'ёмкостью'
    'еще'        = 'ещё'
    'желтый'     = 'жёлтый'
    'жесткий'    = 'жёсткий'
    'жесткая'    = 'жёсткая'
    'жесткие'    = 'жёсткие'
    'жестко'     = 'жёстко'
    'жесткость'  = 'жёсткость'
    'зеленый'    = 'зелёный'
    'зеленая'    = 'зелёная'
    'зеленые'    = 'зелёные'
    'клен'       = 'клён'
    'нажатием'   = 'нажатием'   # no ё
    'немного'    = 'немного'    # no ё
    'номер'      = 'номер'      # no ё
    'новый'      = 'новый'      # no ё
    'объем'      = 'объём'
    'объема'     = 'объёма'
    'объемный'   = 'объёмный'
    'одноразовый'= 'одноразовый'# no ё
    'ориентированный' = 'ориентированный' # no ё
    'пленка'     = 'плёнка'
    'поиск'      = 'поиск'      # no ё
    'подем'      = 'подъём'
    'подъем'     = 'подъём'
    'раздем'     = 'раздем'     # skip
    'разем'      = 'разъём'
    'разъем'     = 'разъём'
    'разъема'    = 'разъёма'
    'рядом'      = 'рядом'      # no ё
    'самый'      = 'самый'      # no ё
    'семья'      = 'семья'      # no ё
    'сечение'    = 'сечение'    # no ё
    'тверд'      = 'твёрд'
    'твердый'    = 'твёрдый'
    'твердая'    = 'твёрдая'
    'твердые'    = 'твёрдые'
    'твердо'     = 'твёрдо'
    'твердость'  = 'твёрдость'
    'темный'     = 'тёмный'
    'темная'     = 'тёмная'
    'темные'     = 'тёмные'
    'темно'      = 'тёмно'
    'темнота'    = 'тёмнота'
    'темнее'     = 'темнее'     # no ё — this is comparative, keep
    'тяжелый'    = 'тяжёлый'
    'тяжелая'    = 'тяжёлая'
    'тяжелые'    = 'тяжёлые'
    'тяжело'     = 'тяжело'     # no ё
    'тяжесть'    = 'тяжесть'    # no ё
    'черный'     = 'чёрный'
    'черная'     = 'чёрная'
    'черные'     = 'чёрные'
    'черно'      = 'чёрно'
    'шерсть'     = 'шерсть'     # no ё
    'щелчок'     = 'щелчок'     # no ё

    # Pronouns / particles
    'все'        = 'всё'
    'всего'      = 'всего'      # ambiguous: всего (genitive) vs всё — skip auto
    'ее'         = 'её'
    'её'         = 'её'         # already correct
    'мое'        = 'моё'
    'моего'      = 'моего'      # no ё
    'своем'      = 'своём'
    'свое'       = 'своё'
    'твоем'      = 'твоём'
    'твое'       = 'твоё'
    'чем'        = 'чем'        # skip — ambiguous (instrumental)
    'что'        = 'что'        # no ё

    # Common in app context
    'видео'      = 'видео'      # no ё
    'режим'      = 'режим'      # no ё
    'решение'    = 'решение'    # no ё
    'сведения'   = 'сведения'   # no ё
    'удаление'   = 'удаление'   # no ё
    'управление' = 'управление' # no ё
}

# Remove no-change entries
$replacements = [ordered]@{}
foreach ($kv in $yoMap.GetEnumerator()) {
    if ($kv.Key -ne $kv.Value -and $kv.Value -notmatch '^$') {
        $replacements[$kv.Key] = $kv.Value
    }
}

function Apply-YoFix {
    param([string]$Text)
    foreach ($kv in $replacements.GetEnumerator()) {
        $lower = $kv.Key
        $fixed = $kv.Value

        # Lowercase form
        $Text = [regex]::Replace($Text, "(?<!\p{L})$([regex]::Escape($lower))(?!\p{L})", $fixed)

        # Title case form
        $lowerTitle = $lower.Substring(0,1).ToUpper() + $lower.Substring(1)
        $fixedTitle = $fixed.Substring(0,1).ToUpper() + $fixed.Substring(1)
        $Text = [regex]::Replace($Text, "(?<!\p{L})$([regex]::Escape($lowerTitle))(?!\p{L})", $fixedTitle)

        # Uppercase form
        $Text = [regex]::Replace($Text, "(?<!\p{L})$([regex]::Escape($lower.ToUpper()))(?!\p{L})", $fixed.ToUpper())
    }
    return $Text
}

foreach ($p in $Paths) {
    if (-not (Test-Path $p)) { Write-Warning "Not found: $p"; continue }

    $raw = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
    $updated = Apply-YoFix $raw

    if ($raw -eq $updated) {
        Write-Host "[no change] $p"
    } elseif ($DryRun) {
        Write-Host "[dry-run]   $p — changes detected"
    } else {
        [System.IO.File]::WriteAllText($p, $updated, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[done]      $p"
    }
}
