# S1919 - что именно показала проверка на устройстве, и как её повторить

Снято 2026-08-21 на эмуляторе телефона `emulator-5556` (Android 13), собственная сборка тикета `2.60.8212.313-DEBUG`, экран `Настройки > Общие`, раздел «General interface settings» развёрнут.

## Вердикт

Иконка лаунчера рисуется в обеих строках. Выдержка из дерева `01__settings-general-uitree-landscape.xml`:

```xml
<node resource-id=".../rowLauncherModeEnabled" content-desc="Make this app the home screen" bounds="[12,471][1012,527]">
  <node resource-id=".../str_switch" bounds="[12,475][64,523]" />
  <node resource-id=".../str_icon"   bounds="[72,487][96,511]" />
  <node resource-id=".../str_textGroup" ... />
```

```xml
<node resource-id=".../ssr_icon" bounds="[12,533][36,540]" />
```

`str_icon` - это и есть слот ведущей иконки, который до тикета был пуст и потому скрыт. Он занимает 24x24 между переключателем и текстом, ровно там же, где у соседней строки «Enable Favorites» стоит звезда.

**Что доказано:** альбомная разметка. Устройство сообщает `cur=1024x600`, то есть на экране был `res/layout-land/fragment_settings_general.xml`.

**Что не доказано на устройстве:** портретная разметка. У `emulator-5556` задан `Override size: 1024x600` поверх панели `1080x2400` - его выставила другая сессия, поэтому экран не поворачивается, а менять чужую настройку устройства ради снимка неправильно. Портрет проверен статически: по одному совпадению `app:str_icon="@drawable/ic_launcher_mode"` и `app:ssr_icon="@drawable/ic_launcher_mode"` в `res/layout/fragment_settings_general.xml`, значения атрибутов совпадают с альбомным файлом дословно.

## Как повторить

```powershell
pwsh -NoProfile -File ./a.ps1 d
pwsh -NoProfile -File scripts/devtest/adb.ps1 install -DeviceId <id>
pwsh -NoProfile -File scripts/devtest/adb.ps1 launch  -DeviceId <id>
pwsh -NoProfile -File scripts/devtest/adb.ps1 tap-id -ResourceId btnSettings   -DeviceId <id>
pwsh -NoProfile -File scripts/devtest/adb.ps1 tap-label -Label "General"       -DeviceId <id>
pwsh -NoProfile -File scripts/devtest/adb.ps1 tap-id -ResourceId headerInterface -DeviceId <id>
pwsh -NoProfile -File scripts/devtest/adb.ps1 uidump -DeviceId <id> -Ids
pwsh -NoProfile -File scripts/devtest/adb.ps1 shot   -DeviceId <id>
```

Ожидание: в дереве под `rowLauncherModeEnabled` присутствует дочерний `str_icon` с ненулевыми границами, и такой же `ssr_icon` под `rowLauncherSettings`. На устройстве без `Override size` тот же прогон после поворота повторяет это для портрета.

## Проверка ключей строк

```powershell
pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_settings_"
```

Ожидание: exit 0, `all 78 key(s) present in en/ru/uk`.

Ни один из пяти изменённых ключей не является новым, поэтому в перечне непереведённых лексем их нет - перечень строится `scripts/utils/list-new-lexemes.ps1` и на момент закрытия тикета содержал 47 строк от других тикетов.
