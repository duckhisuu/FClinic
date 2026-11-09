#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'patient_uuid') THEN
            CREATE DATABASE patient_uuid OWNER fclinic_admin;
        END IF;
    END \$\$;
EOSQL

if [ -f /docker-entrypoint-initdb.d/uuid-schema.sql ]; then
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "patient_uuid" -f /docker-entrypoint-initdb.d/uuid-schema.sql || true
fi
