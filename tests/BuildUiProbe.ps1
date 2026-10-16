param(
    [string]$ToolRoot='D:\开源软件\Android-build-tools',
    [string]$JavaHome='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$stage=Join-Path $env:TEMP ('SmsToneProbe-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$stage\classes","$stage\dex" -Force | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\ui-probe\UiProbe.java","$PSScriptRoot\ui-probe\AndroidManifest.xml" -Destination $stage
$androidJar=(Get-ChildItem -LiteralPath "$ToolRoot\platforms-android-35" -Filter android.jar -Recurse | Select-Object -First 1).FullName
Copy-Item -LiteralPath $androidJar -Destination "$stage\android.jar"
$bt=(Get-ChildItem -LiteralPath "$ToolRoot\build-tools-35.0.0" -Filter aapt2.exe -Recurse | Select-Object -First 1).DirectoryName
function Check {if ($LASTEXITCODE -ne 0) {throw 'UI probe build failed'}}
& "$JavaHome\bin\javac.exe" -encoding UTF-8 --release 8 -cp "$stage\android.jar" -d "$stage\classes" "$stage\UiProbe.java"; Check
& "$JavaHome\bin\jar.exe" cf "$stage\classes.jar" -C "$stage\classes" .; Check
& "$JavaHome\bin\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --lib "$stage\android.jar" --min-api 26 --output "$stage\dex" "$stage\classes.jar"; Check
& "$bt\aapt2.exe" link -I "$stage\android.jar" --manifest "$stage\AndroidManifest.xml" -o "$stage\unsigned.apk"; Check
& "$JavaHome\bin\jar.exe" uf "$stage\unsigned.apk" -C "$stage\dex" classes.dex; Check
& "$bt\zipalign.exe" -f 4 "$stage\unsigned.apk" "$stage\aligned.apk"; Check
& "$JavaHome\bin\java.exe" -jar "$bt\lib\apksigner.jar" sign --ks "$project\signing\smstone-local.p12" --ks-key-alias smstone --ks-pass "file:$project\signing\password.txt" --out "$stage\probe.apk" "$stage\aligned.apk"; Check
. "$PSScriptRoot\AndroidSmokeTools.ps1"
Adb-Run @('install','-r',"$stage\probe.apk")
