@echo off
where node.exe >nul 2>&1
if errorlevel 1 (
  echo Node.js is required. Install Node.js LTS, then restart VS Code.
  echo Install command: winget install --id OpenJS.NodeJS.LTS -e
  exit /b 1
)
node "%~dp0storefront\dev-server.mjs"
