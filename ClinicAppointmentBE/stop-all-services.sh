#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Stopping all microservices..."
if [ -d "$DIR/.logs" ]; then
    for pidfile in "$DIR/.logs/"*.pid; do
        if [ -f "$pidfile" ]; then
            pid=$(cat "$pidfile")
            echo "Stopping PID $pid ($(basename "$pidfile" .pid))..."
            kill "$pid" 2>/dev/null || true
            rm -f "$pidfile"
        fi
    done
fi
# Also kill any leftover spring-boot java processes on ports 8081-8085
fuser -k 8081/tcp 8082/tcp 8083/tcp 8084/tcp 8085/tcp 2>/dev/null || true
echo "All microservices stopped."
