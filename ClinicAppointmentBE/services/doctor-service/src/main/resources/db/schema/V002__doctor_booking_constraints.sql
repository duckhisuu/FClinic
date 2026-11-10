-- Additive migration. Never discard conflicting offers or rewrite existing fees automatically.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM doctor_time_slots GROUP BY doctor_id,slot_date,start_time HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate Doctor offers require reconciliation before uq_doctor_slot_hour can be installed';
    END IF;
    IF EXISTS (SELECT 1 FROM doctors WHERE consultation_fee < 0 OR consultation_fee IS NULL
        OR consultation_fee IN ('NaN'::float8,'Infinity'::float8,'-Infinity'::float8) OR experience_years < 0) THEN
        RAISE EXCEPTION 'Invalid Doctor fees/experience require reconciliation before validation constraints';
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_doctor_slot_hour ON doctor_time_slots(doctor_id,slot_date,start_time);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_doctor_fee_nonnegative' AND conrelid='doctors'::regclass) THEN
        ALTER TABLE doctors ADD CONSTRAINT ck_doctor_fee_nonnegative CHECK (
            consultation_fee IS NOT NULL AND consultation_fee >= 0 AND consultation_fee < 'Infinity'::float8);
        ALTER TABLE doctors ADD CONSTRAINT ck_doctor_experience_nonnegative CHECK (experience_years >= 0);
    END IF;
END $$;
