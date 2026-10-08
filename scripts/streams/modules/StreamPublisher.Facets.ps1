# Language and country folding for the stream catalog publisher (STREAM-BANK 2.3, amendment O).
#
# Contract: `language` holds names of existing languages from a closed vocabulary (ISO 639-1 plus a named
# ISO 639-3 extension), one entry per language; a non-blank cell in which nothing is recognized becomes
# `english`; a blank cell stays blank. `country` holds an officially assigned ISO 3166-1 alpha-2 code or is
# blank. The Kotlin side (domain/streams/facets) implements the same tables, and both are held to
# app_v2/src/test/resources/streams/facet-golden.tsv.
#
# Canonical language names are fixed by the table below and never read from the platform: platform English
# names differ by version, and an id that moves with the device breaks every stored selection.

# ISO 639-1: code, then the fixed canonical lowercase English name.
$script:FacetIso6391 = @'
aa afar
ab abkhazian
ae avestan
af afrikaans
ak akan
am amharic
an aragonese
ar arabic
as assamese
av avaric
ay aymara
az azerbaijani
ba bashkir
be belarusian
bg bulgarian
bh bihari
bi bislama
bm bambara
bn bengali
bo tibetan
br breton
bs bosnian
ca catalan
ce chechen
ch chamorro
co corsican
cr cree
cs czech
cu church slavic
cv chuvash
cy welsh
da danish
de german
dv divehi
dz dzongkha
ee ewe
el greek
en english
eo esperanto
es spanish
et estonian
eu basque
fa persian
ff fulah
fi finnish
fj fijian
fo faroese
fr french
fy western frisian
ga irish
gd scottish gaelic
gl galician
gn guarani
gu gujarati
gv manx
ha hausa
he hebrew
hi hindi
ho hiri motu
hr croatian
ht haitian
hu hungarian
hy armenian
hz herero
ia interlingua
id indonesian
ie interlingue
ig igbo
ii sichuan yi
ik inupiaq
io ido
is icelandic
it italian
iu inuktitut
ja japanese
jv javanese
ka georgian
kg kongo
ki kikuyu
kj kuanyama
kk kazakh
kl kalaallisut
km khmer
kn kannada
ko korean
kr kanuri
ks kashmiri
ku kurdish
kv komi
kw cornish
ky kyrgyz
la latin
lb luxembourgish
lg luganda
li limburgish
ln lingala
lo lao
lt lithuanian
lu luba-katanga
lv latvian
mg malagasy
mh marshallese
mi maori
mk macedonian
ml malayalam
mn mongolian
mr marathi
ms malay
mt maltese
my burmese
na nauru
nb norwegian bokmal
nd north ndebele
ne nepali
ng ndonga
nl dutch
nn norwegian nynorsk
no norwegian
nr south ndebele
nv navajo
ny chichewa
oc occitan
oj ojibwa
om oromo
or odia
os ossetian
pa punjabi
pi pali
pl polish
ps pashto
pt portuguese
qu quechua
rm romansh
rn kirundi
ro romanian
ru russian
rw kinyarwanda
sa sanskrit
sc sardinian
sd sindhi
se northern sami
sg sango
si sinhala
sk slovak
sl slovenian
sm samoan
sn shona
so somali
sq albanian
sr serbian
ss swati
st southern sotho
su sundanese
sv swedish
sw swahili
ta tamil
te telugu
tg tajik
th thai
ti tigrinya
tk turkmen
tl tagalog
tn tswana
to tongan
tr turkish
ts tsonga
tt tatar
tw twi
ty tahitian
ug uyghur
uk ukrainian
ur urdu
uz uzbek
ve venda
vi vietnamese
vo volapuk
wa walloon
wo wolof
xh xhosa
yi yiddish
yo yoruba
za zhuang
zh chinese
zu zulu
'@

# ISO 639-3 extension: languages present in the bank that have no two-letter code.
$script:FacetIso6393Extension = @'
bgc haryanvi
cnr montenegrin
esn esan
haw hawaiian
hne chhattisgarhi
jam jamaican patois
kok konkani
mfe mauritian creole
nso northern sotho
pap papiamento
rue rusyn
tcs torres strait creole
tzm tamazight
zea zeelandic
'@

