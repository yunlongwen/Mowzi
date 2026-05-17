#!/usr/bin/env python3
"""Backend deployment script - works on both Windows and Linux."""

import os
import sys
import signal
import subprocess
import time
import socket

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PID_FILE = os.path.join(SCRIPT_DIR, ".uvicorn.pid")
LOG_FILE = os.path.join(SCRIPT_DIR, "backend.log")
PORT = 8000

def is_port_in_use():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex(('localhost', PORT)) == 0

def get_pid():
    if os.path.exists(PID_FILE):
        with open(PID_FILE, 'r') as f:
            return f.read().strip()
    return ""

def is_running():
    pid = get_pid()
    if pid:
        try:
            os.kill(int(pid), 0)
            return True
        except (OSError, ProcessLookupError):
            pass
    return False

def do_start():
    print("Starting backend server...")
    if is_running():
        print(f"Server is already running (PID: {get_pid()})")
        return

    cmd = [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", str(PORT)]

    with open(LOG_FILE, "w") as f:
        subprocess.Popen(cmd, cwd=SCRIPT_DIR, stdout=f, stderr=subprocess.STDOUT)

    time.sleep(3)

    if is_port_in_use():
        print(f"Server started on port {PORT}")
    else:
        print("Server failed to start. Check backend.log for details.")

def do_stop():
    print("Stopping backend server...")
    pid = get_pid()
    if not pid or not is_running():
        print("Server is not running")
        if os.path.exists(PID_FILE):
            os.remove(PID_FILE)
        return

    try:
        if sys.platform == "win32":
            subprocess.run(["taskkill", "/F", "/PID", pid], check=False)
        else:
            os.kill(int(pid), signal.SIGTERM)
    except:
        pass

    time.sleep(2)

    if os.path.exists(PID_FILE):
        os.remove(PID_FILE)

    if is_port_in_use():
        print("Server may still be running")
    else:
        print("Server stopped")

def do_restart():
    do_stop()
    time.sleep(2)
    do_start()

def do_status():
    if is_port_in_use():
        pid = get_pid()
        print(f"Server is running on port {PORT} (PID: {pid or 'unknown'})")
        try:
            import requests
            resp = requests.get(f"http://localhost:{PORT}/health", timeout=2)
            print(f"Health check: {resp.json()}")
        except:
            print("Health check failed")
    else:
        print("Server is not running")

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "start"

    if cmd == "start":
        do_start()
    elif cmd == "stop":
        do_stop()
    elif cmd == "restart":
        do_restart()
    elif cmd == "status":
        do_status()
    else:
        print(f"Usage: python {sys.argv[0]} [start|stop|restart|status]")