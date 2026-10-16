param([switch]$Prepare, [ValidateSet('Stop','OpenSms')][string]$Action='Stop')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'AndroidSmokeTools.ps1')
$package='cn.local.smstone'
if (((Adb-Run @('shell','getprop','ro.kernel.qemu'))-join '').Trim() -ne '1' -or
    ((Adb-Run @('shell','id','-u'))-join '').Trim() -ne '0') { throw 'Use a rooted disposable emulator only; this test replaces fixture rules' }
if ($Prepare) {
    $null=Adb-Run @('shell','pm','grant',$package,'android.permission.RECEIVE_SMS')
    $null=Adb-Run @('shell','pm','grant',$package,'android.permission.POST_NOTIFICATIONS')
    $null=Adb-Run @('shell','am','start','-W','-n',"$package/.MainActivity")
    $null=Adb-Run @('shell','am','force-stop',$package)
    $rule=@{id='7265d67f-c788-4c27-b88e-13529eb22549';label='Visibility test';number='10690003';soundUri='content://settings/system/alarm_alert';soundName='System alarm';channelId='';enabled=$true;forceAlarm=$true;vibrate=$false;flash=$false}
    $json=ConvertTo-Json -InputObject @($rule) -Compress
    $xml='<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="enabled" value="true"/><string name="items">'+[System.Security.SecurityElement]::Escape($json)+'</string></map>'
    $fixture=Join-Path (Split-Path $PSScriptRoot -Parent) 'build\visibility-fixture.xml'
    [IO.File]::WriteAllText($fixture,$xml,[Text.UTF8Encoding]::new($false))
    $null=Adb-Run @('shell','mkdir','-p',"/data/user/0/$package/shared_prefs")
    $null=Adb-Run @('push',$fixture,"/data/user/0/$package/shared_prefs/rules.xml")
    $uid=[regex]::Match(((Adb-Run @('shell','cmd','package','list','packages','-U',$package))-join ''),'uid:(\d+)').Groups[1].Value
    if (-not $uid) {throw 'App UID not found'}
    $null=Adb-Run @('shell','chown','-R',"${uid}:${uid}","/data/user/0/$package/shared_prefs")
    $context=(((Adb-Run @('shell','ls','-Zd',"/data/user/0/$package"))-join '') -split '\s+')[0]
    $null=Adb-Run @('shell','chcon','-R',$context,"/data/user/0/$package/shared_prefs")
}
# Emulate an OEM withholding heads-up/full-screen UI. The optional overlay is authorized.
$null=Adb-Run @('shell','cmd','appops','set',$package,'USE_FULL_SCREEN_INTENT','ignore')
$null=Adb-Run @('shell','cmd','appops','set',$package,'SYSTEM_ALERT_WINDOW','allow')
$originalHeadsUp=((Adb-Run @('shell','settings','get','global','heads_up_notifications_enabled'))-join '').Trim()
try {
    $null=Adb-Run @('shell','settings','put','global','heads_up_notifications_enabled','0')
    $null=Adb-Run @('shell','input','keyevent','224')
    $null=Adb-Run @('shell','wm','dismiss-keyguard')
    $null=Adb-Run @('shell','cmd','statusbar','collapse')
    $null=Adb-Run @('shell','am','start','-W','-n',"$package/.MainActivity",'-f','0x04000000')
    $null=Adb-Run @('shell','input','keyevent','3')
    $null=Adb-Run @('shell','cmd','media_session','volume','--stream','4','--set','2')
    $null=Adb-Run @('emu','sms','send','10690003','SmsTone visibility regression test')
    Start-Sleep -Seconds 3
    $service=(Adb-Run @('shell','dumpsys','activity','services',"$package/.AlarmService"))-join "`n"
    if ($service -notmatch 'isForeground=true') {throw 'FAIL: SMS did not start alarm service'}
    Write-Output 'PASS: SMS started the foreground alarm'
    $uiText=(Adb-Run @('shell','am','instrument','-w','-e','package','cn.local.smstone','cn.local.smstone.probe/.UiProbe'))-join "`n"
    if ($uiText -notmatch '停止提醒' -or $uiText -notmatch '停止并打开短信') {
        throw 'FAIL: alarm is running, but visible stop / open SMS controls are absent'
    }
    Write-Output 'PASS: stop and open-SMS controls are visible over another app'
    $button=if ($Action -eq 'Stop') {'停止提醒'} else {'停止并打开短信'}
    $clicked=(Adb-Run @('shell','am','instrument','-w','-e','package','cn.local.smstone','-e','click',$button,'cn.local.smstone.probe/.UiProbe'))-join "`n"
    if ($clicked -notmatch 'clicked=true') {throw 'FAIL: control was not clickable'}
    Start-Sleep -Milliseconds 700
    $after=(Adb-Run @('shell','dumpsys','activity','services',"$package/.AlarmService"))-join "`n"
    if ($after -match 'isForeground=true') {throw 'FAIL: action did not stop alarm service'}
    $volume=(Adb-Run @('shell','cmd','media_session','volume','--stream','4','--get'))-join ''
    if ($volume -notmatch 'volume is 2') {throw "FAIL: original alarm volume not restored: $volume"}
    Write-Output 'PASS: action stopped alarm and restored volume 2'
    if ($Action -eq 'OpenSms') {
        $smsPackage=((Adb-Run @('shell','cmd','role','get-role-holders','--user','0','android.app.role.SMS'))-join '').Trim()
        $top=(Adb-Run @('shell','dumpsys','activity','activities'))-join "`n"
        if ($smsPackage -eq 'null' -or $top -notmatch ('topResumedActivity=.*'+[regex]::Escape($smsPackage))) { throw 'FAIL: default SMS app was not opened' }
        Write-Output 'PASS: default SMS app opened'
    }
} finally {
    $null=Adb-Run @('shell','am','broadcast','-n',"$package/.AlarmActionReceiver",'-a','cn.local.smstone.STOP_ALARM')
    if ($originalHeadsUp -eq 'null') { $null=Adb-Run @('shell','settings','delete','global','heads_up_notifications_enabled') }
    else { $null=Adb-Run @('shell','settings','put','global','heads_up_notifications_enabled',$originalHeadsUp) }
}
