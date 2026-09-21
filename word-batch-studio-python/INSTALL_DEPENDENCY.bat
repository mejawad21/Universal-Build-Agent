@echo off
title Install Word Batch Studio Pro Dependency
cd /d "%~dp0"
where py >nul 2>&1
if %errorlevel%==0 (
  py -m pip install --user pywin32
  pause
  goto end
)
where python >nul 2>&1
if %errorlevel%==0 (
  python -m pip install --user pywin32
  pause
  goto end
)
echo.
echo Python is not installed or is blocked by your IT policy.
pause
:end
