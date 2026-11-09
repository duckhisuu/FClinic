// Explicit synthetic acceptance data against real services; never imported by runtime code.
const fs = require('node:fs');
const http = require('node:http');
const https = require('node:https');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const runtime = path.join(root, '.runtime');
const realm = JSON.parse(fs.readFileSync(path.join(root, 'ClinicAppointmentBE/infra/keycloak/fclinic-realm.json')));
const stateFile = path.join(runtime, 'acceptance-state.json');
const state = fs.existsSync(stateFile) ? JSON.parse(fs.readFileSync(stateFile)) : {};
const evidence = [];
const phase = process.argv[2] || 'verify';
const save = () => fs.writeFileSync(stateFile, JSON.stringify(state, null, 2));
const ca = fs.readFileSync(path.join(runtime, 'localhost.pem'));
async function request(method, route, body, token, expected, label = route, port = 8081, headers = {}) {
    const bytes = body === undefined ? undefined : JSON.stringify(body);
    const result = await new Promise((resolve, reject) => {
        const transport = port === 8085 ? https : http;
        const req = transport.request({ hostname: 'localhost', port, path: route, method, ca,
            headers: { ...(bytes ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(bytes) } : {}),
                ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers } }, res => {
            let text = ''; res.on('data', b => text += b); res.on('end', () => {
                let parsed; try { parsed = JSON.parse(text); } catch { parsed = { message: text.slice(0, 100) }; }
                resolve({ status: res.statusCode, body: parsed });
            });
        });
        req.setTimeout(15000, () => req.destroy(new Error('timeout'))); req.on('error', reject);
        if (bytes) req.write(bytes); req.end();
    });
    if (expected !== undefined) {
        evidence.push({ label, method, route, expected, actual: result.status, pass: result.status === expected });
        if (result.status !== expected) console.error(`${label}: expected ${expected}, got ${result.status} (${result.body.code || result.body.message})`);
    }
    return result;
}
async function login(role) {
    const account = realm.users.find(u => u.realmRoles?.includes(role));
    const password = account.credentials.find(c => c.type === 'password').value;
    const result = await request('POST', '/api/v1/auth/login', { username: account.username, password }, null, 200, `login ${role}`, 8085);
    if (!result.body.data?.accessToken) throw new Error(`Cannot authenticate ${role}`);
    return result.body.data;
}
function assignments() {
    const now = Date.now();
    const staff = realm.users.find(u => u.realmRoles?.includes('ROLE_STAFF'));
    const doctor = realm.users.find(u => u.realmRoles?.includes('ROLE_DOCTOR'));
    const ids = state.patient ? [state.patient.id, ...(state.additionalPatientIds || [])] : [];
    const common = { active: true, 'all-patients': false, 'valid-from': new Date(now - 60000).toISOString(), 'valid-until': new Date(now + 8 * 3600000).toISOString(), 'patient-ids': ids };
    const grants = {
        '94dcfc19-f6bf-4fce-a33f-abd5bb217941': { ...common, subject: staff.id,
            operations: ['ADMIN_ASSIGNMENT_CREATE', 'ADMIN_ASSIGNMENT_READ', 'ADMIN_ASSIGNMENT_UPDATE', 'ADMIN_ASSIGNMENT_SEARCH', 'ADMIN_PROFILE_READ', 'ADMIN_PROFILE_UPDATE', 'ADMIN_SEARCH', 'APPOINTMENT_CREATE', 'APPOINTMENT_READ', 'APPOINTMENT_SEARCH', 'APPOINTMENT_AVAILABILITY', 'APPOINTMENT_CANCEL', 'APPOINTMENT_RESCHEDULE'] },
        '94dcfc19-f6bf-4fce-a33f-abd5bb217942': { ...common, subject: doctor.id,
            operations: ['DOCTOR_PROFILE_READ', 'DOCTOR_SEARCH', 'MEDICAL_READ', 'MEDICAL_CREATE', 'MEDICAL_UPDATE'] },
    };
    if (state.visitDate) grants['94dcfc19-f6bf-4fce-a33f-abd5bb217943'] = { ...common, subject: doctor.id,
        operations: ['VALID_VISIT'], 'patient-ids': [state.patient.id], 'visit-date': state.visitDate, 'visit-id': state.visitId };
    if (state.uiVisitDate) grants['94dcfc19-f6bf-4fce-a33f-abd5bb217944'] = { ...common, subject: doctor.id, operations: ['VALID_VISIT'], 'patient-ids': [state.patient.id], 'visit-date': state.uiVisitDate, 'visit-id': state.uiVisitId };
    fs.writeFileSync(path.join(runtime, 'assignments.json'), JSON.stringify({ app: { authorization: { grants } } }, null, 2));
}
async function main() {
    if (phase === 'assign') { assignments(); console.log('Finite synthetic assignments prepared; restart Auth to load them.'); return; }
    const staff = await login('ROLE_STAFF');
    if (phase === 'negative') {
        const doctor = await login('ROLE_DOCTOR');
        const route = `/api/v3/patients/${state.patient.id}`;
        const current = await request('GET', route, undefined, staff.accessToken, 200, 'baseline profile', 8080);
        const body = Object.fromEntries(['fullName', 'phoneNumber', 'email', 'dateOfBirth', 'gender', 'address', 'status', 'version'].map(k => [k, current.body.data[k]]));
        body.address = 'UNAUTHORIZED MUST NOT PERSIST';
        await request('GET', `/api/v3/patients/${randomUUID()}`, undefined, staff.accessToken, 403, 'missing exact Patient scope', 8080);
        await request('PUT', route, body, doctor.accessToken, 403, 'unauthorized profile write', 8080);
        await request('POST', '/api/v3/patients', { fullName: 'UNAUTHORIZED', phoneNumber: '+84900000000' }, doctor.accessToken, 403, 'Doctor cannot create profile', 8080);
        await request('GET', `/internal/v1/doctors/${state.doctor.id}`, undefined, undefined, 401, 'Doctor reference requires machine credentials', 8082);
        fs.writeFileSync(path.join(__dirname, 'authorization-negative-evidence.json'), JSON.stringify({ timestamp: new Date().toISOString(), evidence }, null, 2));
        console.log(`${evidence.filter(e => e.pass).length}/${evidence.length} authorization checks passed.`);
        if (evidence.some(e => !e.pass)) process.exitCode = 1;
        return;
    }
    if (phase === 'outage') {
        const doctor = await login('ROLE_DOCTOR');
        await request('POST', `/api/v3/patients/${state.patient.id}/medical-records`, {
            doctorId: state.doctor.id, appointmentId: null, visitDate: state.visitDate,
            symptoms: 'Synthetic provider outage', diagnosis: 'Must not be persisted', treatment: null, notes: null
        }, doctor.accessToken, 503, 'Doctor provider outage must not write', 8080);
        fs.writeFileSync(path.join(__dirname, 'provider-outage-evidence.json'), JSON.stringify({ timestamp: new Date().toISOString(), evidence }, null, 2));
        console.log(`${evidence.filter(e => e.pass).length}/${evidence.length} outage checks passed.`);
        if (evidence.some(e => !e.pass)) process.exitCode = 1;
        return;
    }
    if (phase === 'cross') {
        const doctor = await login('ROLE_DOCTOR');
        const patient = await login('ROLE_PATIENT');
        const date = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
        if (!state.slots) {
            state.slots = [];
            for (const hour of [9, 10]) {
                const slot = await request('POST', `/api/v1/doctors/${state.doctor.id}/slots`, { slotDate: date, startTime: `${hour.toString().padStart(2, '0')}:00`, endTime: `${(hour + 1).toString().padStart(2, '0')}:00` }, staff.accessToken, 201, 'publish real 60-minute slot', 8082);
                if (slot.body.data?.id) state.slots.push(slot.body.data);
            }
            save();
        }
        await request('GET', `/api/v2/appointments/available-slots?doctorId=${state.doctor.id}&date=${date}`, undefined, patient.accessToken, 200, 'availability through real Doctor provider', 8080);
        if (!state.appointment && state.slots[0]) {
            const booked = await request('POST', '/api/v2/appointments', { patientId: state.patient.id, doctorId: state.doctor.id, slotId: state.slots[0].id, reasonForVisit: 'Synthetic integration smoke' }, staff.accessToken, 201, 'Staff booking through Patient M2M lookup', 8080, { 'Idempotency-Key': randomUUID() });
            if (booked.body.data?.id) { state.appointment = booked.body.data; save(); }
        }
        await request('GET', `/api/v2/appointments?doctorId=${state.doctor.id}&page=0&size=10`, undefined, doctor.accessToken, 200, 'Doctor schedule Patient association', 8080);
        if (state.appointment) {
            const route = `/api/v2/appointments/${state.appointment.id}`;
            const current = await request('GET', route, undefined, patient.accessToken, 200, 'Patient reads own booking', 8080);
            const version = current.body.data.version;
            if (current.body.data.status === 'CONFIRMED' && state.slots[1]) {
                const changed = await request('PUT', `${route}/reschedule`, { slotId: state.slots[1].id, version, reason: 'Synthetic reschedule smoke' }, patient.accessToken, 200, 'reschedule exact UUID/version', 8080, { 'Idempotency-Key': randomUUID() });
                if (changed.status === 200) await request('PUT', `${route}/cancel`, { version: changed.body.data.version, reason: 'Synthetic cancellation smoke' }, patient.accessToken, 200, 'cancel exact UUID/version', 8080, { 'Idempotency-Key': randomUUID() });
            }
        }
        fs.writeFileSync(path.join(__dirname, 'cross-service-http-evidence.json'), JSON.stringify({ timestamp: new Date().toISOString(), evidence }, null, 2));
        console.log(`${evidence.filter(e => e.pass).length}/${evidence.length} cross-service checks passed.`);
        if (evidence.some(e => !e.pass)) process.exitCode = 1;
        return;
    }
    if (phase === 'create') {
        if (!state.patient) {
            const r = await request('POST', '/api/v3/patients', { fullName: 'FClinic Acceptance Synthetic', phoneNumber: '+84900009876', email: 'acceptance@example.test', dateOfBirth: '1995-01-01', gender: 'UNKNOWN', address: 'Synthetic localhost test' }, staff.accessToken, 201, 'create synthetic profile');
            if (!r.body.data?.id) throw new Error('Profile creation failed'); state.patient = r.body.data;
        }
        if (!state.doctor) {
            const r = await request('POST', '/api/v1/doctors', { name: 'FClinic Acceptance Doctor', specialty: 'General', department: 'Synthetic QA', consultationFee: 100000 }, staff.accessToken, 201, 'create real Doctor UUID', 8082);
            state.doctor = r.body.data;
            const admin = await login('ROLE_ADMIN');
            const doctor = await login('ROLE_DOCTOR');
            await request('PUT', `/api/v1/auth/admin/users/${doctor.user.id}`, { doctorId: state.doctor.id }, admin.accessToken, 200, 'trusted admin Doctor binding', 8085);
            const patient = await login('ROLE_PATIENT');
            await request('PUT', `/api/v1/auth/admin/users/${patient.user.id}`, { patientId: state.patient.id }, admin.accessToken, 200, 'trusted admin Patient binding', 8085);
        }
        state.visitDate ||= new Date(Date.now() - 60000).toISOString().replace(/\.\d{3}Z$/, 'Z');
        state.visitId ||= randomUUID();
        state.uiVisitDate ||= state.visitDate.slice(0, 16) + ':00Z'; state.uiVisitId ||= randomUUID(); save(); assignments();
        console.log('Real profile/Doctor created and exact synthetic visit assigned; restart Auth.'); return;
    }
    if (!state.patient || !state.doctor) throw new Error('Run assign, restart, create, restart first.');
    const p = state.patient.id;
    const doctor = await login('ROLE_DOCTOR'); const patient = await login('ROLE_PATIENT');
    await request('GET', '/api/v1/auth/me', undefined, staff.accessToken, 200, 'verified user profile', 8085);
    await request('GET', '/api/v1/auth/me/patient-capabilities', undefined, staff.accessToken, 200, 'backend capabilities', 8085);
    await request('GET', '/api/v3/patients', undefined, null, 401, 'missing token');
    await request('GET', '/api/v3/patients', undefined, 'invalid.token.value', 401, 'invalid token');
    await request('GET', '/api/v3/patients', undefined, patient.accessToken, 403, 'patient cannot search');
    await request('GET', `/api/v3/patients/${randomUUID()}`, undefined, patient.accessToken, 403, 'wrong patient');
    await request('GET', '/api/v3/patients/not-a-uuid', undefined, staff.accessToken, 400, 'invalid UUID');
    await request('GET', `/api/v3/patients?keyword=Acceptance&page=0&size=1`, undefined, staff.accessToken, 200, 'search and pagination');
    const current = await request('GET', `/api/v3/patients/${p}`, undefined, staff.accessToken, 200, 'read profile');
    const profile = current.body.data;
    const body = Object.fromEntries(['fullName', 'phoneNumber', 'email', 'dateOfBirth', 'gender', 'address', 'status', 'version'].map(k => [k, profile[k]]));
    body.address = 'Synthetic acceptance updated ' + randomUUID();
    await request('PUT', `/api/v3/patients/${p}`, body, staff.accessToken, 200, 'replace profile');
    await request('PUT', `/api/v3/patients/${p}`, body, staff.accessToken, 409, 'stale profile version');
    const clinical = { doctorId: state.doctor.id, appointmentId: null, visitDate: state.visitDate, symptoms: 'Synthetic symptom', diagnosis: 'Synthetic acceptance only', treatment: 'Synthetic test', notes: 'No real medical data' };
    if (!state.record) {
        const created = await request('POST', `/api/v3/patients/${p}/medical-records`, clinical, doctor.accessToken, 201, 'create record with exact VALID_VISIT');
        if (created.body.data?.id) { state.record = created.body.data; save(); }
    }
    await request('POST', `/api/v3/patients/${p}/medical-records`, { ...clinical, doctorId: randomUUID() }, doctor.accessToken, 403, 'wrong doctor');
    await request('POST', `/api/v3/patients/${p}/medical-records`, clinical, staff.accessToken, 403, 'unauthorized clinical write');
    await request('GET', `/api/v3/patients/${p}/medical-records?page=0&size=1`, undefined, doctor.accessToken, 200, 'list records');
    if (state.record) {
        const route = `/api/v3/patients/${p}/medical-records/${state.record.id}`;
        const read = await request('GET', route, undefined, doctor.accessToken, 200, 'read medical record');
        const r = read.body.data;
        const update = { visitDate: r.visitDate, symptoms: r.symptoms, diagnosis: r.diagnosis, treatment: r.treatment, notes: 'Synthetic updated ' + randomUUID(), version: r.version, changeReason: 'Acceptance regression verification' };
        await request('PUT', route, update, doctor.accessToken, 200, 'replace medical record');
        await request('PUT', route, update, doctor.accessToken, 409, 'stale record version');
        await request('GET', route, undefined, patient.accessToken, 200, 'patient reads own record');
    }
    await request('POST', '/api/v1/auth/refresh', { refreshToken: staff.refreshToken }, undefined, 200, 'refresh rotation', 8085);
    await request('POST', '/api/v1/auth/logout', { refreshToken: doctor.refreshToken }, doctor.accessToken, 200, 'logout', 8085);
    await request('GET', `/api/v3/patients/${p}`, undefined, doctor.accessToken, 401, 'revoked token rejected');
    fs.writeFileSync(path.join(__dirname, 'runtime-http-evidence.json'), JSON.stringify({ timestamp: new Date().toISOString(), evidence }, null, 2));
    console.log(`${evidence.filter(e => e.pass).length}/${evidence.length} HTTP checks passed; evidence contains no tokens.`);
    if (evidence.some(e => !e.pass)) process.exitCode = 1;
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
