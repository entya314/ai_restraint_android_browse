param([string]$Apk = "$PSScriptRoot\..\dist\restricted-browser-test.apk", [string]$Serial = '')
$ErrorActionPreference = 'Stop'
if (!(Test-Path $Apk)) { throw "APK not found: $Apk" }
$sdk = $env:ANDROID_HOME
if (!$sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (!(Test-Path $adb)) { throw 'adb not found. Set ANDROID_HOME or install Android SDK Platform-Tools.' }
& $adb devices -l
$targetArgs = @()
if ($Serial) { $targetArgs = @('-s', $Serial) }
& $adb @targetArgs install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed. See INSTALL-WINDOWS.md for troubleshooting.' }
& $adb @targetArgs shell am start -n 'jp.example.restrictedbrowser.test/jp.example.restrictedbrowser.MainActivity'
