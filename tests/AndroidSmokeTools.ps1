$ErrorActionPreference = 'Stop'
$script:Adb = 'D:\开源软件\Android-build-tools\platform-tools\platform-tools\adb.exe'
$script:Device = 'emulator-5556'
function Adb-Run([string[]]$Arguments) {
    $result = & $script:Adb -s $script:Device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
    return $result
}
function Get-TestUi {
    $null = Adb-Run @('shell','uiautomator','dump','/sdcard/smstone-ui.xml')
    return [xml]((Adb-Run @('shell','cat','/sdcard/smstone-ui.xml')) -join "`n")
}
function Click-Node($Node) {
    if (-not $Node) { throw 'UI node was not found.' }
    $bounds = [regex]::Matches($Node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    $x = [int](($bounds[0]+$bounds[2])/2); $y = [int](($bounds[1]+$bounds[3])/2)
    $null = Adb-Run @('shell','input','tap',"$x","$y")
}
function Click-Text([string]$Text) {
    $ui = Get-TestUi
    $node = $ui.SelectNodes('//node') | Where-Object { $_.text -eq $Text } | Select-Object -First 1
    if (-not $node) { throw "Text not found: $Text" }
    Click-Node $node
}
function Get-UiText {
    $ui = Get-TestUi
    return $ui.SelectNodes('//node') | Where-Object { $_.text } | ForEach-Object { $_.text }
}
function Find-TextWithScroll([string]$Text) {
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        $ui = Get-TestUi
        $node = $ui.SelectNodes('//node') | Where-Object { $_.text -eq $Text } | Select-Object -First 1
        if ($node) { return $node }
        $null = Adb-Run @('shell','input','swipe','1000','2100','1000','550','650')
    }
    throw "Text not found after scrolling: $Text"
}
function Input-Field([int]$Index,[string]$Text) {
    $ui = Get-TestUi
    $fields = @($ui.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' })
    Click-Node $fields[$Index]
    $null = Adb-Run @('shell','input','text',$Text)
    $null = Adb-Run @('shell','input','keyevent','4')
}
function Save-TestScreenshot([string]$Name) {
    $destination = Join-Path (Split-Path $PSScriptRoot -Parent) ('test-output\' + $Name + '.png')
    $null = Adb-Run @('shell','screencap','-p','/sdcard/smstone-test.png')
    $null = Adb-Run @('pull','/sdcard/smstone-test.png',$destination)
    Write-Output $destination
}
