-- Cross-database mapping: equal (kind, legacy number) yields the same UUID in every service.
CREATE OR REPLACE FUNCTION pg_temp.fclinic_uuid(kind text, value text) RETURNS uuid
LANGUAGE sql IMMUTABLE STRICT AS $$
 WITH digest AS (SELECT md5('fclinic:' || kind || ':' || value) AS h)
 SELECT CASE WHEN value ~ '^[0-9]+$' THEN
  (substr(h,1,12) || '3' || substr(h,14,3) ||
   to_hex(8 + (('x' || substr(h,17,1))::bit(4)::integer & 3)) || substr(h,18,15))::uuid
 ELSE value::uuid END FROM digest
$$;
CREATE OR REPLACE FUNCTION pg_temp.fclinic_uuid_json(value jsonb, root_kind text) RETURNS jsonb
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE result jsonb; pair record; kind text;
BEGIN
 IF jsonb_typeof(value) = 'object' THEN
  result := '{}'::jsonb;
  FOR pair IN SELECT * FROM jsonb_each(value) LOOP
   kind := CASE pair.key WHEN 'id' THEN root_kind WHEN 'appointmentId' THEN 'appointment'
    WHEN 'doctorId' THEN 'doctor' WHEN 'slotId' THEN 'slot' WHEN 'previousSlotId' THEN 'slot'
    WHEN 'userId' THEN 'user' WHEN 'recipientUserId' THEN 'user' WHEN 'medicalRecordId' THEN 'medical-record'
    WHEN 'aggregateId' THEN 'appointment' ELSE NULL END;
   IF kind IS NOT NULL AND pair.value <> 'null'::jsonb THEN
    result := result || jsonb_build_object(pair.key, pg_temp.fclinic_uuid(kind, pair.value #>> '{}'));
   ELSE
    result := result || jsonb_build_object(pair.key, pg_temp.fclinic_uuid_json(pair.value, root_kind));
   END IF;
  END LOOP;
  RETURN result;
 ELSIF jsonb_typeof(value) = 'array' THEN
  SELECT COALESCE(jsonb_agg(pg_temp.fclinic_uuid_json(v, root_kind)), '[]'::jsonb) INTO result FROM jsonb_array_elements(value) v;
  RETURN result;
 END IF;
 RETURN value;
END $$;
-- Stop writers/consumers before this breaking ID cutover. READY events must be drained first.
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM outbox_events WHERE status = 'READY' AND payload IS NOT NULL) THEN
  RAISE EXCEPTION 'Drain READY appointment outbox events before UUID migration';
 END IF;
END $$;
ALTER TABLE appointment_history DROP CONSTRAINT appointment_history_appointment_id_fkey;
ALTER TABLE operation_results DROP CONSTRAINT operation_results_appointment_id_fkey;
ALTER TABLE outbox_events DROP CONSTRAINT outbox_events_aggregate_id_fkey;
ALTER TABLE reminder_publications DROP CONSTRAINT reminder_publications_appointment_id_fkey;
ALTER TABLE appointments DROP CONSTRAINT appointments_id_check;
ALTER TABLE appointments DROP CONSTRAINT appointments_doctor_id_check;
ALTER TABLE appointments DROP CONSTRAINT appointments_slot_id_check;
ALTER TABLE appointments ALTER COLUMN id DROP IDENTITY;
ALTER TABLE appointments ALTER COLUMN id TYPE uuid USING pg_temp.fclinic_uuid('appointment', id::text);
ALTER TABLE appointments ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE appointments ALTER COLUMN doctor_id TYPE uuid USING pg_temp.fclinic_uuid('doctor', doctor_id::text);
ALTER TABLE appointments ALTER COLUMN slot_id TYPE uuid USING pg_temp.fclinic_uuid('slot', slot_id::text);
ALTER TABLE appointment_history ALTER COLUMN id DROP IDENTITY;
ALTER TABLE appointment_history ALTER COLUMN id TYPE uuid USING pg_temp.fclinic_uuid('appointment-history', id::text);
ALTER TABLE appointment_history ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE appointment_history ALTER COLUMN appointment_id TYPE uuid USING pg_temp.fclinic_uuid('appointment', appointment_id::text);
ALTER TABLE operation_results ALTER COLUMN id DROP IDENTITY;
ALTER TABLE operation_results ALTER COLUMN id TYPE uuid USING pg_temp.fclinic_uuid('operation-result', id::text);
ALTER TABLE operation_results ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE operation_results ALTER COLUMN appointment_id TYPE uuid USING pg_temp.fclinic_uuid('appointment', appointment_id::text);
ALTER TABLE operation_results ALTER COLUMN response_body TYPE text USING pg_temp.fclinic_uuid_json(response_body::jsonb, 'appointment')::text;
ALTER TABLE outbox_events ALTER COLUMN aggregate_id TYPE uuid USING pg_temp.fclinic_uuid('appointment', aggregate_id::text);
-- Pending intents have not been frozen/published and may adopt the new wire contract.
UPDATE outbox_events SET intent = pg_temp.fclinic_uuid_json(intent, 'appointment') || '{"schemaVersion":3}'::jsonb
 WHERE status = 'PENDING_ENRICHMENT' AND payload IS NULL;
ALTER TABLE reminder_publications ALTER COLUMN id DROP IDENTITY;
ALTER TABLE reminder_publications ALTER COLUMN id TYPE uuid USING pg_temp.fclinic_uuid('reminder-publication', id::text);
ALTER TABLE reminder_publications ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE reminder_publications ALTER COLUMN appointment_id TYPE uuid USING pg_temp.fclinic_uuid('appointment', appointment_id::text);
ALTER TABLE appointment_history ADD FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT;
ALTER TABLE operation_results ADD FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT;
ALTER TABLE outbox_events ADD FOREIGN KEY (aggregate_id) REFERENCES appointments(id) ON DELETE RESTRICT;
ALTER TABLE reminder_publications ADD FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT;