# Alias | canonical name. Keys are folded (lowercase, no diacritics) the same way input is.
$script:FacetLanguageAliases = @'
american english|english
engilsh|english
englsh|english
englisgh|english
ingles|english
deutsch|german
gernan|german
gerrnan|german
norddeutsch|german
espanol|spanish
espanish|spanish
spsnish|spanish
castellano|spanish
portugues|portuguese
portoguese|portuguese
pt-br|portuguese
francais|french
francaise|french
francais - letzebuergesch|french,luxembourgish
letzebuergesch|luxembourgish
turkce|turkish
arapca|arabic
arabi|arabic
romana|romanian
japones|japanese
bulgarien|bulgarian
greel|greek
gr|greek
yeruoba|yoruba
galego|galician
kurdi|kurdish
flammish|dutch
flemish|dutch
filipino|tagalog
kiswahili|swahili
fulani|fulah
farsi|persian
bangla|bengali
sepedi|northern sotho
sesotho|southern sotho
sotho|southern sotho
setswana|tswana
siswati|swati
xitsonga|tsonga
tshivenda|venda
isindebele|south ndebele
isixhosa|xhosa
isizulu|zulu
gaelic|scottish gaelic
papiamentu|papiamento
jamaican|jamaican patois
haiti creole|haitian
sami|northern sami
bahasa indonesia|indonesian
bahasa melayu|malay
japan|japanese
korea|korean
estonia|estonian
holland|dutch
nederland|dutch
italiano|italian
polski|polish
nederlands|dutch
svenska|swedish
norsk|norwegian
dansk|danish
suomi|finnish
magyar|hungarian
cestina|czech
hrvatski|croatian
srpski|serbian
slovenscina|slovenian
русский|russian
українська|ukrainian
ελληνικά|greek
عربي|arabic
العربية|arabic
فارسی|persian
עברית|hebrew
हिन्दी|hindi
ภาษาไทย|thai
中文|chinese
日本語|japanese
한국어|korean
'@

# ISO 3166-1 alpha-2, officially assigned codes only (user-assigned ones such as XX are not in it).
$script:FacetAssignedCountries = @'
AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ
CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO
FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE
JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO
MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW
PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM
TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW
'@

# Country names and aliases that .NET region tables do not carry, or carry under another spelling.
$script:FacetCountryAliases = @'
uk|GB
usa|US
united states of america|US
czech republic|CZ
macedonia|MK
the russian federation|RU
russian federation|RU
great britain|GB
wales|GB
england|GB
scotland|GB
northern ireland|GB
'@

