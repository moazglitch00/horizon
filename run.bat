@echo off
setlocal

rem Horizon: Minecraft in Rust. Builds (if needed) and runs the game client.
rem Requires Rust (cargo) and a prepared Minecraft install (see README.md).

cd /d "%~dp0"

if not exist "Cargo.toml" (
    echo run.bat must be placed in the Horizon repository root.
    pause
    exit /b 1
)

cargo run --release -p horizon-client %*
set "EXIT=%ERRORLEVEL%"

if not "%EXIT%"=="0" (
    echo.
    echo Horizon exited with code %EXIT%.
    pause
)

exit /b %EXIT%
