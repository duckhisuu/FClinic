-- Additive and repeatable: no historical profile is claimed from its creator audit field.
CREATE TABLE IF NOT EXISTS patient_self_registrations (
    subject VARCHAR(255) PRIMARY KEY,
    patient_id UUID NOT NULL UNIQUE REFERENCES patients(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
