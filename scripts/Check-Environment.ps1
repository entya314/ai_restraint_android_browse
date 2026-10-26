# Read-only checks. Run: powershell -ExecutionPolicy Bypass -File .\scripts\Check-Environment.ps1
$ErrorActionPreference = 'Continue'
Write-Output '=== Android build environment (Windows) ==='
$studioCandidates = @("$env:ProgramFiles\Android\Android Studio", "$env:LOCALAPPDATA\Programs\Android Studio")
$studioCandidates += @(Get-ItemProperty 'HKLM:\SOFTWARE\Android Studio' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path -ErrorAction SilentlyContinue)
$studio = $studioCandidates | Where-Object { $_ -and (Test-Path "$_\bin\studio64.exe") } | Select-Object -First 1
Write-Output "Android Studio: $(if ($studio) { $studio } else { 'Not found in standard locations (check custom install location).' })"
$javaCandidates = @()
if ($env:JAVA_HOME) { $javaCandidates += "$env:JAVA_HOME\bin\java.exe" }
if ($studio) { $javaCandidates += "$studio\jbr\bin\java.exe" }
$javaOnPath = Get-Command java.exe -ErrorAction SilentlyContinue
if ($javaOnPath) { $javaCandidates += $javaOnPath.Source }
$java = $javaCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($java) {
    Write-Output "Java: $java"
    & $java -version 2>&1 | ForEach-Object { Write-Output $_.ToString() }
    $javac = Join-Path (Split-Path $java) 'javac.exe'
    Write-Output "JDK compiler: $(if (Test-Path $javac) { $javac } else { 'Not found (a JRE alone cannot build).' })"
} else { Write-Output 'JDK: not found. Install Android Studio and use its bundled jbr.' }
$sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")
if (Test-Path '.\local.properties') {
    $sdkLine = Get-Content '.\local.properties' | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
    if ($sdkLine) { $sdkCandidates = @($sdkLine.Substring(8).Replace('\\', '\').Replace('\:', ':')) + $sdkCandidates }
}
$sdk = $sdkCandidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
Write-Output "Android SDK: $(if ($sdk) { $sdk } else { 'Not found. Use Android Studio > SDK Manager.' })"
if ($sdk) {
    foreach ($component in @('platforms\android-35\android.jar', 'build-tools\35.0.0\apksigner.bat', 'platform-tools\adb.exe')) {
        Write-Output "$component : $(Test-Path (Join-Path $sdk $component))"
    }
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    if (Test-Path $adb) { & $adb devices -l }
}
Write-Output 'Required: Android SDK Platform 35, Build-Tools 35.0.0, Platform-Tools, JDK 17 or 21.'
Write-Output 'No configuration was modified. Android Studio > Settings > Build Tools > Gradle: select bundled jbr.'
