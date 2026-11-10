-- Finite administrative assignments. No clinical permission and no cross-database FK.
CREATE TABLE reception_patient_assignments (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id),
 subject UUID NOT NULL,
 patient_id UUID NOT NULL,
 source_assignment_id UUID NOT NULL,
 operations TEXT[] NOT NULL,
 valid_from TIMESTAMPTZ NOT NULL,
 valid_until TIMESTAMPTZ NOT NULL,
 CHECK(valid_until > valid_from),
 CHECK(cardinality(operations) BETWEEN 1 AND 8 AND operations <@ ARRAY[
   'ADMIN_PROFILE_READ','ADMIN_PROFILE_UPDATE','ADMIN_SEARCH','APPOINTMENT_CREATE','APPOINTMENT_READ',
   'APPOINTMENT_SEARCH','APPOINTMENT_CANCEL','APPOINTMENT_RESCHEDULE']::TEXT[]),
 UNIQUE(user_id, patient_id, source_assignment_id)
);
CREATE INDEX ix_reception_assignment_subject ON reception_patient_assignments(subject, valid_until);
