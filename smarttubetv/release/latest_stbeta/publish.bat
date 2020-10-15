@echo off

echo Running %~n0...

cd /d "%~dp0"

for /r %%a in (*.apk) do set APK_FILE_NAME="%%a"
for /r %%a in (*.json) do set JSON_FILE_NAME="%%a"

REM ============= GitHub =====================

REM https://github.com/yuliskov/SmartYouTubeTV/releases/download/beta/smartyoutubetv_latest.apk

set APK_FILE_NAME_TEMP=%TEMP%\smarttube_beta.apk
set JSON_FILE_NAME_TEMP=%TEMP%\smarttube_beta.json

copy /y %APK_FILE_NAME% "%APK_FILE_NAME_TEMP%" >nul
copy /y %JSON_FILE_NAME% "%JSON_FILE_NAME_TEMP%" >nul
hub release edit -a "%JSON_FILE_NAME_TEMP%" -m "Latest beta release" beta
hub release edit -a "%APK_FILE_NAME_TEMP%" -m "Latest beta release" beta

pause

