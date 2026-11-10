// Test scope only: real local services, canonical databases, synthetic identities.
const fs=require('node:fs'),path=require('node:path'),{randomUUID}=require('node:crypto'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),file=path.join(root,'.runtime/self-onboarding-state.json');
const env=Object.fromEntries(fs.readFileSync(path.join(root,'ClinicAppointmentBE/.env'),'utf8').split(/\r?\n/).filter(x=>/^[A-Z_]+=/.test(x)).map(x=>[x.slice(0,x.indexOf('=')),x.slice(x.indexOf('=')+1)]));
const state=fs.existsSync(file)?JSON.parse(fs.readFileSync(file)):{};
const evidence=[]; const save=()=>fs.writeFileSync(file,JSON.stringify(state,null,2));
function check(label,pass,details={}){evidence.push({label,pass,...details});if(!pass)throw Error(label);}
async function req(method,route,body,token,status,label,headers={}){
 const response=await fetch('http://localhost:8080'+route,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{}),...headers},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)});
 const json=await response.json();check(label||method+' '+route,response.status===status,{expected:status,actual:response.status});return json.data;
}
const sql=(svc,q)=>execFileSync('docker',['exec','fclinic-postgres-'+svc,'psql','-U',env.DB_USERNAME,'-d',env[svc.toUpperCase()+'_DB_NAME'],'-Atc',q],{encoding:'utf8'}).trim();
const login=u=>req('POST','/api/v1/auth/login',{username:u.username,password:u.password},null,200,'Real login');
async function main(){
 for(const key of ['api','browser']){
  if(!state[key]){const suffix=Date.now().toString(36)+key; state[key]={username:'self.'+suffix,password:'T!'+randomUUID(),email:suffix+'@example.test'};save();}
  if(!state[key].registered){await req('POST','/api/v1/auth/register',{username:state[key].username,password:state[key].password,email:state[key].email,fullName:'Synthetic Self '+key},null,201,'Register '+key);state[key].registered=true;save();}
 }
 let a=await login(state.api),b=await login(state.browser);
 if(!state.patient){
  check('Initially unlinked',a.user.patientId===null);
  await req('GET','/api/v3/patients/self-profile',null,a.accessToken,404,'No initial self profile');
  const payload={fullName:'Synthetic Self API',phoneNumber:'+84900000891',email:state.api.email,dateOfBirth:'1995-01-01',gender:'OTHER'};
  const results=await Promise.all([1,2].map(()=>req('POST','/api/v3/patients',payload,a.accessToken,201,'Concurrent self creation')));
  check('Concurrent requests return one UUID',results[0].id===results[1].id);
  state.patient=results[0];save();
  const retry=await req('POST','/api/v3/patients',payload,a.accessToken,201,'Lost-response creation retry');
  check('Retry same UUID',retry.id===state.patient.id);
 }
 const pid=state.patient.id,subject=JSON.parse(Buffer.from(a.accessToken.split('.')[1],'base64url')).sub;
 check('Canonical Patient DB row',sql('patient',`select count(*) from patients where id='${pid}' and created_by='${subject}' and status='ACTIVE'`)==='1');
 check('Durable owner association',sql('patient',`select count(*) from patient_self_registrations where subject='${subject}' and patient_id='${pid}'`)==='1');
 check('Exactly one profile revision',sql('patient',`select count(*) from patient_revisions where patient_id='${pid}'`)==='1');
 check('No MedicalRecord needed',sql('patient',`select count(*) from medical_records where patient_id='${pid}'`)==='0');
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:pid},b.accessToken,403,'Other account cannot claim profile');
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:randomUUID()},a.accessToken,403,'Arbitrary UUID cannot be linked');
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:pid},a.accessToken,200,'Trusted automatic account linking');
 const again=await req('PUT','/api/v1/auth/me/patient-profile',{patientId:pid},a.accessToken,200,'Link retry is idempotent');
 check('Linked UUID matches',again.patientId===pid);
 const me=await req('GET','/api/v1/auth/me',null,a.accessToken,200,'Verified current user');check('Me exposes binding',me.patientId===pid);
 check('Auth DB binding persisted',sql('auth',`select patient_id from users where id='${me.id}'`)===pid);
 await req('GET','/api/v3/patients/'+pid,null,b.accessToken,403,'Other Patient cannot read profile');
 await req('GET','/api/v3/patients/'+pid,null,null,401,'Anonymous read denied');
 await req('GET','/api/v3/patients/'+pid,null,a.accessToken,200,'Owner reads canonical profile');
 const refresh=await req('POST','/api/v1/auth/refresh',{refreshToken:a.refreshToken},null,200,'Refresh real session');
 check('Refresh preserves identity',refresh.user.patientId===pid);
 await req('POST','/api/v1/auth/logout',{refreshToken:refresh.refreshToken},refresh.accessToken,200,'Logout');
 a=await login(state.api);check('Relogin retains binding',a.user.patientId===pid);
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:randomUUID()},a.accessToken,403,'Cannot rebind linked account');
 const own=await req('GET','/api/v3/patients/self-profile',null,a.accessToken,200,'Recover server-owned profile after relogin');check('Same durable profile',own.id===pid);
 state.lastVerified=new Date().toISOString();save();
}
async function runtime(){
 const b=await login(state.browser),a=await login(state.api);
 const me=await req('GET','/api/v1/auth/me',null,b.accessToken,200,'Browser-created profile linked in Auth');
 check('Browser identity has Patient UUID',!!me.patientId);
 const profile=await req('GET','/api/v3/patients/'+me.patientId,null,b.accessToken,200,'Read browser-created canonical profile');
 check('Browser form fields persisted',profile.fullName==='Synthetic Self browser'&&profile.dateOfBirth==='1996-02-03'&&profile.address==='Synthetic local acceptance');
 check('Browser profile row in canonical PostgreSQL',sql('patient',`select count(*) from patients p join patient_self_registrations s on s.patient_id=p.id where p.id='${me.patientId}' and s.subject=p.created_by`)==='1');
 check('Browser profile has no MedicalRecord',sql('patient',`select count(*) from medical_records where patient_id='${me.patientId}'`)==='0');
 check('Unique Auth binding migration applied',sql('auth',"select count(*) from flyway_schema_history where version='3' and success") === '1');
 const realm=JSON.parse(fs.readFileSync(path.join(root,'ClinicAppointmentBE/infra/keycloak/fclinic-realm.json')));
 const doctor=realm.users.find(u=>u.realmRoles?.includes('ROLE_DOCTOR'));
 const d=await login({username:doctor.username,password:doctor.credentials.find(c=>c.type==='password').value});
 const previous=JSON.parse(fs.readFileSync(path.join(root,'.runtime/acceptance-state.json')));
 if(!state.slot){state.slot=await req('POST',`/api/v1/doctors/${previous.doctor.id}/slots`,{slotDate:new Date(Date.now()+3*86400000).toISOString().slice(0,10),startTime:'16:00',endTime:'17:00'},d.accessToken,201,'Publish actual future Doctor UUID slot');save();}
 if(!state.booking){state.bookingKey=state.bookingKey||randomUUID();save();state.booking=await req('POST','/api/v2/appointments',{patientId:state.patient.id,doctorId:previous.doctor.id,slotId:state.slot.id,reasonForVisit:'Synthetic self-onboarding acceptance'},a.accessToken,201,'Book with automatically linked Patient and no MedicalRecord',{'Idempotency-Key':state.bookingKey});save();}
 const booking=await req('GET','/api/v2/appointments/'+state.booking.id,null,a.accessToken,200,'Read real booking');
 check('Booking CONFIRMED',booking.status==='CONFIRMED');
 check('Booking PostgreSQL patient association',sql('appointment',`select count(*) from appointments where id='${booking.id}' and patient_id='${state.patient.id}' and status='CONFIRMED'`)==='1');
 const history=await req('GET',`/api/v2/appointments/${booking.id}/history`,null,a.accessToken,200,'Booking history persisted');check('History exists',history.length>0);
 check('Booking Patient still has no MedicalRecord',sql('patient',`select count(*) from medical_records where patient_id='${state.patient.id}'`)==='0');
 // The first API fixture omitted optional Patient contact email. The consumer
 // correctly skips that Patient's email; do not report a fabricated delivery.
 if(!state.patient.email) check('Profile without contact email is not falsely notified',
  sql('notification',`select count(*) from notifications where appointment_id='${booking.id}' and recipient_user_id='${a.user.id}'`)==='0');
 if(!state.browserSlot){state.browserSlot=await req('POST',`/api/v1/doctors/${previous.doctor.id}/slots`,{slotDate:new Date(Date.now()+3*86400000).toISOString().slice(0,10),startTime:'17:00',endTime:'18:00'},d.accessToken,201,'Publish browser Patient future slot');save();}
 if(!state.browserBooking){state.browserBookingKey=state.browserBookingKey||randomUUID();save();state.browserBooking=await req('POST','/api/v2/appointments',{patientId:me.patientId,doctorId:previous.doctor.id,slotId:state.browserSlot.id,reasonForVisit:'Synthetic browser self-profile acceptance'},b.accessToken,201,'Browser-created Patient books without MedicalRecord',{'Idempotency-Key':state.browserBookingKey});save();}
 check('Browser Patient booking CONFIRMED',state.browserBooking.status==='CONFIRMED');
 let notification;
 for(let i=0;i<20;i++){
  const inbox=await req('GET','/api/v1/notifications/me?size=100',null,b.accessToken,200,'Read browser Patient real Notification inbox');
  notification=inbox.content.find(n=>n.appointmentId===state.browserBooking.id);if(notification)break;
  await new Promise(r=>setTimeout(r,1000));
 }
 check('Real booking Notification reached exact account',notification?.recipientUserId===b.user.id);
 state.browserPatientId=me.patientId;save();
}
const phase=process.argv[2]==='runtime'?'runtime':'main';
(phase==='runtime'?runtime():main()).then(()=>{fs.writeFileSync(path.join(root,'docs/integration/'+(phase==='runtime'?'self-onboarding-runtime-evidence.json':'self-onboarding-evidence.json')),JSON.stringify({verifiedAt:new Date().toISOString(),checks:evidence},null,2));console.log(JSON.stringify({checks:evidence.length,pass:true,patientId:state.patient.id}));}).catch(e=>{console.error(e.message);console.log(JSON.stringify(evidence.slice(-3)));process.exitCode=1;});
