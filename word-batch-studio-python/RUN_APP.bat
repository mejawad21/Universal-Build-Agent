@echo off
title Word Batch Studio Pro
cd /d "%~dp0"
where py >nul 2>&1
if %errorlevel%==0 (
  py "Word_Batch_Studio_Pro.py"
  goto end
)
where python >nul 2>&1
if %errorlevel%==0 (
  python "Word_Batch_Studio_Pro.py"
  goto end
)
echo.
echo Python is not installed or is blocked by your IT policy.
echo Please install Python or contact IT.
pause
:end
