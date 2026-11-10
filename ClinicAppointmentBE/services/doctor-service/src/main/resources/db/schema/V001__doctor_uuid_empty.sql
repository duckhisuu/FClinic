-- Doctor Service UUID Schema
CREATE TABLE IF NOT EXISTS doctors (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    specialty VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    qualification VARCHAR(255),
    experience_years INTEGER,
    consultation_fee DOUBLE PRECISION,
    room_number VARCHAR(255),
    bio TEXT,
    avatar_url VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS doctor_time_slots (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL,
    slot_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_booked BOOLEAN NOT NULL DEFAULT FALSE,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_doctor_slots_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_doctor_slots_lookup ON doctor_time_slots (doctor_id, slot_date, start_time);
CREATE UNIQUE INDEX IF NOT EXISTS uq_doctor_slot_hour ON doctor_time_slots (doctor_id, slot_date, start_time);
