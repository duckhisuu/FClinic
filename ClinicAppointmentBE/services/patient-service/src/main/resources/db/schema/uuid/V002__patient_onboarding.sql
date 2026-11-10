-- Additive, repeatable migration for the existing UUID Patient schema.
CREATE TABLE IF NOT EXISTS patient_onboardings (
    account_id uuid PRIMARY KEY,
    patient_id uuid NOT NULL UNIQUE REFERENCES patients(id) ON DELETE RESTRICT,
    subject varchar(255) NOT NULL,
    request_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
