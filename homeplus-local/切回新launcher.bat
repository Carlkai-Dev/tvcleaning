@echo off
chcp 65001 >nul
rem 先設定 TV_HOST（例如 set TV_HOST=192.168.1.50），adb 不在 PATH 時再設定 ADB=完整路徑
powershell -NoProfile -File "%~dp0switch-to-tvhome.ps1" %*
