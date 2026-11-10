-- One owner per business profile, including administrative provisioning paths.
-- Existing conflicting mappings stop migration; no owner is guessed or silently unlinked.
CREATE UNIQUE INDEX uq_users_patient_profile ON users(patient_id) WHERE patient_id IS NOT NULL;
CREATE UNIQUE INDEX uq_users_doctor_profile ON users(doctor_id) WHERE doctor_id IS NOT NULL;
CREATE TABLE patient_onboardings (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    patient_id uuid NOT NULL UNIQUE,
    subject uuid NOT NULL,
    request_body text NOT NULL,
    command_body text NOT NULL,
    request_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
