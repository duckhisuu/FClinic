-- PATIENT UUID TARGET: fresh disposable database ONLY. Not an ALTER/backfill migration.
-- LEGACY DATA MIGRATION REQUIRES OWNER MAPPING. Never run after legacy V001.
CREATE TABLE patients (
    id uuid PRIMARY KEY CHECK (id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    patient_code varchar(40) NOT NULL UNIQUE,
    full_name varchar(255) NOT NULL CHECK (length(btrim(full_name)) > 0),
    phone_number varchar(16) NOT NULL
        CHECK (phone_number ~ '^\+[1-9][0-9]{7,14}$'),
    email varchar(255),
    date_of_birth date,
    gender varchar(10) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    address varchar(2000),
    status varchar(8) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by varchar(255) NOT NULL,
    updated_by varchar(255) NOT NULL,
    CHECK (patient_code = 'PAT-' || id::text)
);
CREATE TABLE medical_records (
    id uuid PRIMARY KEY CHECK (id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE RESTRICT CHECK (patient_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    doctor_id uuid NOT NULL CHECK (doctor_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    appointment_id uuid UNIQUE CHECK (appointment_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    visit_date timestamptz NOT NULL,
    symptoms varchar(2000) NOT NULL CHECK (length(btrim(symptoms)) > 0),
    diagnosis varchar(2000) NOT NULL CHECK (length(btrim(diagnosis)) > 0),
    treatment varchar(4000),
    notes varchar(4000),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by varchar(255) NOT NULL,
    updated_by varchar(255) NOT NULL
);
CREATE TABLE patient_revisions (
    id uuid PRIMARY KEY CHECK (id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE RESTRICT CHECK (patient_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    entity_version bigint NOT NULL CHECK (entity_version >= 0),
    action varchar(10) NOT NULL CHECK (action IN ('CREATE', 'UPDATE')),
    before_data jsonb,
    after_data jsonb NOT NULL,
    actor_id varchar(255) NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (patient_id, entity_version),
    CHECK ((action = 'CREATE' AND before_data IS NULL)
        OR (action = 'UPDATE' AND before_data IS NOT NULL))
);
CREATE TABLE medical_record_revisions (
    id uuid PRIMARY KEY CHECK (id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    medical_record_id uuid NOT NULL REFERENCES medical_records(id) ON DELETE RESTRICT CHECK (medical_record_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    entity_version bigint NOT NULL CHECK (entity_version >= 0),
    action varchar(10) NOT NULL CHECK (action IN ('CREATE', 'UPDATE')),
    before_data jsonb,
    after_data jsonb NOT NULL,
    actor_id varchar(255) NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_reason varchar(1000),
    UNIQUE (medical_record_id, entity_version),
    CHECK ((action = 'CREATE' AND before_data IS NULL AND change_reason IS NULL)
        OR (action = 'UPDATE' AND before_data IS NOT NULL
            AND change_reason IS NOT NULL AND length(btrim(change_reason)) > 0))
);
CREATE INDEX ix_patients_phone_id ON patients(phone_number, id);
CREATE INDEX ix_patients_name_id ON patients(full_name, id);
CREATE INDEX ix_patients_created_id ON patients(created_at, id);
CREATE INDEX ix_medical_patient_visit_id
    ON medical_records(patient_id, visit_date DESC, id DESC);
-- Additive and repeatable: no historical profile is claimed from its creator audit field.
CREATE TABLE IF NOT EXISTS patient_self_registrations (
    subject VARCHAR(255) PRIMARY KEY,
    patient_id UUID NOT NULL UNIQUE REFERENCES patients(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
