-- Convert owned IDs with persisted random UUIDv4 mappings. Never invent another service's IDs.
CREATE OR REPLACE FUNCTION pg_temp.is_uuid_v4(value uuid) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT AS $$
 SELECT value::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
$$;
INSERT INTO appointment_uuid_v4_mapping(resource_type,old_id,new_id)
 SELECT kind,id,gen_random_uuid() FROM (
  SELECT 'appointment' AS kind,id FROM appointments UNION ALL
  SELECT 'appointment-history',id FROM appointment_history UNION ALL
  SELECT 'operation-result',id FROM operation_results UNION ALL
  SELECT 'event',id FROM outbox_events UNION ALL
  SELECT 'reminder-publication',id FROM reminder_publications
 ) ids WHERE NOT pg_temp.is_uuid_v4(id)
 ON CONFLICT(resource_type,old_id) DO NOTHING;

CREATE OR REPLACE FUNCTION pg_temp.uuid_v4(kind text,value uuid) RETURNS uuid
LANGUAGE plpgsql STABLE STRICT AS $$
DECLARE mapped uuid;
BEGIN
 IF pg_temp.is_uuid_v4(value) THEN RETURN value; END IF;
 SELECT new_id INTO mapped FROM appointment_uuid_v4_mapping WHERE resource_type=kind AND old_id=value;
 IF mapped IS NULL THEN
  RAISE EXCEPTION 'Missing authoritative UUIDv4 mapping for % ID %. Prepare mapping after V3 before V4; no external ID is generated automatically.',kind,value;
 END IF;
 RETURN mapped;
