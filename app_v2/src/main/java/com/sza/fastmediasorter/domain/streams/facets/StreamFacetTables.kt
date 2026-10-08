package com.sza.fastmediasorter.domain.streams.facets

/**
 * Closed vocabularies of the stream catalog's `language` and `country` columns (STREAM-BANK 2.3,
 * amendment O). The tables are the same text as the publisher module `StreamPublisher.Facets.ps1`; a unit
 * test compares the two copies, so a table edited on one side only is a red build instead of a catalog
 * whose old and new copies disagree.
 *
 * Canonical language names are fixed here and never read from `Locale`: platform English names differ by
 * ICU version, and an id that moves with the device breaks every stored filter selection. The platform is
 * used only to draw a label for the user.
 *
 * Pure Kotlin on purpose: the watch module compiles this package from the phone tree (like the FD-SEC
 * package), which is legal only while nothing here imports Android, Hilt or an app package.
 */
object StreamFacetTables {

    /** ISO 639-1: code, then the fixed canonical lowercase English name. */
    internal const val ISO_639_1_TABLE = """
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
"""

    /** ISO 639-3 extension: languages present in the bank that have no two-letter code. */
    internal const val ISO_639_3_EXTENSION_TABLE = """
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
"""

    /** Alias and canonical name(s), separated by a vertical bar; a comma in the target means several languages. */
    internal const val LANGUAGE_ALIAS_TABLE = """
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
"""

    /** ISO 3166-1 alpha-2, officially assigned codes only (user-assigned ones such as XX are not in it). */
    internal const val ASSIGNED_COUNTRY_TABLE = """
AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ
CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO
FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE
JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO
MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW
PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM
TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW
"""

    /** Country names and aliases the platform region names do not carry or spell differently. */
    internal const val COUNTRY_ALIAS_TABLE = """
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
"""
}
