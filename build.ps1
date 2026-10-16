param(
    [string]$ToolRoot = 'D:\开源软件\Android-build-tools',
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$stageRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('SmsTone-' + [Guid]::NewGuid().ToString('N'))
if ($stageRoot -match '[^\x00-\x7F]') { throw 'Set TEMP to an ASCII-only path for Android native build tools.' }
New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'app') -Destination (Join-Path $stageRoot 'app') -Recurse
$buildDir = Join-Path $stageRoot 'build'
$classesDir = Join-Path $buildDir 'classes'
$generatedDir = Join-Path $buildDir 'generated'
$dexDir = Join-Path $buildDir 'dex'
$outputDir = Join-Path $projectRoot 'dist'
$signDir = Join-Path $projectRoot 'signing'
$androidJar = (Get-ChildItem -LiteralPath (Join-Path $ToolRoot 'platforms-android-35') -Filter android.jar -Recurse | Select-Object -First 1).FullName
$buildTools = (Get-ChildItem -LiteralPath (Join-Path $ToolRoot 'build-tools-35.0.0') -Filter aapt2.exe -Recurse | Select-Object -First 1).DirectoryName
if (-not $androidJar -or -not $buildTools) { throw 'Android API 35 platform and build-tools 35.0.0 are required.' }
Copy-Item -LiteralPath $androidJar -Destination (Join-Path $stageRoot 'android.jar')
$androidJar = Join-Path $stageRoot 'android.jar'
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) { throw 'JDK 17 not found; supply -JavaHome.' }
foreach ($dir in @($buildDir,$classesDir,$generatedDir,$dexDir,$outputDir,$signDir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
function Run-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Exe" }
}
$compiledRes = Join-Path $buildDir 'resources.zip'
$unsignedApk = Join-Path $buildDir 'unsigned.apk'
$alignedApk = Join-Path $buildDir 'aligned.apk'
$finalApk = Join-Path $buildDir 'SmsTone-0.3.1.apk'
Run-Checked (Join-Path $buildTools 'aapt2.exe') @('compile','--dir',(Join-Path $stageRoot 'app\src\main\res'),'-o',$compiledRes)
Run-Checked (Join-Path $buildTools 'aapt2.exe') @('link','-I',$androidJar,'--manifest',(Join-Path $stageRoot 'app\src\main\AndroidManifest.xml'),'--java',$generatedDir,'-o',$unsignedApk,$compiledRes)
$sourceFiles = @((Get-ChildItem -LiteralPath (Join-Path $stageRoot 'app\src\main\java') -Filter '*.java' -Recurse).FullName) + @((Get-ChildItem -LiteralPath $generatedDir -Filter '*.java' -Recurse).FullName)
$javacArgs = @('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d',$classesDir) + $sourceFiles
Run-Checked (Join-Path $JavaHome 'bin\javac.exe') $javacArgs
$classesJar = Join-Path $buildDir 'classes.jar'
Run-Checked (Join-Path $JavaHome 'bin\jar.exe') @('cf',$classesJar,'-C',$classesDir,'.')
Run-Checked (Join-Path $JavaHome 'bin\java.exe') @('-cp',(Join-Path $buildTools 'lib\d8.jar'),'com.android.tools.r8.D8','--lib',$androidJar,'--min-api','26','--output',$dexDir,$classesJar)
Run-Checked (Join-Path $JavaHome 'bin\jar.exe') @('uf',$unsignedApk,'-C',$dexDir,'classes.dex')
Run-Checked (Join-Path $buildTools 'zipalign.exe') @('-f','4',$unsignedApk,$alignedApk)
$keyPath = Join-Path $signDir 'smstone-local.p12'
$passwordPath = Join-Path $signDir 'password.txt'
if (-not (Test-Path -LiteralPath $keyPath)) {
    $password = [Guid]::NewGuid().ToString('N')
    Set-Content -LiteralPath $passwordPath -Value $password -Encoding ascii -NoNewline
    Run-Checked (Join-Path $JavaHome 'bin\keytool.exe') @('-genkeypair','-keystore',$keyPath,'-storetype','PKCS12','-storepass',$password,'-keypass',$password,'-alias','smstone','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=SmsTone Local Build')
}
Run-Checked (Join-Path $JavaHome 'bin\java.exe') @('-jar',(Join-Path $buildTools 'lib\apksigner.jar'),'sign','--ks',$keyPath,'--ks-key-alias','smstone','--ks-pass',('file:'+$passwordPath),'--out',$finalApk,$alignedApk)
Run-Checked (Join-Path $JavaHome 'bin\java.exe') @('-jar',(Join-Path $buildTools 'lib\apksigner.jar'),'verify','--verbose',$finalApk)
Run-Checked (Join-Path $buildTools 'zipalign.exe') @('-c','4',$finalApk)
$hash = (Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath (Join-Path $outputDir 'SHA256-0.3.1.txt') -Value "$hash  SmsTone-0.3.1.apk" -Encoding ascii
Copy-Item -LiteralPath $finalApk -Destination (Join-Path $outputDir 'SmsTone-0.3.1.apk') -Force
Write-Output ('APK ready: ' + (Join-Path $outputDir 'SmsTone-0.3.1.apk'))
Write-Output "Build staging: $stageRoot"
Write-Output "SHA256: $hash"
