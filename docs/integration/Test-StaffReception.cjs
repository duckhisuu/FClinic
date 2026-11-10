// Real local acceptance only; synthetic Patient data and real scoped identities.
const fs=require('node:fs'),path=require('node:path'),{randomUUID,createHash}=require('node:crypto'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),runtime=path.join(root,'.runtime'),file=path.join(runtime,'staff-reception-state.json');
const state=fs.existsSync(file)?JSON.parse(fs.readFileSync(file)):{};
const phase=process.argv[2]||'verify',report=path.join(root,'docs/integration/staff-reception-evidence.json');
const evidence=fs.existsSync(report)?JSON.parse(fs.readFileSync(report)).checks:[];
const realm=JSON.parse(fs.readFileSync(path.join(root,'ClinicAppointmentBE/infra/keycloak/fclinic-realm.json')));
const env=Object.fromEntries(fs.readFileSync(path.join(root,'ClinicAppointmentBE/.env'),'utf8').split(/\r?\n/).filter(x=>/^[A-Z_]+=/.test(x)).map(x=>[x.slice(0,x.indexOf('=')),x.slice(x.indexOf('=')+1)]));
const save=()=>fs.writeFileSync(file,JSON.stringify(state,null,2));
const publish=()=>fs.writeFileSync(report,JSON.stringify({updatedAt:new Date().toISOString(),patientId:state.patient?.id,bookingId:state.booking?.id,checks:evidence},null,2));
function check(label,pass,details={}){evidence.push({phase,label,pass,...details});publish();if(!pass)throw Error(label);}
async function req(method,route,body,token,status,label,headers={}){
 const r=await fetch('http://localhost:8080'+route,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{}),...headers},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)});
 const text=await r.text();let data;try{data=JSON.parse(text)}catch{data={}};
 check(label,r.status===status,{method,route,expected:status,actual:r.status,...(data.code?{code:data.code}:{})});return data.data;
}
const sql=(svc,q)=>execFileSync('docker',['exec','fclinic-postgres-'+svc,'psql','-U',env.DB_USERNAME,'-d',env[svc.toUpperCase()+'_DB_NAME'],'-Atc',q],{encoding:'utf8'}).trim();
const login=role=>{const u=realm.users.find(u=>u.realmRoles?.includes(role));return req('POST','/api/v1/auth/login',{username:u.username,password:u.credentials.find(c=>c.type==='password').value},null,200,'Real '+role+' login')};
const hash=()=>createHash('sha256').update(fs.readFileSync(path.join(runtime,'assignments.json'))).digest('hex');
async function main(){
 const staff=await login('ROLE_STAFF'),doctor=await login('ROLE_DOCTOR'),patient=await login('ROLE_PATIENT');
 const subject=JSON.parse(Buffer.from(staff.accessToken.split('.')[1],'base64url')).sub;
 state.assignmentsHash ||= hash();save();
 const caps=await req('GET','/api/v1/auth/me/patient-capabilities',null,staff.accessToken,200,'Reception capabilities loaded');check('Desk lookup is explicitly allowed',caps.receptionLookup===true);
 if(phase==='browser'){
  const p=process.argv[3]||'f8ab36f8-eb81-4565-b7be-27cf88a8b2b2';
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(p))throw Error('Invalid browser fixture UUID');
  const profile=await req('GET','/api/v3/patients/'+p,null,staff.accessToken,200,'Read React-created profile after full service restart');
  check('Browser edit persisted with optimistic version',profile.fullName==='QA Staff UI Access 20261110' && profile.address==='Synthetic Staff UI updated' && profile.version===1);
  check('Canonical PostgreSQL contains browser edit',sql('patient',`select count(*) from patients where id='${p}' and address='Synthetic Staff UI updated' and version=1`)==='1');
  check('Browser profile has persisted exact Staff assignment',sql('auth',`select count(*) from reception_patient_assignments where subject='${subject}' and patient_id='${p}'`)!=='0');
  check('Browser access did not edit static assignments',hash()===state.assignmentsHash);
  return;
 }
 if(phase==='persist'){
  check('Database grant survived Auth restart before any Patient read',sql('auth',`select count(*) from reception_patient_assignments where subject='${subject}' and patient_id='${state.patient.id}'`)!=='0');
  check('Exact stored assignment ID survives restart',sql('auth',`select id from reception_patient_assignments where subject='${subject}' and patient_id='${state.patient.id}' order by id limit 1`)===state.grantId);
  await req('GET','/api/v3/patients/'+state.patient.id,null,staff.accessToken,200,'Staff reads after restart');
  const current=await req('GET','/api/v1/auth/me/patient-capabilities',null,staff.accessToken,200,'Persisted capabilities after restart');check('Persisted update permission',current.updatePatientIds.includes(state.patient.id));
  check('No Patient UUID was added to static configuration',hash()===state.assignmentsHash);return;
 }
 if(!state.patient){
  state.name='QA Staff Access '+Date.now().toString(36);save();
  state.patient=await req('POST','/api/v3/patients',{fullName:state.name,phoneNumber:'+84900000961',email:'staff.access.'+Date.now()+'@example.test',gender:'UNKNOWN',address:'Synthetic reception regression'},staff.accessToken,201,'Staff creates actual Patient');save();
 }
 const p=state.patient.id,base='/api/v3/patients/'+p;
 const read=await req('GET',base,null,staff.accessToken,200,'Immediately read newly created profile without config edits/restart');check('Created UUID read back exactly',read.id===p);
 const current=await req('GET','/api/v1/auth/me/patient-capabilities',null,staff.accessToken,200,'Refresh real capabilities');check('New profile is editable immediately',current.updatePatientIds.includes(p));
 const body=Object.fromEntries(['fullName','phoneNumber','email','dateOfBirth','gender','address','status','version'].map(k=>[k,read[k]]));body.address='Synthetic Staff immediate update '+(read.version+1);
 const changed=await req('PUT',base,body,staff.accessToken,200,'Staff updates newly created profile');check('Version advances',changed.version===read.version+1);
 const old=JSON.parse(fs.readFileSync(path.join(runtime,'self-onboarding-state.json'))).patient;
 const found=await req('GET','/api/v3/patients/reception-lookup?keyword='+encodeURIComponent(old.patientCode)+'&page=0&size=10',null,staff.accessToken,200,'Lookup existing Patient outside configured finite IDs');
 check('Existing profile found in real database',found.content.some(v=>v.id===old.id));
 await req('GET','/api/v3/patients/'+old.id,null,staff.accessToken,200,'Open existing looked-up profile');
 const own=await req('GET','/api/v3/patients?keyword='+encodeURIComponent(state.name)+'&page=0&size=10',null,staff.accessToken,200,'Canonical assigned search includes new grants');check('Dynamic grants are unioned with configured IDs',own.content.some(v=>v.id===p));
 await req('GET','/api/v3/patients/reception-lookup',null,staff.accessToken,400,'No unrestricted reception directory dump');
 await req('GET','/api/v3/patients/reception-lookup?keyword=a',null,staff.accessToken,400,'Short lookup rejected');
 await req('GET','/api/v3/patients/reception-lookup?keyword=QA&size=100',null,staff.accessToken,400,'Lookup page bounded');
 for(const [label,token] of [['Doctor',doctor.accessToken],['Patient',patient.accessToken]])await req('GET','/api/v3/patients/reception-lookup?keyword=QA',null,token,403,label+' cannot use Staff reception lookup');
 await req('GET',base+'/medical-records',null,staff.accessToken,403,'Administrative grant does not permit clinical read');
 await req('GET',base,null,patient.accessToken,403,'Other Patient still cannot read administrative profile');
 const proof={contractVersion:'patient-reception/1',nonce:randomUUID(),requestedAt:new Date().toISOString(),caller:{subject,principalType:'USER',patientId:null,doctorId:null,serviceName:null},patientIds:[p],action:'READ'};
 await req('POST','/api/v1/auth/internal/reception-assignments',proof,staff.accessToken,401,'Browser bearer cannot provision grants');
 const settings=JSON.parse(fs.readFileSync(path.join(runtime,'settings.json')));
 await req('POST','/api/v1/auth/internal/reception-assignments',proof,null,401,'Introspection secret cannot impersonate reception provider',{Authorization:'Basic '+Buffer.from('patient-reception:'+settings.PATIENT_AUTH_CLIENT_SECRET).toString('base64')});
 check('Persistent rows contain no clinical operations',sql('auth',"select count(*) from reception_patient_assignments where operations && ARRAY['MEDICAL_READ','MEDICAL_CREATE','MEDICAL_UPDATE','VALID_VISIT']::text[]")==='0');
 check('No static UUID configuration change was needed',hash()===state.assignmentsHash);
 state.grantId=sql('auth',`select id from reception_patient_assignments where subject='${subject}' and patient_id='${p}' order by id limit 1`);check('Exact grant persisted',!!state.grantId);save();
 check('New profile has no account binding',sql('auth',`select count(*) from users where patient_id='${p}'`)==='0');
 if(!state.slot){const date=new Date();date.setHours(date.getHours()+1,0,0,0);const hour=date.getHours();state.slot=await req('POST',`/api/v1/doctors/${doctor.user.doctorId}/slots`,{slotDate:date.toLocaleDateString('en-CA'),startTime:String(hour).padStart(2,'0')+':00',endTime:String(hour+1).padStart(2,'0')+':00'},doctor.accessToken,201,'Publish real Doctor offer');save();}
 if(!state.booking){state.bookingKey ||= randomUUID();save();state.booking=await req('POST','/api/v2/appointments',{patientId:p,doctorId:doctor.user.doctorId,slotId:state.slot.id,reasonForVisit:'Synthetic reception scope regression'},staff.accessToken,201,'Staff books for Patient without an account using derived exact scope',{'Idempotency-Key':state.bookingKey});save();}
 check('Booking without Patient account is confirmed',state.booking.status==='CONFIRMED');
 check('Canonical Patient PostgreSQL row exists',sql('patient',`select count(*) from patients where id='${p}' and address='${changed.address}' and version=${changed.version}`)==='1');
}
main().then(()=>{publish();console.log(JSON.stringify({phase,checks:evidence.length,failures:evidence.filter(e=>!e.pass).length,patientId:state.patient?.id}));}).catch(e=>{publish();console.error(e.message);console.log(JSON.stringify(evidence.slice(-3)));process.exitCode=1;});
