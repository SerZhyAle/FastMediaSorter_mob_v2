# Verification harness for S1544 steps 01.1 and 01.2.
# Exit codes: 0 every case passed. 1 at least one case failed.

# Durable evidence for S1544 phase 01. Run from the repo root:
#   pwsh -NoProfile -File PLAN/S1544_house-style-unenforced-where-it-applies/evidence/test-house-text-style.ps1
param([string]$LibPath = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '..\scripts\quality\lib\house-text-style.ps1'))

$ErrorActionPreference = 'Stop'
. $LibPath

$fails = 0
function Check {
    param([string]$Name, [string]$Actual, [string]$Expected)
    if ($Actual -ceq $Expected) {
        Write-Host ("  PASS  {0}" -f $Name)
    } else {
        Write-Host ("  FAIL  {0}`n        expected: [{1}]`n        actual  : [{2}]" -f $Name, $Expected, $Actual)
        $script:fails++
    }
}

Write-Host 'ResourceValue area'
Check 'plain hyphen survives'      (Convert-HouseStyleText -Text 'a - b' -Area ResourceValue).Text 'a - b'
Check 'en dash to hyphen'          (Convert-HouseStyleText -Text 'PIN (4–6)' -Area ResourceValue).Text 'PIN (4-6)'
Check 'em dash to hyphen'          (Convert-HouseStyleText -Text 'A — B' -Area ResourceValue).Text 'A - B'
Check 'three dots to two'          (Convert-HouseStyleText -Text 'Loading...' -Area ResourceValue).Text 'Loading..'
Check 'unicode ellipsis to two'    (Convert-HouseStyleText -Text 'Loading…' -Area ResourceValue).Text 'Loading..'
Check 'double unicode ellipsis'    (Convert-HouseStyleText -Text 'Loading……' -Area ResourceValue).Text 'Loading..'
Check 'existing two dots untouched' (Convert-HouseStyleText -Text 'Loading..' -Area ResourceValue).Text 'Loading..'
Check 'url untouched'              (Convert-HouseStyleText -Text 'https://x/.../y' -Area ResourceValue).Text 'https://x/.../y'
Check 'path untouched'             (Convert-HouseStyleText -Text '/storage/.../file' -Area ResourceValue).Text '/storage/.../file'
Check 'placeholder only untouched' (Convert-HouseStyleText -Text '%1$s' -Area ResourceValue).Text '%1$s'
Check 'placeholder in prose fixed' (Convert-HouseStyleText -Text 'Copying %1$s...' -Area ResourceValue).Text 'Copying %1$s..'
# CJK sets no spaces, so a Chinese sentence containing a slash must not read as a file path.
$cjkSlash = [char]0x952E + [string][char]0x76D8 + '/' + [string][char]0x9F20 + [string][char]0x6807 + '...'
$cjkSlashFixed = [char]0x952E + [string][char]0x76D8 + '/' + [string][char]0x9F20 + [string][char]0x6807 + '..'
Check 'cjk sentence with slash fixed' (Convert-HouseStyleText -Text $cjkSlash -Area ResourceValue).Text $cjkSlashFixed
Check 'doubled em dash collapses to one' (Convert-HouseStyleText -Text ('A' + [string][char]0x2014 + [string][char]0x2014 + 'B') -Area ResourceValue).Text 'A-B'

Write-Host 'Prose area'
$backtick = [char]0x60
Check 'inline code untouched' (Convert-HouseStyleText -Text ("{0}a...b{0}" -f $backtick) -Area Prose).Text ("{0}a...b{0}" -f $backtick)
Check 'plain prose fixed'     (Convert-HouseStyleText -Text 'well then... yes' -Area Prose).Text 'well then.. yes'
$fenced = ("{0}{0}{0}`nx...y`n{0}{0}{0}" -f $backtick)
Check 'fenced block untouched' (Convert-HouseStyleText -Text $fenced -Area Prose).Text $fenced
Check 'indented block untouched' (Convert-HouseStyleText -Text "    x...y" -Area Prose).Text "    x...y"

Write-Host 'Yo rule'
Check 'yo applied when selected'  (Convert-HouseStyleText -Text 'еще темный' -Area ResourceValue -Rules 'yo').Text 'ещё тёмный'
Check 'yo skipped when not asked' (Convert-HouseStyleText -Text 'еще темный' -Area ResourceValue -Rules 'ellipsis','long-dash').Text 'еще темный'
Check 'yo title case'             (Convert-HouseStyleText -Text 'Еще' -Area ResourceValue -Rules 'yo').Text 'Ещё'
Check 'ambiguous vse untouched'   (Convert-HouseStyleText -Text 'все файлы' -Area ResourceValue -Rules 'yo').Text 'все файлы'
Check 'tyazhelo untouched'        (Convert-HouseStyleText -Text 'тяжело' -Area ResourceValue -Rules 'yo').Text 'тяжело'
Check 'substring not touched'     (Convert-HouseStyleText -Text 'темнее' -Area ResourceValue -Rules 'yo').Text 'темнее'

Write-Host 'Reporting'
$r = Convert-HouseStyleText -Text 'A — B...' -Area ResourceValue
Check 'rules fired listed' (($r.RulesFired | Sort-Object) -join ',') 'ellipsis,long-dash'
Check 'changed flag true'  ([string]$r.Changed) 'True'
$r2 = Convert-HouseStyleText -Text 'clean text' -Area ResourceValue
Check 'changed flag false' ([string]$r2.Changed) 'False'
Check 'no rules fired'     (($r2.RulesFired) -join ',') ''

Write-Host 'Unknown rule name'
try {
    Convert-HouseStyleText -Text 'x' -Area Prose -Rules 'nosuchrule' | Out-Null
    Write-Host '  FAIL  unknown rule name did not throw'
    $fails++
} catch {
    Write-Host '  PASS  unknown rule name throws'
}

Write-Host ''
if ($fails -eq 0) { Write-Host 'house-text-style: ALL PASS' -ForegroundColor Green; exit 0 }
Write-Error "house-text-style: $fails case(s) failed" -ErrorAction Continue
exit 1

