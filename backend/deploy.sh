#!/bin/bash

# Backend deployment script
# Usage: ./deploy.sh [start|stop|restart|status]

set -e

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
PID_FILE="$SCRIPT_DIR/.uvicorn.pid"
LOG_FILE="$SCRIPT_DIR/backend.log"
PORT=8000

# Detect OS
is_windows=false
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ "$OS" == "Windows_NT" ]]; then
    is_windows=true
fi

get_pid() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE"
    else
        echo ""
    fi
}

is_running() {
    local pid=$(get_pid)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

do_start() {
    echo "Starting backend server..."

    if is_running; then
        echo "Server is already running (PID: $(get_pid))"
        return
    fi

    # Start uvicorn in background
    if [ "$is_windows" = true ]; then
        nohup python -m uvicorn app.main:app --host 0.0.0.0 --port $PORT > "$LOG_FILE" 2>&1 &
    else
        nohup python -m uvicorn app.main:app --host 0.0.0.0 --port $PORT > "$LOG_FILE" 2>&1 &
    fi

    sleep 2

    if is_running; then
        echo $! > "$PID_FILE"
        echo "Server started successfully (PID: $(get_pid))"
    else
        echo "Server failed to start. Check $LOG_FILE for details."
        exit 1
    fi
}

do_stop() {
    echo "Stopping backend server..."

    if ! is_running; then
        echo "Server is not running"
        rm -f "$PID_FILE" 2>/dev/null || true
        return
    fi

    local pid=$(get_pid)

    if [ "$is_windows" = true ]; then
        taskkill //F //PID "$pid" 2>/dev/null || true
    else
        kill "$pid" 2>/dev/null || true
    fi

    sleep 2

    if is_running; then
        echo "Failed to stop server gracefully, forcing..."
        if [ "$is_windows" = true ]; then
            taskkill //F //PID "$pid" 2>/dev/null || true
        else
            kill -9 "$pid" 2>/dev/null || true
        fi
    fi

    rm -f "$PID_FILE" 2>/dev/null || true
    echo "Server stopped"
}

do_restart() {
    do_stop
    sleep 2
    do_start
    echo "Server restarted"
}

do_status() {
    if is_running; then
        echo "Server is running (PID: $(get_pid))"
        curl -s http://localhost:$PORT/health || echo "Health check failed"
    else
        echo "Server is not running"
    fi
}

# Main
case "${1:-start}" in
    start)
        do_start
        ;;
    stop)
        do_stop
        ;;
    restart)
        do_restart
        ;;
    status)
        do_status
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac