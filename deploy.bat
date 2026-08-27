@echo off
chcp 65001 >nul
title IoT 大数据平台一键自动化部署工具

echo.
echo ========================================================
echo        IoT 工业物联网与大数据平台 · 一键部署脚本
echo ========================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1" %*

pause
