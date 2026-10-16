$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'AndroidSmokeTools.ps1')
$null=Adb-Run @('shell','am','start','-W','-n','cn.local.smstone/.MainActivity','-f','0x04000000')
Click-Node (Find-TextWithScroll '编辑')
Click-Node (Find-TextWithScroll '选择系统铃声')
$text=(Get-UiText)-join "`n"
foreach ($required in '‹ 返回','取消','确定','试听所选') {
    if ($text -notmatch [regex]::Escape($required)) {throw "FAIL: missing system ringtone control: $required"}
}
Write-Output 'PASS: system ringtone picker has visible return, cancel, audition and confirm controls'
Click-Text 'Adara'
Click-Text '取消'
$text=(Get-UiText)-join "`n"
if ($text -notmatch 'System alarm') {throw 'FAIL: cancel changed original sound'}
Write-Output 'PASS: selecting then cancelling retains the original sound'
Click-Node (Find-TextWithScroll '选择系统铃声')
Click-Text 'Aldebaran'
Click-Text '‹ 返回'
$text=(Get-UiText)-join "`n"
if ($text -notmatch 'System alarm') {throw 'FAIL: return changed original sound'}
Write-Output 'PASS: return retains the original sound'
Click-Node (Find-TextWithScroll '选择系统铃声')
Click-Text 'Adara'
# The confirmation bar must remain reachable even after scrolling the ringtone list.
$null=Adb-Run @('shell','input','swipe','750','1700','750','700','350')
$text=(Get-UiText)-join "`n"
if ($text -notmatch '确定') {throw 'FAIL: confirm scrolled out of view'}
Save-TestScreenshot 'system-picker031'
Click-Text '确定'
$text=(Get-UiText)-join "`n"
if ($text -notmatch 'Adara') {throw 'FAIL: confirm did not apply the sound'}
Click-Text '保存规则'
$xml=(Adb-Run @('shell','cat','/data/user/0/cn.local.smstone/shared_prefs/rules.xml'))-join ''
if ($xml -notmatch 'Adara') {throw 'FAIL: confirmed sound was not saved'}
Write-Output 'PASS: explicit confirm and save apply the selected ringtone'