function ConvertTo-FacetKey {
    param([string]$Text)
    $decomposed = ($Text ?? '').Normalize([System.Text.NormalizationForm]::FormD)
    $builder = [System.Text.StringBuilder]::new()
    foreach ($ch in $decomposed.ToCharArray()) {
        $category = [System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($ch)
        if ($category -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) { [void]$builder.Append($ch) }
    }
    $key = $builder.ToString().ToLowerInvariant() -replace '[‐-―]', '-' -replace '\s+', ' '
    return $key.Trim()
}

function Get-FacetLanguageTables {
    if ($script:FacetLanguageTablesBuilt) { return $script:FacetLanguageTablesBuilt }
    $names = [System.Collections.Generic.Dictionary[string, string]]::new()
    $codes = [System.Collections.Generic.Dictionary[string, string]]::new()
    $canonical = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($line in ($script:FacetIso6391 -split "\r?\n")) {
        if (-not $line.Trim()) { continue }
        $code, $name = $line.Trim() -split ' ', 2
        $codes[$code] = $name
        $names[(ConvertTo-FacetKey $name)] = $name
        [void]$canonical.Add($name)
    }
    foreach ($line in ($script:FacetIso6393Extension -split "\r?\n")) {
        if (-not $line.Trim()) { continue }
        $code, $name = $line.Trim() -split ' ', 2
        $names[(ConvertTo-FacetKey $name)] = $name
        [void]$canonical.Add($name)
    }
    foreach ($line in ($script:FacetLanguageAliases -split "\r?\n")) {
        if (-not $line.Trim()) { continue }
        $alias, $target = $line.Trim() -split '\|', 2
        $names[(ConvertTo-FacetKey $alias)] = $target
    }
    $script:FacetLanguageTablesBuilt = @{ Names = $names; Codes = $codes; Canonical = $canonical }
    return $script:FacetLanguageTablesBuilt
}

function Resolve-FacetLanguagePiece {
    param([string]$Piece)
    $tables = Get-FacetLanguageTables
    $key = (ConvertTo-FacetKey $Piece) -replace '^[#\s\.\-_:;,!?*"''()]+|[\s\.\-_:;,!?*"''()]+$', ''
    if (-not $key) { return @() }
    if ($tables.Names.ContainsKey($key)) { return @($tables.Names[$key] -split ',') }
    if ($key.Length -eq 2 -and $tables.Codes.ContainsKey($key)) { return @($tables.Codes[$key]) }
    $words = @($key -split '[\s\-\.:()/_&+]+' | Where-Object { $_ })
    $found = [System.Collections.Generic.List[string]]::new()
    $i = 0
    while ($i -lt $words.Count) {
        $matched = $false
        $longest = [Math]::Min(3, $words.Count - $i)
        for ($len = $longest; $len -ge 1; $len--) {
            $phrase = $words[$i..($i + $len - 1)] -join ' '
            if ($tables.Names.ContainsKey($phrase)) {
                foreach ($name in ($tables.Names[$phrase] -split ',')) { $found.Add($name) }
                $i += $len
                $matched = $true
                break
            }
        }
        if (-not $matched) { $i++ }
    }
    return @($found)
}

function Get-CanonicalLanguages {
    param([string]$Languages)
    $raw = ($Languages ?? '').Trim()
    if (-not $raw -or $raw -notmatch '[\p{L}\p{N}]') { return '' }
    $found = [System.Collections.Generic.List[string]]::new()
    foreach ($piece in ($raw -split '[,;/|]')) {
        foreach ($name in (Resolve-FacetLanguagePiece -Piece $piece)) {
            if (-not $found.Contains($name)) { $found.Add($name) }
        }
    }
    if ($found.Count -eq 0) { return 'english' }
    return ($found -join ',')
}

function Get-FacetCountryTables {
    if ($script:FacetCountryTablesBuilt) { return $script:FacetCountryTablesBuilt }
    $assigned = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($code in ($script:FacetAssignedCountries -split '\s+')) {
        if ($code) { [void]$assigned.Add($code) }
    }
    $names = [System.Collections.Generic.Dictionary[string, string]]::new()
    foreach ($culture in [System.Globalization.CultureInfo]::GetCultures(
            [System.Globalization.CultureTypes]::SpecificCultures)) {
        try {
            $region = [System.Globalization.RegionInfo]::new($culture.Name)
            foreach ($name in @($region.EnglishName, $region.NativeName, $region.DisplayName)) {
                $key = ConvertTo-FacetKey $name
                if ($key) { $names[$key] = $region.TwoLetterISORegionName }
            }
        } catch {
            # A culture without a region is not a catalogue value and contributes no name.
        }
    }
    foreach ($line in ($script:FacetCountryAliases -split "\r?\n")) {
        if (-not $line.Trim()) { continue }
        $alias, $code = $line.Trim() -split '\|', 2
        $names[(ConvertTo-FacetKey $alias)] = $code
    }
    $script:FacetCountryTablesBuilt = @{ Assigned = $assigned; Names = $names }
    return $script:FacetCountryTablesBuilt
}

function Get-CanonicalCountry {
    param([string]$Country)
    $raw = ($Country ?? '').Trim()
    if (-not $raw) { return '' }
    $tables = Get-FacetCountryTables
    $upper = $raw.ToUpperInvariant()
    if ($tables.Assigned.Contains($upper)) { return $upper }
    $code = $null
    if ($tables.Names.TryGetValue((ConvertTo-FacetKey $raw), [ref]$code) -and $tables.Assigned.Contains($code)) {
        return $code
    }
    return ''
}
