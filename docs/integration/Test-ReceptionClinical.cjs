// Test orchestration only. Real services/database, explicitly synthetic records.
const fs=require('node:fs'),path=require('node:path'),{randomUUID}=require('node:crypto'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),runtime=path.join(root,'.runtime'),stateFile=path.join(runtime,'reception-clinical-state.json');
const state=fs.existsSync(stateFile)?JSON.parse(fs.readFileSync(stateFile)):{};
const phase=process.argv[2]||'verify',evidenceFile=path.join(root,'docs/integration/reception-clinical-evidence.json');
const evidence=fs.existsSync(evidenceFile)?JSON.parse(fs.readFileSync(evidenceFile)).checks:[];
const attempt=1+Math.max(0,...evidence.filter(e=>e.phase===phase).map(e=>e.attempt||1));
const realm=JSON.parse(fs.readFileSync(path.join(root,'ClinicAppointmentBE/infra/keycloak/fclinic-realm.json')));
const env=Object.fromEntries(fs.readFileSync(path.join(root,'ClinicAppointmentBE/.env'),'utf8').split(/\r?\n/).filter(x=>/^[A-Z_]+=/.test(x)).map(x=>[x.slice(0,x.indexOf('=')),x.slice(x.indexOf('=')+1)]));
const save=()=>fs.writeFileSync(stateFile,JSON.stringify(state,null,2));
const publish=()=>{
 const latest={};for(const e of evidence)latest[e.phase]=Math.max(latest[e.phase]||0,e.attempt||1);
 const current=evidence.filter(e=>(e.attempt||1)===latest[e.phase]);
 fs.writeFileSync(evidenceFile,JSON.stringify({updatedAt:new Date().toISOString(),patientId:state.patientId,appointmentId:state.appointmentId,recordId:state.recordId,latestAttempts:latest,latestChecks:current.length,latestFailures:current.filter(e=>!e.pass).length,historicalFailures:evidence.filter(e=>!e.pass).length,checks:evidence},null,2));
};
function check(label,pass,details={}){evidence.push({phase,attempt,label,pass,...details});publish();if(!pass)throw Error(label);}
async function req(method,route,body,token,status,label){
 const r=await fetch('http://localhost:8080'+route,{method,headers:{...(body!==undefined?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{})},...(body!==undefined?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)});
 const json=await r.json();check(label||method+' '+route,r.status===status,{method,route,expected:status,actual:r.status});return json.data;
}
const sql=(svc,q)=>execFileSync('docker',['exec','fclinic-postgres-'+svc,'psql','-U',env.DB_USERNAME,'-d',env[svc.toUpperCase()+'_DB_NAME'],'-Atc',q],{encoding:'utf8'}).trim();
const account=role=>{const u=realm.users.find(u=>u.realmRoles?.includes(role));return {username:u.username,password:u.credentials.find(c=>c.type==='password').value}};
const login=u=>req('POST','/api/v1/auth/login',{username:u.username,password:u.password},undefined,200,'Real login');
function grants(){
 const file=path.join(runtime,'assignments.json');
 if(!state.assignmentBackup){state.assignmentBackup='assignments-before-reception-'+Date.now()+'.json';fs.copyFileSync(file,path.join(runtime,state.assignmentBackup));save();}
 const config=JSON.parse(fs.readFileSync(file)); const all=config.app.authorization.grants;
 state.grantIds ||= [randomUUID(),randomUUID(),randomUUID(),randomUUID()];
 const common={active:true,'all-patients':false,'valid-from':new Date(Date.now()-60000).toISOString(),'valid-until':new Date(Date.now()+8*3600000).toISOString(),'patient-ids':state.patientId?[state.patientId]:[]};
 const subject=role=>realm.users.find(u=>u.realmRoles?.includes(role)).id;
 all[state.grantIds[0]]={...common,subject:subject('ROLE_STAFF'),operations:['ADMIN_ASSIGNMENT_CREATE','ADMIN_ASSIGNMENT_READ','ADMIN_ASSIGNMENT_UPDATE','ADMIN_ASSIGNMENT_SEARCH','ADMIN_PROFILE_READ','ADMIN_PROFILE_UPDATE','ADMIN_SEARCH','APPOINTMENT_CREATE','APPOINTMENT_READ','APPOINTMENT_SEARCH','APPOINTMENT_AVAILABILITY']};
 all[state.grantIds[1]]={...common,subject:subject('ROLE_DOCTOR'),operations:['DOCTOR_PROFILE_READ','DOCTOR_SEARCH','MEDICAL_READ','MEDICAL_CREATE','MEDICAL_UPDATE']};
 if(state.readerSubject)all[state.grantIds[2]]={...common,subject:state.readerSubject,operations:['DOCTOR_PROFILE_READ','DOCTOR_SEARCH','MEDICAL_READ']};
 if(state.appointmentId)all[state.grantIds[3]]={...common,subject:subject('ROLE_DOCTOR'),operations:['VALID_VISIT'],'appointment-id':state.appointmentId,'visit-date':state.visitDate,'visit-id':state.visitId};
 fs.writeFileSync(file,JSON.stringify(config,null,2));save();
}
async function prepare(){
 const admin=await login(account('ROLE_ADMIN'));
 state.suffix ||= Date.now().toString(36);state.name ||= 'QA Reception '+state.suffix;save();
 for(const key of ['patient','outsider','reader']){
  state[key] ||= {username:'reception.'+key+'.'+state.suffix,password:'T!'+randomUUID(),email:'reception.'+key+'.'+state.suffix+'@example.test'};save();
  if(!state[key].registered){
   let u;
   if(key==='reader'){
    if(!state.readerDoctor){state.readerDoctor=await req('POST','/api/v1/doctors',{name:'QA Read Only Doctor '+state.suffix,specialty:'General',department:'Synthetic QA',consultationFee:100000},admin.accessToken,201,'Create actual reader Doctor UUID');save();}
    u=await req('POST','/api/v1/auth/admin/users',{...state[key],fullName:'QA Read Only Doctor',role:'ROLE_DOCTOR',doctorId:state.readerDoctor.id},admin.accessToken,201,'Admin provisions least-privilege reader Doctor');
   }else u=await req('POST','/api/v1/auth/register',{...state[key],fullName:key==='patient'?state.name:'QA Outside Patient'},undefined,201,'Register synthetic '+key);
   state[key].registered=true;state[key].id=u.id;save();
  }
 }
 const reader=await login(state.reader);state.readerSubject=JSON.parse(Buffer.from(reader.accessToken.split('.')[1],'base64url')).sub;save();grants();
 check('No wildcard human grant added',state.grantIds.every(id=>!JSON.parse(fs.readFileSync(path.join(runtime,'assignments.json'))).app.authorization.grants[id]?.['all-patients']));
 console.log('Finite grants prepared. Restart Auth, then Staff creates the profile in React.');
}
async function scope(){
 const pid=process.argv[3];if(!/^[a-f0-9-]{36}$/.test(pid||''))throw Error('Supply browser-created Patient UUID');
 const staff=await login(account('ROLE_STAFF')),subject=JSON.parse(Buffer.from(staff.accessToken.split('.')[1],'base64url')).sub;
 check('Staff-created synthetic row exists with exact immutable creator',sql('patient',`select count(*) from patients where id='${pid}' and created_by='${subject}' and full_name='${state.name}' and email='${state.patient.email}' and status='ACTIVE'`)==='1');
 state.patientId=pid;save();
 const admin=await login(account('ROLE_ADMIN'));const p=await login(state.patient);
 check('New account initially unlinked',p.user.patientId==null);
 check('No other account owns reception profile',sql('auth',`select count(*) from users where patient_id='${pid}'`)==='0');
 await req('PUT',`/api/v1/auth/admin/users/${p.user.id}`,{patientId:pid},admin.accessToken,200,'Trusted Admin associates identified reception Patient account');
 const linked=await login(state.patient);check('Verified patient identity binding',linked.user.patientId===pid);
 grants();console.log('Exact Patient assignments prepared. Restart Auth, then Staff books via React.');
}
async function visit(){
 const id=process.argv[3]||sql('appointment',`select id from appointments where patient_id='${state.patientId}' and status='CONFIRMED' order by created_at desc limit 1`);if(!/^[a-f0-9-]{36}$/.test(id||''))throw Error('Supply actual booked Appointment UUID');
 const staff=await login(account('ROLE_STAFF')),doctor=await login(account('ROLE_DOCTOR'));
 const a=await req('GET','/api/v2/appointments/'+id,undefined,staff.accessToken,200,'Staff reads actual booked appointment');
 check('Appointment exact Patient/Doctor/slot and CONFIRMED',a.patientId===state.patientId&&a.doctorId===doctor.user.doctorId&&a.status==='CONFIRMED');
 state.appointmentId=id;state.doctorId=doctor.user.doctorId;state.slotId=a.slotId;
 state.visitDate=new Date(Math.floor((Date.now()-60000)/60000)*60000).toISOString();state.visitId=randomUUID();save();
 check('No MedicalRecord before clinical examination',sql('patient',`select count(*) from medical_records where patient_id='${state.patientId}'`)==='0');
 grants();console.log('Exact appointment + visit timestamp VALID_VISIT prepared. Restart Auth; Doctor records the encounter in React.');
}
async function slots(){
 const doctor=await login(account('ROLE_DOCTOR'));state.doctorId=doctor.user.doctorId;
 const local=new Date();local.setHours(local.getHours()+1,0,0,0);
 const date=local.toLocaleDateString('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}),hour=local.getHours();
 if(!state.offer){state.offer=await req('POST',`/api/v1/doctors/${state.doctorId}/slots`,{slotDate:date,startTime:String(hour).padStart(2,'0')+':00',endTime:String(hour+1).padStart(2,'0')+':00'},doctor.accessToken,201,'Doctor publishes real same-day 60-minute offer');save();}
 await req('GET',`/api/v2/appointments/available-slots?doctorId=${state.doctorId}&date=${date}`,undefined,doctor.accessToken,200,'Offer visible through protected actual provider');
}
async function verify(){
 const staff=await login(account('ROLE_STAFF')),doctor=await login(account('ROLE_DOCTOR')),patient=await login(state.patient),reader=await login(state.reader),outsider=await login(state.outsider),admin=await login(account('ROLE_ADMIN'));
 const base='/api/v3/patients',p=state.patientId,rbase=base+'/'+p+'/medical-records';
 const profile=await req('GET',base+'/'+p,undefined,staff.accessToken,200,'Get canonical reception profile');
 for(const query of ['keyword='+encodeURIComponent(state.name),'keyword='+encodeURIComponent(profile.patientCode),'phoneNumber='+encodeURIComponent(profile.phoneNumber)]){
  const found=await req('GET',base+'?'+query+'&page=0&size=1',undefined,staff.accessToken,200,'Search by name/code/phone with pagination');check('Search returns exact finite Patient',found.content.length===1&&found.content[0].id===p&&found.totalElements===1);
 }
 const next=await req('GET',base+'?page=1&size=1',undefined,staff.accessToken,200,'Second finite page');check('Pagination is bounded',next.content.length===0&&next.totalElements===1);
 const body=Object.fromEntries(['fullName','phoneNumber','email','dateOfBirth','gender','address','status','version'].map(k=>[k,profile[k]]));body.address='Synthetic reception address updated through live API';
 const updated=await req('PUT',base+'/'+p,body,staff.accessToken,200,'Replace Patient profile');check('Profile version advances',updated.version===profile.version+1);
 await req('PUT',base+'/'+p,body,staff.accessToken,409,'Stale profile rejected');
 const reread=await req('GET',base+'/'+p,undefined,patient.accessToken,200,'Patient reads saved profile');check('Profile update read back',reread.address===body.address&&reread.version===updated.version);
 const list=await req('GET',rbase+'?page=0&size=1',undefined,doctor.accessToken,200,'List medical records');check('Browser-created record exists',list.content.length===1&&list.totalElements===1);
 const record=list.content[0];state.recordId=record.id;save();const route=rbase+'/'+record.id;
 check('Real Doctor UUID and appointment association',record.doctorId===state.doctorId&&record.appointmentId===state.appointmentId);
 const read=await req('GET',route,undefined,doctor.accessToken,200,'Get medical record');
 const update={visitDate:read.visitDate,symptoms:read.symptoms,diagnosis:read.diagnosis,treatment:read.treatment,notes:'Synthetic clinical amendment, verified through PostgreSQL',version:read.version,changeReason:'QA verifies optimistic locking and revision audit'};
 const amended=await req('PUT',route,update,doctor.accessToken,200,'Replace medical record with version and reason');check('Medical version advances',amended.version===read.version+1);
 await req('PUT',route,update,doctor.accessToken,409,'Stale medical version rejected');
 for(const [label,token] of [['author Doctor',doctor.accessToken],['assigned read-only Doctor',reader.accessToken],['own Patient',patient.accessToken]]){
  const result=await req('GET',route,undefined,token,200,label+' reads persisted record');check(label+' sees amended record',result.notes===update.notes&&result.version===amended.version);
  await req('GET',rbase+'?page=0&size=1',undefined,token,200,label+' lists records');
 }
 const snapshot=sql('patient',`select json_build_object('profile',(select row_to_json(p) from patients p where id='${p}'),'record',(select row_to_json(r) from medical_records r where id='${record.id}'),'patientRevisions',(select count(*) from patient_revisions where patient_id='${p}'),'recordRevisions',(select count(*) from medical_record_revisions where medical_record_id='${record.id}'))`);
 const clinical={doctorId:state.doctorId,appointmentId:state.appointmentId,visitDate:state.visitDate,symptoms:'QA synthetic symptoms',diagnosis:'QA synthetic encounter',treatment:'QA synthetic documentation',notes:'Synthetic acceptance only'};
 for(const [label,token] of [['Staff',staff.accessToken],['Admin without clinical role',admin.accessToken],['outside Patient',outsider.accessToken]])await req('GET',route,undefined,token,403,label+' cannot read clinical data');
 await req('GET',route,undefined,undefined,401,'Missing token');await req('GET',route,undefined,'invalid-token',401,'Invalid token');
 await req('GET',base+'/'+randomUUID()+'/medical-records',undefined,doctor.accessToken,403,'Doctor outside finite Patient scope');
 await req('GET',rbase+'/'+randomUUID(),undefined,doctor.accessToken,404,'Absent record correctly rejected');
 await req('GET',base+'/not-a-uuid',undefined,staff.accessToken,400,'Malformed Patient UUID');
 await req('POST',rbase,clinical,staff.accessToken,403,'Staff cannot create medical record');
 await req('POST',rbase,{...clinical,doctorId:randomUUID()},doctor.accessToken,403,'Wrong Doctor UUID');
 await req('POST',rbase,{...clinical,doctorId:'invalid'},doctor.accessToken,400,'Malformed Doctor UUID');
 await req('POST',rbase,{...clinical,visitDate:new Date(Date.now()+86400000).toISOString()},doctor.accessToken,400,'Future encounter rejected');
 await req('POST',rbase,{...clinical,appointmentId:null},doctor.accessToken,403,'Null appointment is not an implicit walk-in grant');
 await req('PUT',route,{...update,version:amended.version},reader.accessToken,403,'Read-only Doctor cannot amend');
 await req('PUT',route,{...update,version:amended.version},patient.accessToken,403,'Patient cannot amend');
 await req('PUT',base+'/'+p,{...body,version:updated.version,address:'UNAUTHORIZED'},doctor.accessToken,403,'Doctor cannot change administrative profile');
 const after=sql('patient',`select json_build_object('profile',(select row_to_json(p) from patients p where id='${p}'),'record',(select row_to_json(r) from medical_records r where id='${record.id}'),'patientRevisions',(select count(*) from patient_revisions where patient_id='${p}'),'recordRevisions',(select count(*) from medical_record_revisions where medical_record_id='${record.id}'))`);
 check('All rejected writes leave database and revisions unchanged',snapshot===after);
 const db=JSON.parse(after);check('Canonical PostgreSQL profile and record match HTTP',db.profile.version===updated.version&&db.record.version===amended.version&&db.record.notes===update.notes);
 check('Revision rows persist',db.patientRevisions===updated.version+1&&db.recordRevisions===amended.version+1);
 const revision=JSON.parse(sql('patient',`select row_to_json(r) from medical_record_revisions r where medical_record_id='${record.id}' order by entity_version desc limit 1`));
 check('Amendment reason is audited',revision.change_reason===update.changeReason);
 state.verifiedRecord=amended;save();
}
async function outage(){
 const doctor=await login(account('ROLE_DOCTOR')),p=state.patientId;
 const before=sql('patient',`select count(*) from medical_records where patient_id='${p}'`);
 await req('POST',`/api/v3/patients/${p}/medical-records`,{doctorId:state.doctorId,appointmentId:state.appointmentId,visitDate:state.visitDate,symptoms:'Synthetic outage',diagnosis:'Must not persist'},doctor.accessToken,503,'Doctor provider outage is not success');
 check('Outage leaves MedicalRecord count unchanged',sql('patient',`select count(*) from medical_records where patient_id='${p}'`)===before);
}
async function extras(){
 const staff=await login(account('ROLE_STAFF')),doctor=await login(account('ROLE_DOCTOR')),patient=await login(state.patient),reader=await login(state.reader),outside=await login(state.outsider);
 const base='/api/v3/patients/'+state.patientId,rbase=base+'/medical-records',route=rbase+'/'+state.recordId;
 const record=await req('GET',route,undefined,doctor.accessToken,200,'Read record after service restart');
 const clinical={doctorId:state.doctorId,appointmentId:state.appointmentId,visitDate:state.visitDate,symptoms:'Synthetic duplicate',diagnosis:'Must not duplicate existing appointment record'};
 const before=sql('patient',`select count(*) from medical_records where patient_id='${state.patientId}'`);
 await req('POST',rbase,clinical,doctor.accessToken,409,'One medical record per Appointment enforced');
 const replacement=Object.fromEntries(['visitDate','symptoms','diagnosis','treatment','notes','version'].map(k=>[k,record[k]]));
 await req('PUT',route,replacement,doctor.accessToken,400,'Change reason is mandatory');
 const same=await req('PUT',route,{...replacement,changeReason:'QA validates no-op update'},doctor.accessToken,200,'Unchanged clinical update is idempotent');
 check('No-op preserves medical version',same.version===record.version);
 const profile=await req('GET',base,undefined,staff.accessToken,200,'Read current profile for no-op and status checks');
 const body=Object.fromEntries(['fullName','phoneNumber','email','dateOfBirth','gender','address','status','version'].map(k=>[k,profile[k]]));
 const noChange=await req('PUT',base,body,staff.accessToken,200,'Unchanged profile update is idempotent');check('No-op preserves profile version',noChange.version===profile.version);
 const inactive=await req('PUT',base,{...body,status:'INACTIVE'},staff.accessToken,200,'Staff deactivates exact assigned profile');
 await req('POST',rbase,clinical,doctor.accessToken,409,'Inactive Patient cannot receive a new medical record');
 const active=await req('PUT',base,{...body,version:inactive.version,status:'ACTIVE'},staff.accessToken,200,'Staff restores active status');check('Active status restored with audited version',active.status==='ACTIVE'&&active.version===inactive.version+1);
 check('Rejected duplicate and inactive writes did not add records',sql('patient',`select count(*) from medical_records where patient_id='${state.patientId}'`)===before);
 const page=await req('GET',rbase+'?page=1&size=1',undefined,patient.accessToken,200,'Medical pagination next page');check('Medical pagination bounded',page.content.length===0&&page.totalElements===1);
 const caps=await req('GET','/api/v1/auth/me/patient-capabilities',undefined,reader.accessToken,200,'Read-only Doctor actual capabilities');
 check('Read capability does not imply write',caps.readRecordPatientIds.includes(state.patientId)&&!caps.createRecordPatientIds.includes(state.patientId)&&!caps.updateRecordPatientIds.includes(state.patientId));
 const staffCaps=await req('GET','/api/v1/auth/me/patient-capabilities',undefined,staff.accessToken,200,'Staff actual capabilities');check('Staff clinical navigation denied',staffCaps.readRecordPatientIds.length===0);
 if(!state.outsideProfile){
  await req('GET','/api/v3/patients/self-profile',undefined,outside.accessToken,404,'New Patient has no self-owned profile');
  state.outsideProfile=await req('POST','/api/v3/patients',{fullName:'QA Outside Patient',phoneNumber:'+84900000952',email:state.outsider.email,gender:'UNKNOWN'},outside.accessToken,201,'Unlinked Patient self-creates canonical profile');save();
  const retry=await req('POST','/api/v3/patients',{fullName:'QA Outside Patient',phoneNumber:'+84900000952',email:state.outsider.email,gender:'UNKNOWN'},outside.accessToken,201,'Self creation retry');check('Self retry returns existing UUID',retry.id===state.outsideProfile.id);
 }
 const self=await req('GET','/api/v3/patients/self-profile',undefined,outside.accessToken,200,'Server-owned initial profile lookup');check('Self profile exact UUID',self.id===state.outsideProfile.id);
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:self.id},outside.accessToken,200,'Verified self-profile binding');
 await req('GET',route,undefined,outside.accessToken,403,'Other linked Patient still cannot read this clinical record');
 const inbox=await req('GET','/api/v1/notifications/me?size=100',undefined,patient.accessToken,200,'Patient reads actual booking Notification');
 const notification=inbox.content.find(n=>n.appointmentId===state.appointmentId);check('Booking event delivered to exact Patient account',notification?.recipientUserId===patient.user.id);
 if(notification)await req('GET','/api/v1/notifications/'+notification.id,undefined,outside.accessToken,404,'Other Patient cannot read booking Notification');
 for(const [label,token] of [['Staff',staff.accessToken],['Doctor',doctor.accessToken],['Patient',patient.accessToken]])await req('GET','/api/v2/appointments/'+state.appointmentId,undefined,token,200,label+' reads exact appointment');
 const refreshed=await req('POST','/api/v1/auth/refresh',{refreshToken:patient.refreshToken},undefined,200,'Real refresh rotation');
 await req('POST','/api/v1/auth/logout',{refreshToken:refreshed.refreshToken},refreshed.accessToken,200,'Logout current test session');
 await req('GET',route,undefined,refreshed.accessToken,401,'Revoked token cannot read medical data');
 state.finalProfileVersion=active.version;state.finalRecordVersion=record.version;save();
}
async function audit(){
 const patient=await login(state.patient),reader=await login(state.reader);
 const base='/api/v3/patients/'+state.patientId;
 const profile=await req('GET',base,undefined,patient.accessToken,200,'Final Patient reads canonical profile');
 check('Staff editor persisted exact address',profile.address==='Synthetic reception address updated through React Staff editor');
 const record=await req('GET',base+'/medical-records/'+state.recordId,undefined,reader.accessToken,200,'Final assigned reader reads canonical medical record');
 check('Final record author and appointment match',record.doctorId===state.doctorId&&record.appointmentId===state.appointmentId&&record.notes==='Synthetic clinical amendment, verified through PostgreSQL');
 const snapshot=sql('patient',`select json_build_object('profile',(select row_to_json(p) from patients p where id='${state.patientId}'),'record',(select row_to_json(r) from medical_records r where id='${state.recordId}'),'patientRevisions',(select count(*) from patient_revisions where patient_id='${state.patientId}'),'medicalRevisions',(select count(*) from medical_record_revisions where medical_record_id='${state.recordId}'))`);
 const data=JSON.parse(snapshot);
 check('Final API and PostgreSQL versions match',data.profile.version===profile.version&&data.record.version===record.version);
 check('All profile and medical revisions retained',data.patientRevisions===profile.version+1&&data.medicalRevisions===record.version+1);
 check('Exactly one medical record for original registration',sql('patient',`select count(*) from medical_records where patient_id='${state.patientId}' and appointment_id='${state.appointmentId}'`)==='1');
 check('Appointment remains honestly CONFIRMED',sql('appointment',`select status from appointments where id='${state.appointmentId}'`)==='CONFIRMED');
 if(state.persistedSnapshot)check('Cold service restart preserved exact database rows and audits',snapshot===state.persistedSnapshot);
 state.persistedSnapshot=snapshot;state.finalProfileVersion=profile.version;state.finalRecordVersion=record.version;state.finalPatientRevisionCount=data.patientRevisions;state.finalMedicalRevisionCount=data.medicalRevisions;save();
}
const actions={prepare,scope,slots,visit,verify,outage,extras,audit};
if(!actions[phase])throw Error('Unknown test phase');
actions[phase]().then(()=>{publish();console.log(JSON.stringify({phase,attempt,checks:evidence.filter(e=>e.phase===phase&&(e.attempt||1)===attempt).length,failed:evidence.filter(e=>e.phase===phase&&(e.attempt||1)===attempt&&!e.pass).length,historicalFailures:evidence.filter(e=>!e.pass).length,patientId:state.patientId,recordId:state.recordId}));}).catch(e=>{publish();console.error(e.message);console.log(JSON.stringify(evidence.slice(-3)));process.exitCode=1;});
