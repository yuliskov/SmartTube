@echo off
echo Running %~n0...
echo This script deletes all workflow runs from the current repo.
echo WARNING: Deleted runs cannot be recovered.

REM List all workflow runs (up to 1000)
for /F "tokens=*" %%A in ('gh run list --limit 1000') do (
    REM Skip the header line
    if NOT "%%A"=="ID" (
        echo Deleting workflow run %%A
        gh run delete %%A --confirm
    )
)

pause
