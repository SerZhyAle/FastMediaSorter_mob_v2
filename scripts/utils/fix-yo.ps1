# fix-yo.ps1 - Replace E with Yo where required in Russian text. \b works for Cyrillic.
# Usage: .\scripts\utils\fix-yo.ps1 [-Apply] [-Verbose] [-File "path"]
param([string]$File="",[switch]$Apply,[switch]$Verbose)
$root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$pairs = @(
,@('\bеще\b','ещё'),@('\bЕще\b','Ещё'),@('\bЕЩЕ\b','ЕЩЁ')
,@('\bдает\b','даёт'),@('\bДает\b','Даёт')
,@('\bпередает\b','передаёт'),@('\bПередает\b','Передаёт')
,@('\bзадает\b','задаёт'),@('\bЗадает\b','Задаёт')
,@('\bотдает\b','отдаёт'),@('\bОтдает\b','Отдаёт')
,@('\bвыдает\b','выдаёт'),@('\bВыдает\b','Выдаёт')
,@('\bсоздает\b','создаёт'),@('\bСоздает\b','Создаёт')
,@('\bсдает\b','сдаёт'),@('\bСдает\b','Сдаёт')
,@('\bидет\b','идёт'),@('\bИдет\b','Идёт')
,@('\bнесет\b','несёт'),@('\bНесет\b','Несёт')
,@('\bвезет\b','везёт'),@('\bВезет\b','Везёт')
,@('\bберет\b','берёт'),@('\bБерет\b','Берёт')
,@('\bведет\b','ведёт'),@('\bВедет\b','Ведёт')
,@('\bпоет\b','поёт'),@('\bПоет\b','Поёт')
,@('\bпошел\b','пошёл'),@('\bПошел\b','Пошёл')
,@('\bнашел\b','нашёл'),@('\bНашел\b','Нашёл')
,@('\bзашел\b','зашёл'),@('\bЗашел\b','Зашёл')
,@('\bпришел\b','пришёл'),@('\bПришел\b','Пришёл')
,@('\bперешел\b','перешёл'),@('\bПерешел\b','Перешёл')
,@('\bподошел\b','подошёл'),@('\bПодошел\b','Подошёл')
,@('\bотошел\b','отошёл'),@('\bОтошел\b','Отошёл')
,@('\bдошел\b','дошёл'),@('\bДошел\b','Дошёл')
,@('\bпрошел\b','прошёл'),@('\bПрошел\b','Прошёл')
,@('\bвошел\b','вошёл'),@('\bВошел\b','Вошёл')
,@('\bушел\b','ушёл'),@('\bУшел\b','Ушёл')
,@('\bнес\b','нёс'),@('\bНес\b','Нёс')
,@('\bвел\b','вёл'),@('\bВел\b','Вёл')
,@('\bтемный\b','тёмный'),@('\bтемная\b','тёмная'),@('\bтемное\b','тёмное')
,@('\bтемные\b','тёмные'),@('\bтемного\b','тёмного'),@('\bтемному\b','тёмному')
,@('\bтемном\b','тёмном'),@('\bтемной\b','тёмной'),@('\bтемных\b','тёмных')
,@('\bтемными\b','тёмными'),@('\bтемнее\b','тёмнее')
,@('\bТемный\b','Тёмный'),@('\bТемная\b','Тёмная'),@('\bТемное\b','Тёмное')
,@('\bТемные\b','Тёмные'),@('\bТемного\b','Тёмного'),@('\bТемному\b','Тёмному')
,@('\bТемном\b','Тёмном'),@('\bТемной\b','Тёмной'),@('\bТемных\b','Тёмных')
,@('\bТемными\b','Тёмными'),@('\bТемнее\b','Тёмнее')
,@('\bчерный\b','чёрный'),@('\bчерная\b','чёрная'),@('\bчерное\b','чёрное')
,@('\bчерные\b','чёрные'),@('\bчерного\b','чёрного'),@('\bчерному\b','чёрному')
,@('\bчерном\b','чёрном'),@('\bчерной\b','чёрной'),@('\bчерных\b','чёрных')
,@('\bчерными\b','чёрными')
,@('\bЧерный\b','Чёрный'),@('\bЧерная\b','Чёрная'),@('\bЧерное\b','Чёрное')
,@('\bЧерные\b','Чёрные'),@('\bЧерного\b','Чёрного'),@('\bЧерному\b','Чёрному')
,@('\bЧерном\b','Чёрном'),@('\bЧерной\b','Чёрной')
,@('\bжесткий\b','жёсткий'),@('\bжесткая\b','жёсткая'),@('\bжесткое\b','жёсткое')
,@('\bжесткие\b','жёсткие'),@('\bжесткого\b','жёсткого'),@('\bжесткому\b','жёсткому')
,@('\bжестком\b','жёстком'),@('\bжесткой\b','жёсткой'),@('\bжестких\b','жёстких')
,@('\bжесткими\b','жёсткими'),@('\bжестко\b','жёстко')
,@('\bжесткость\b','жёсткость'),@('\bжесткости\b','жёсткости'),@('\bжесткостью\b','жёсткостью')
,@('\bЖесткий\b','Жёсткий'),@('\bЖесткая\b','Жёсткая'),@('\bЖесткое\b','Жёсткое')
,@('\bЖесткие\b','Жёсткие'),@('\bЖестко\b','Жёстко')
,@('\bЖесткость\b','Жёсткость'),@('\bЖесткости\b','Жёсткости')
,@('\bтяжелый\b','тяжёлый'),@('\bтяжелая\b','тяжёлая'),@('\bтяжелое\b','тяжёлое')
,@('\bтяжелые\b','тяжёлые'),@('\bтяжелого\b','тяжёлого'),@('\bтяжелому\b','тяжёлому')
,@('\bтяжелом\b','тяжёлом'),@('\bтяжелой\b','тяжёлой'),@('\bтяжелых\b','тяжёлых')
,@('\bтяжело\b','тяжёло')
,@('\bТяжелый\b','Тяжёлый'),@('\bТяжелая\b','Тяжёлая'),@('\bТяжелое\b','Тяжёлое')
,@('\bТяжелые\b','Тяжёлые'),@('\bТяжелого\b','Тяжёлого'),@('\bТяжело\b','Тяжёло')
,@('\bобъем\b','объём'),@('\bобъема\b','объёма'),@('\bобъему\b','объёму')
,@('\bобъемом\b','объёмом'),@('\bобъемный\b','объёмный'),@('\bобъемная\b','объёмная')
,@('\bобъемное\b','объёмное'),@('\bобъемные\b','объёмные'),@('\bобъемных\b','объёмных')
,@('\bОбъем\b','Объём'),@('\bОбъема\b','Объёма'),@('\bОбъему\b','Объёму')
,@('\bОбъемом\b','Объёмом'),@('\bОбъемный\b','Объёмный')
,@('\bприем\b','приём'),@('\bприема\b','приёма'),@('\bприему\b','приёму')
,@('\bприемом\b','приёмом'),@('\bприемы\b','приёмы')
,@('\bприемник\b','приёмник'),@('\bприемника\b','приёмника')
,@('\bПрием\b','Приём'),@('\bПриема\b','Приёма'),@('\bПриемник\b','Приёмник')
,@('\bтвердый\b','твёрдый'),@('\bтвердая\b','твёрдая'),@('\bтвердое\b','твёрдое')
,@('\bтвердые\b','твёрдые'),@('\bтвердо\b','твёрдо')
,@('\bТвердый\b','Твёрдый'),@('\bТвердая\b','Твёрдая'),@('\bТвердое\b','Твёрдое')
,@('\bТвердые\b','Твёрдые'),@('\bТвердо\b','Твёрдо')
)
function Apply-Yo([string]$t){foreach($p in $pairs){$t=[regex]::Replace($t,$p[0],$p[1])};return $t}
$rootSep=$root+"\"
$xmlT=@("$root\app_v2\src\main\res\values-ru\strings.xml","$root\app_v2\src\main\res\values-uk\strings.xml","$root\wear\src\main\res\values-ru\strings.xml","$root\wear\src\main\res\values-uk\strings.xml")
$mdT=@(Get-ChildItem "$root\docs" -Filter "*_RU.md"|%{$_.FullName})+@(Get-ChildItem "$root\docs" -Filter "*_UK.md"|%{$_.FullName})
if($File-ne""){$xmlT=@();$mdT=@($File)}
$total=0
function Fix-Xml([string]$path){
  if(-not(Test-Path $path)){return}
  $enc=[System.Text.Encoding]::UTF8
  $raw=[System.IO.File]::ReadAllText($path,$enc)
  $c=0
  $new=[regex]::Replace($raw,'(?<=<string[^>]*>)(.*?)(?=</string>)',{param($m)$o=$m.Value;$f=Apply-Yo $o;if($f-ne$o){$script:c++};return $f},[System.Text.RegularExpressions.RegexOptions]::Singleline)
  if($new-ne$raw){
    $rel=$path.Substring($script:rootSep.Length)
    Write-Host "[XML] $rel - $c string(s)" -ForegroundColor Cyan
    if($Verbose){$a=$raw-split"`n";$b=$new-split"`n";for($i=0;$i-lt$a.Count;$i++){if($a[$i]-ne$b[$i]){Write-Host "  - $($a[$i].Trim())" -ForegroundColor Red;Write-Host "  + $($b[$i].Trim())" -ForegroundColor Green}}}
    if($Apply){[System.IO.File]::WriteAllText($path,$new,$enc)}
    $script:total+=$c
  }
}
function Fix-Md([string]$path){
  if(-not(Test-Path $path)){return}
  $enc=[System.Text.Encoding]::UTF8
  $lines=[System.IO.File]::ReadAllLines($path,$enc)
  $inCode=$false;$changed=0
  $out=for($i=0;$i-lt$lines.Count;$i++){
    $ln=$lines[$i]
    if($ln-match'^```'){$inCode=-not$inCode}
    if($inCode-or$ln-match'^```'){$ln;continue}
    if($ln-match'^(\s{4}|\t)'){$ln;continue}
    $segs=$ln-split'(`[^`]*`)'
    $nl=($segs|%{if($_-match'^`.*`$'){$_}else{Apply-Yo $_}})-join''
    if($nl-ne$ln){$changed++;if($Verbose){Write-Host "  L$($i+1) - $($ln.Trim())" -ForegroundColor Red;Write-Host "  L$($i+1) + $($nl.Trim())" -ForegroundColor Green}}
    $nl
  }
  if($changed-gt0){
    $rel=$path.Substring($script:rootSep.Length)
    Write-Host "[MD ] $rel - $changed line(s)" -ForegroundColor Cyan
    if($Apply){[System.IO.File]::WriteAllLines($path,$out,$enc)}
    $script:total+=$changed
  }
}
Write-Host "";if(-not$Apply){Write-Host "DRY RUN - use -Apply to write." -ForegroundColor Yellow}
Write-Host "Loaded $($pairs.Count) rules. Processing..." -ForegroundColor Gray
foreach($f in $xmlT){Fix-Xml $f}
foreach($f in $mdT){Fix-Md $f}
Write-Host ""
if($total-eq0){Write-Host "All Yo correct." -ForegroundColor Green}elseif($Apply){Write-Host "Done. Changed: $total." -ForegroundColor Green}else{Write-Host "Would change $total. Run with -Apply." -ForegroundColor Yellow}