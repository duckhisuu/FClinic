import { describe, expect, it } from 'vitest';
import { blankProfile, localMinute, medicalPayload, phone, profilePayload, UUID } from '../model';
import { makeFixtures, PREVIEW_DOCTOR } from '../preview/fixtures';
describe('PatientInput binding', () => {
  it('requires name/phone and validates length/date/email/enums', () => {
    const { errors } = profilePayload({ ...blankProfile, fullName: ' ', phoneNumber: 'abc', dateOfBirth: '2099-01-01', email: 'not-email', gender: 'X', address: 'x'.repeat(2001) });
    expect(Object.keys(errors)).toEqual(expect.arrayContaining(['fullName', 'phoneNumber', 'dateOfBirth', 'email', 'gender', 'address']));
    expect(profilePayload({ ...blankProfile, fullName: 'An', phoneNumber: '0900000001', dateOfBirth: '2025-02-31' }).errors.dateOfBirth).toBeTruthy();
  });
  it('builds exact create vs complete PUT payloads, never response fields', () => {
    const patient = makeFixtures().patients[0];
    const v = { ...patient, phoneNumber: '0900 000 001', email: '', address: '', dateOfBirth: '' };
    const create = profilePayload(v); expect(create.errors).toEqual({});
    expect(Object.keys(create.body).sort()).toEqual(['fullName', 'phoneNumber', 'email', 'dateOfBirth', 'gender', 'address'].sort());
    expect(create.body).toMatchObject({ phoneNumber: '+84900000001', email: null, address: null, dateOfBirth: null });
    const put = profilePayload(v, patient); expect(put.body.version).toBe(patient.version); expect(put.body.status).toBe(patient.status);
    expect(put.body.id).toBeUndefined(); expect(put.body.createdAt).toBeUndefined();
  });
  it('counts Unicode code points and refuses imprecise version', () => {
    const v = { ...blankProfile, fullName: '😀'.repeat(255), phoneNumber: '0900000001' };
    expect(profilePayload(v).errors).toEqual({});
    expect(profilePayload(v, { version: Number.MAX_SAFE_INTEGER + 1 }).errors.version).toBeTruthy();
  });
  it.each(['0', '-1', '900', '550e8400-e29b-11d4-a716-44665544000c', '550E8400-E29B-41D4-A716-44665544000C'])('does not turn invalid Doctor %s into UUID', doctorId => {
    expect(medicalPayload({ doctorId, appointmentId: '', visitDate: '2025-01-01T10:00', symptoms: 'A', diagnosis: 'B', treatment: '', notes: '' }).errors.doctorId).toBeTruthy();
  });
  it('clinical PUT excludes immutable IDs and requires changeReason', () => {
    const record = makeFixtures().records[0];
    const v = { ...record, visitDate: localMinute(record.visitDate), changeReason: '', notes: record.notes || '', treatment: record.treatment || '' };
    expect(medicalPayload(v, record).errors.changeReason).toBeTruthy();
    const result = medicalPayload({ ...v, changeReason: 'Lý do minh họa' }, record);
    expect(result.body).not.toHaveProperty('doctorId'); expect(result.body).not.toHaveProperty('appointmentId');
    expect(result.body.visitDate).toBe(record.visitDate); expect(result.body.version).toBe(record.version);
    expect(UUID.test(record.id)).toBe(true);
  });
  it('phone search uses normalized full phone, not arbitrary numeric coercion', () => { expect(phone('0900 000 001')).toBe('+84900000001'); expect(phone('0900')).toBeNull(); });
});
