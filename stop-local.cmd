@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Stop-Local.ps1" %*
exit /b %ERRORLEVEL%
