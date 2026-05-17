@echo off
rem Backend deployment script
rem Usage: deploy.bat [start|stop|restart|status]

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PID_FILE=%SCRIPT_DIR%.uvicorn.pid"
set "LOG_FILE=%SCRIPT_DIR%backend.log"
set "PORT=8000"

goto :main

:get_pid
if exist "%PID_FILE%" (
    set /p PID=<"%PID_FILE%"
) else (
    set "PID="
)
exit /b

:is_running
call :get_pid
if defined PID (
    tasklist /FI "PID eq %PID%" 2>nul | find /I "%PID%" >nul
    if !errorlevel! equ 0 (
        exit /b 0
    )
)
exit /b 1

:do_start
echo Starting backend server...
call :is_running
if !errorlevel! equ 0 (
    echo Server is already running ^(PID: %PID%^)
    exit /b
)

cmd /c start /b python -m uvicorn app.main:app --host 0.0.0.0 --port %PORT% ^> "%LOG_FILE%" 2^>^&1

ping localhost -n 4 >nul 2>&1

call :get_pid
if defined PID (
    echo Server started ^(PID: %PID%^)
) else (
    echo Server may have started, check task manager
)
exit /b

:do_stop
echo Stopping backend server...
call :is_running
if !errorlevel! neq 0 (
    echo Server is not running
    if exist "%PID_FILE%" del /F "%PID_FILE%"
    exit /b
)

call :get_pid
if defined PID (
    taskkill //F //PID %PID% >nul 2>&1
)

ping localhost -n 3 >nul 2>&1

if exist "%PID_FILE%" del /F "%PID_FILE%"
echo Server stopped
exit /b

:do_restart
call :do_stop
ping localhost -n 3 >nul 2>&1
call :do_start
echo Server restarted
exit /b

:do_status
call :is_running
if !errorlevel! equ 0 (
    echo Server is running ^(PID: %PID%^)
    curl -s http://localhost:%PORT%/health || echo Health check failed
) else (
    echo Server is not running
)
exit /b

:main
if "%1"=="" goto :do_start
if "%1"=="start" goto :do_start
if "%1"=="stop" goto :do_stop
if "%1"=="restart" goto :do_restart
if "%1"=="status" goto :do_status
echo Usage: %0 {start^|stop^|restart^|status}
exit /b