CREATE TABLE roles (
    id UUID PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_roles_id_uuid_v4 CHECK (
        id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_roles_name_valid CHECK (
        role_name IN ('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_PATIENT', 'ROLE_STAFF')
    )
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    keycloak_id UUID NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone_number VARCHAR(20),
    full_name VARCHAR(100) NOT NULL,
    patient_id UUID,
    doctor_id UUID,
    google_id VARCHAR(128) UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_id_uuid_v4 CHECK (
        id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_users_role_id_uuid_v4 CHECK (
        role_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_users_keycloak_id_uuid_v4 CHECK (
        keycloak_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_users_patient_id_uuid_v4 CHECK (
        patient_id IS NULL OR patient_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_users_doctor_id_uuid_v4 CHECK (
        doctor_id IS NULL OR doctor_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    )
);

CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_phone_number ON users(phone_number);
CREATE INDEX idx_users_patient_id ON users(patient_id);
CREATE INDEX idx_users_doctor_id ON users(doctor_id);

CREATE TABLE password_reset_otps (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email VARCHAR(100) NOT NULL,
    otp_code VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_otps_id_uuid_v4 CHECK (
        id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_otps_user_id_uuid_v4 CHECK (
        user_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    )
);

CREATE INDEX idx_password_reset_otps_email ON password_reset_otps(email);
CREATE INDEX idx_password_reset_otps_user_id ON password_reset_otps(user_id);

