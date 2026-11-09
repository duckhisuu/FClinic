#!/bin/bash
# ==============================================================================
# FClinic - Launch All Microservices
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=== [1/2] Ensuring Docker containers are running ==="
docker compose up -d

echo "=== [2/2] Starting Spring Boot microservices in background ==="
mkdir -p "$DIR/.logs"

start_service() {
    local name=$1
    local port=$2
    local path=$3
    local profiles=$4
    echo "Starting $name on port $port..."
    if [ -n "$profiles" ]; then
        SPRING_PROFILES_ACTIVE="$profiles" nohup mvn -f "$path/pom.xml" spring-boot:run > "$DIR/.logs/$name.log" 2>&1 &
    else
        nohup mvn -f "$path/pom.xml" spring-boot:run > "$DIR/.logs/$name.log" 2>&1 &
    fi
    echo "$!" > "$DIR/.logs/$name.pid"
}

start_service "auth-service" 8085 "$DIR/services/auth-service" "local"
start_service "doctor-service" 8082 "$DIR/services/doctor-service" ""
start_service "appointment-service" 8083 "$DIR/services/appointment-service" ""
start_service "patient-service" 8081 "$DIR/services/patient-service" "service-integration,local,doctor-reference"
start_service "notification-service" 8084 "$DIR/services/notification-service" ""

echo "All services launched in background. Logs are available in $DIR/.logs/"
echo "To stop all services, run: ./stop-all-services.sh"
