-- Preserve existing rows. If historical duplicates exist, migration fails for explicit reconciliation.
CREATE UNIQUE INDEX uq_users_patient_binding ON users(patient_id) WHERE patient_id IS NOT NULL;