END $$;
CREATE OR REPLACE FUNCTION pg_temp.uuid_v4_json(value jsonb,root_kind text) RETURNS jsonb
LANGUAGE plpgsql STABLE AS $$
DECLARE result jsonb; pair record; kind text;
BEGIN
 IF jsonb_typeof(value)='object' THEN
  result := '{}'::jsonb;
  FOR pair IN SELECT * FROM jsonb_each(value) LOOP
   kind := CASE pair.key WHEN 'id' THEN root_kind WHEN 'appointmentId' THEN 'appointment'
    WHEN 'aggregateId' THEN 'appointment' WHEN 'eventId' THEN 'event'
    WHEN 'patientId' THEN 'patient' WHEN 'doctorId' THEN 'doctor'
    WHEN 'slotId' THEN 'slot' WHEN 'previousSlotId' THEN 'slot'
    WHEN 'userId' THEN 'user' WHEN 'recipientUserId' THEN 'user' ELSE NULL END;
   IF kind IS NOT NULL AND pair.value<>'null'::jsonb THEN
    result := result || jsonb_build_object(pair.key,pg_temp.uuid_v4(kind,(pair.value #>> '{}')::uuid));
   ELSE
    result := result || jsonb_build_object(pair.key,pg_temp.uuid_v4_json(pair.value,root_kind));
   END IF;
  END LOOP;
  RETURN result;
 ELSIF jsonb_typeof(value)='array' THEN
  SELECT COALESCE(jsonb_agg(pg_temp.uuid_v4_json(v,root_kind)),'[]'::jsonb) INTO result FROM jsonb_array_elements(value) v;
  RETURN result;
 END IF;
 RETURN value;
END $$;

-- Fail before touching rows when external mappings are missing or frozen events need draining.
DO $$ BEGIN
 PERFORM pg_temp.uuid_v4('patient',patient_id),pg_temp.uuid_v4('doctor',doctor_id),pg_temp.uuid_v4('slot',slot_id) FROM appointments;
 IF EXISTS(SELECT 1 FROM outbox_events WHERE status='READY') THEN
  RAISE EXCEPTION 'Drain READY appointment outbox events before UUIDv4 cutover';
 END IF;
END $$;
ALTER TABLE appointment_history DROP CONSTRAINT appointment_history_appointment_id_fkey;
ALTER TABLE operation_results DROP CONSTRAINT operation_results_appointment_id_fkey;
ALTER TABLE outbox_events DROP CONSTRAINT outbox_events_aggregate_id_fkey;
ALTER TABLE reminder_publications DROP CONSTRAINT reminder_publications_appointment_id_fkey;
ALTER TABLE reminder_publications DROP CONSTRAINT reminder_publications_event_id_fkey;

UPDATE appointments SET id=pg_temp.uuid_v4('appointment',id),patient_id=pg_temp.uuid_v4('patient',patient_id),
 doctor_id=pg_temp.uuid_v4('doctor',doctor_id),slot_id=pg_temp.uuid_v4('slot',slot_id);
UPDATE appointment_history SET id=pg_temp.uuid_v4('appointment-history',id),appointment_id=pg_temp.uuid_v4('appointment',appointment_id);
UPDATE operation_results SET id=pg_temp.uuid_v4('operation-result',id),appointment_id=pg_temp.uuid_v4('appointment',appointment_id),
 response_body=CASE WHEN response_body::jsonb=pg_temp.uuid_v4_json(response_body::jsonb,'appointment') THEN response_body
  ELSE pg_temp.uuid_v4_json(response_body::jsonb,'appointment')::text END;
UPDATE outbox_events SET id=pg_temp.uuid_v4('event',id),aggregate_id=pg_temp.uuid_v4('appointment',aggregate_id);
UPDATE outbox_events SET intent=pg_temp.uuid_v4_json(intent,'appointment')
 WHERE status='PENDING_ENRICHMENT' AND payload IS NULL;
-- PUBLISHED/SUPPRESSED snapshots are immutable historical evidence, never replayed as new events.
UPDATE reminder_publications SET id=pg_temp.uuid_v4('reminder-publication',id),
 appointment_id=pg_temp.uuid_v4('appointment',appointment_id),event_id=pg_temp.uuid_v4('event',event_id);

ALTER TABLE appointment_history ADD FOREIGN KEY(appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT;
ALTER TABLE operation_results ADD FOREIGN KEY(appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT;
ALTER TABLE outbox_events ADD FOREIGN KEY(aggregate_id) REFERENCES appointments(id) ON DELETE RESTRICT;
ALTER TABLE reminder_publications ADD FOREIGN KEY(appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT;
ALTER TABLE reminder_publications ADD FOREIGN KEY(event_id) REFERENCES outbox_events(id) ON DELETE RESTRICT;
ALTER TABLE appointments ADD CONSTRAINT appointments_uuid_v4 CHECK(
 id::text ~ '^........-....-4...-[89ab]...-............$' AND patient_id::text ~ '^........-....-4...-[89ab]...-............$'
 AND doctor_id::text ~ '^........-....-4...-[89ab]...-............$' AND slot_id::text ~ '^........-....-4...-[89ab]...-............$');
ALTER TABLE appointment_history ADD CONSTRAINT history_uuid_v4 CHECK(
 id::text ~ '^........-....-4...-[89ab]...-............$' AND appointment_id::text ~ '^........-....-4...-[89ab]...-............$');
ALTER TABLE operation_results ADD CONSTRAINT operations_uuid_v4 CHECK(
 id::text ~ '^........-....-4...-[89ab]...-............$' AND appointment_id::text ~ '^........-....-4...-[89ab]...-............$');
ALTER TABLE outbox_events ADD CONSTRAINT outbox_uuid_v4 CHECK(
 id::text ~ '^........-....-4...-[89ab]...-............$' AND aggregate_id::text ~ '^........-....-4...-[89ab]...-............$');
ALTER TABLE reminder_publications ADD CONSTRAINT reminders_uuid_v4 CHECK(
 id::text ~ '^........-....-4...-[89ab]...-............$' AND appointment_id::text ~ '^........-....-4...-[89ab]...-............$'
 AND event_id::text ~ '^........-....-4...-[89ab]...-............$');
