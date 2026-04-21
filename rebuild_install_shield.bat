@echo off
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
set "ANDROID_SDK_ROOT=%ROOT%.tools\android-sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "JAVA_HOME=%ROOT%.tools\jdk\temurin11\jdk-11.0.30+7"
set "GRADLE_USER_HOME=%ROOT%.tools\gradle-home"

set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin;%PATH%"

cd /d "%ROOT%"

call gradlew.bat clean :smarttubetv:assembleStstableDebug
if errorlevel 1 exit /b 1

set "APK="
for %%F in ("%ROOT%smarttubetv\build\outputs\apk\ststable\debug\SmartTube_stable_*_universal.apk") do (
  set "APK=%%~fF"
)

if "%APK%"=="" (
  echo APK not found.
  exit /b 2
)

adb connect shield:5555
if errorlevel 1 exit /b 3

adb uninstall org.smarttube.stable >nul 2>nul
adb install -r -d "%APK%"
if errorlevel 1 exit /b 4

adb shell monkey -p org.smarttube.stable -c android.intent.category.LAUNCHER 1 >nul

echo OK: %APK%
exit /b 0
