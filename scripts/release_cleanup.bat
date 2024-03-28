@echo off

echo Running %~n0...
echo This script removes all apks from the repo. 
echo Existing tags and releases won't be touched.
echo To skip specific tags add your patterns below.
echo This could be helpful when you need to clean the repo after DMCA Notice.

cd /d "%~dp0"

REM Skip first 15 releases
for /F "skip=15 tokens=*" %%a in ('hub release') do call :cleanupRelease %%a

goto End

:cleanupRelease
    set TAG_NAME=%1

    REM Skip auto update release
    if "%TAG_NAME%" == "latest" goto :cleanupReleaseEnd
    
    echo Processing %TAG_NAME%...
    
    for /F "tokens=*" %%a in ('hub release show -f %%as %TAG_NAME%') do (
        REM NOTE: don't add quotes around %%~nxf because there's a white space at the end.
        REM NOTE: Empty message == don't change release title
        REM Manual: https://hub.github.com/hub-release.1.html
        for %%f in ("%%a") do hub release edit -a %%~nxf -m "" %TAG_NAME% 2>nul
    )
    
    :cleanupReleaseEnd  
goto :eof

:End

pause

