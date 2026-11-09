// Test-scope orchestration only. Uses real services and synthetic accounts.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),https=require('node:https');
const {randomUUID}=require('node:crypto'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'), runtime=path.join(root,'.runtime');
const ca=fs.readFileSync(path.join(runtime,'localhost.pem'));
const env=Object.fromEntries(fs.readFileSync(path.join(root,'ClinicAppointmentBE/.env'),'utf8').split(/\r?\n/).filter(x=>/^[A-Z_]+=/.test(x)).map(x=>[x.slice(0,x.indexOf('=')),x.slice(x.indexOf('=')+1)]));
const realm=JSON.parse(fs.readFileSync(path.join(root,'ClinicAppointmentBE/infra/keycloak/fclinic-realm.json')));
const old=JSON.parse(fs.readFileSync(path.join(runtime,'acceptance-state.json')));
const evidence=[],stateFile=path.join(runtime,'onboarding-notification-state.json');
const state=fs.existsSync(stateFile)?JSON.parse(fs.readFileSync(stateFile)):{};
const save=()=>fs.writeFileSync(stateFile,JSON.stringify(state,null,2));
function check(label,pass,details={}){evidence.push({label,pass,...details});if(!pass)throw Error(label);}
async function req(method,route,body,token,expected,label,headers={}){
 const data=body===undefined?undefined:JSON.stringify(body);
 const r=await new Promise((resolve,reject)=>{const q=http.request({hostname:'localhost',port:8080,path:route,method,headers:{...(data?{'Content-Type':'application/json','Content-Length':Buffer.byteLength(data)}:{}),...(token?{Authorization:'Bearer '+token}:{}),...headers}},res=>{let raw='';res.on('data',b=>raw+=b);res.on('end',()=>{let b;try{b=JSON.parse(raw)}catch{b={}}resolve({status:res.statusCode,body:b})})});q.setTimeout(20000,()=>q.destroy(Error('HTTP timeout')));q.on('error',reject);if(data)q.write(data);q.end()});
 if(expected!==undefined)check(label||`${method} ${route}`,r.status===expected,{expected,actual:r.status,method,route});return r.body.data;
}
function sql(service,query){return execFileSync('docker',['exec','fclinic-postgres-'+service,'psql','-U',env.DB_USERNAME,'-d',env[service.toUpperCase()+'_DB_NAME']||service+'_db','-At','-c',query],{encoding:'utf8'}).trim()}
async function login(account){return req('POST','/api/v1/auth/login',{username:account.username,password:account.password},undefined,200,'Real login')}
async function seeded(role){const u=realm.users.find(u=>u.realmRoles?.includes(role));return login({username:u.username,password:u.credentials.find(c=>c.type==='password').value})}
async function main(){
 const admin=await seeded('ROLE_ADMIN'),doctor=await seeded('ROLE_DOCTOR'),staff=await seeded('ROLE_STAFF');
 for(const key of ['a','b']){
  if(!state[key]){const suffix=Date.now().toString(36)+key;state[key]={username:'integration.'+suffix,password:'T!'+randomUUID(),email:`integration.${suffix}@example.test`};save();
   await req('POST','/api/v1/auth/register',{...state[key],fullName:'Integration Synthetic '+key,phoneNumber:key==='a'?'+84900000881':'+84900000882'},undefined,201,'Register synthetic Patient '+key);
  }
 }
 let a=await login(state.a),b=await login(state.b);
 const bToken=b.accessToken;
 if(!state.patient){
  check('Initial account is unlinked',!a.user.patientId);
  await req('POST','/api/v2/appointments',{patientId:old.patient.id,doctorId:old.doctor.id,slotId:old.slots[0].id},a.accessToken,403,'Unlinked Patient cannot book',{'Idempotency-Key':randomUUID()});
  state.patient=await req('POST','/api/v3/patients',{fullName:'Onboarding Integration Synthetic',phoneNumber:'+84900000881',email:state.a.email,dateOfBirth:'1995-01-01',gender:'OTHER'},a.accessToken,201,'Patient creates initial profile');save();
 }
 const pid=state.patient.id, aid=a.user.id;
 check('No MedicalRecord before booking',sql('patient',`select count(*) from medical_records where patient_id='${pid}'`) === '0');
 const subject=JSON.parse(Buffer.from(a.accessToken.split('.')[1],'base64url').toString()).sub;
 const owner=sql('patient',`select created_by from patients where id='${pid}' and status='ACTIVE'`);
 check('Operator verifies exact profile creation owner and active state',owner===subject||owner===aid);
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:pid},bToken,403,'Other account cannot claim Patient A');
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:randomUUID()},a.accessToken,403,'Nonexistent/unproven self-link refused');
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:pid},staff.accessToken,403,'STAFF cannot use Patient self-link');
 if(!a.user.patientId){
  await req('PUT','/api/v1/auth/me/patient-profile',{patientId:pid},a.accessToken,403,'Public arbitrary linking fails closed');
  check('No existing account claims this new profile',sql('auth',`select count(*) from users where patient_id='${pid}'`) === '0');
  // Explicit operator-admin provisioning after database ownership verification.
  // This is NOT automatic self-service onboarding and is reported separately.
  await req('PUT',`/api/v1/auth/admin/users/${aid}`,{patientId:pid},admin.accessToken,200,'Trusted operator provisions verified synthetic Patient');
 }
 let me=await req('GET','/api/v1/auth/me',undefined,a.accessToken,200,'Me after provisioning');check('Me uses linked Patient UUID',me.patientId===pid);
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:pid},a.accessToken,200,'Same trusted binding is idempotent');
 await req('PUT','/api/v1/auth/me/patient-profile',{patientId:old.patient.id},a.accessToken,403,'Verified binding cannot be replaced by caller');
 a=await login(state.a);check('Re-login preserves identity binding',a.user.patientId===pid);
 const refreshed=await req('POST','/api/v1/auth/refresh',{refreshToken:a.refreshToken},undefined,200,'Refresh real token');
 a={...a,...refreshed};check('Refresh preserves binding',a.user.patientId===pid);
 const date=new Date(Date.now()+2*86400000).toISOString().slice(0,10);
 if(!state.slots){state.slots=[];for(const hour of [13,14]){state.slots.push(await req('POST',`/api/v1/doctors/${old.doctor.id}/slots`,{slotDate:date,startTime:`${hour}:00`,endTime:`${hour+1}:00`},doctor.accessToken,201,'Publish synthetic future Doctor offer'))}save()}
 await req('GET',`/api/v2/appointments/available-slots?doctorId=${old.doctor.id}&date=${date}`,undefined,a.accessToken,200,'Real provider availability');
 const booking={patientId:pid,doctorId:old.doctor.id,slotId:state.slots[0].id,reasonForVisit:'Synthetic onboarding notification acceptance'};
 await req('POST','/api/v2/appointments',{...booking,patientId:old.patient.id},a.accessToken,403,'Cannot book for another Patient',{'Idempotency-Key':randomUUID()});
 if(!state.booking){state.bookingKey=randomUUID();save();state.booking=await req('POST','/api/v2/appointments',booking,a.accessToken,201,'Self booking without MedicalRecord',{'Idempotency-Key':state.bookingKey});save()}
 const appid=state.booking.id;
 const replay=await req('POST','/api/v2/appointments',booking,a.accessToken,201,'Idempotent booking replay',{'Idempotency-Key':state.bookingKey});check('Replay returns same Appointment UUID',replay.id===appid);
 check('Confirmed booking persisted once',sql('appointment',`select count(*) from appointments where id='${appid}' and patient_id='${pid}'`) === '1');
 let current=await req('GET',`/api/v2/appointments/${appid}`,undefined,a.accessToken,200,'Read own booking');
 if(current.status==='CONFIRMED'){
  await req('POST','/api/v2/appointments',booking,a.accessToken,409,'Duplicate occupied slot refused',{'Idempotency-Key':randomUUID()});
  await req('PUT',`/api/v2/appointments/${appid}/cancel`,{version:current.version+100,reason:'Synthetic stale'},a.accessToken,409,'Stale booking version rejected',{'Idempotency-Key':randomUUID()});
  current=await req('PUT',`/api/v2/appointments/${appid}/reschedule`,{slotId:state.slots[1].id,version:current.version,reason:'Synthetic reschedule'},a.accessToken,200,'Owner reschedules',{'Idempotency-Key':randomUUID()});
  await req('PUT',`/api/v2/appointments/${appid}/cancel`,{version:current.version,reason:'Synthetic acceptance cleanup'},a.accessToken,200,'Owner cancels',{'Idempotency-Key':randomUUID()});
 }
 const history=await req('GET',`/api/v2/appointments/${appid}/history`,undefined,a.accessToken,200,'Read persisted booking history');check('History includes create/reschedule/cancel',history.length>=3);
 check('Still no MedicalRecord after booking lifecycle',sql('patient',`select count(*) from medical_records where patient_id='${pid}'`) === '0');
 let mine;
 for(let i=0;i<30;i++){mine=await req('GET','/api/v1/notifications/me?size=100',undefined,a.accessToken,200,i===0?'JWT authenticated inbox':undefined);if(mine.content.some(n=>n.appointmentId===appid))break;await new Promise(r=>setTimeout(r,1000))}
 const relevant=mine.content.filter(n=>n.appointmentId===appid);check('Real booking event reached Notification inbox',relevant.length>=1);
 check('Every returned notification belongs to verified caller',mine.content.every(n=>n.recipientUserId===aid));
 const other=await req('GET',`/api/v1/notifications/me?userId=${aid}`,undefined,bToken,200,'Forged inbox userId ignored');check('User B sees no User A rows',other.content.every(n=>n.recipientUserId===b.user.id)&&!other.content.some(n=>n.appointmentId===appid));
 await req('GET',`/api/v1/notifications/${relevant[0].id}`,undefined,bToken,404,'Cross-user detail denied');
 await req('GET','/api/v1/admin/notifications',undefined,a.accessToken,403,'Patient cannot query admin inbox');
 await req('POST',`/api/v1/admin/notifications/${relevant[0].id}/retry`,undefined,bToken,403,'Other user cannot retry');
 await req('GET','/api/v1/notifications/me',undefined,undefined,401,'Notification missing token');
 await req('GET','/api/v1/notifications/me',undefined,'invalid-token',401,'Notification invalid token');
 check('UUID rows persisted in Notification PostgreSQL',Number(sql('notification',`select count(*) from notifications where appointment_id='${appid}' and recipient_user_id='${aid}'`))>=1);
 state.lastVerified=new Date().toISOString();save();
}
async function broker(){
 const id=state.booking.id;
 const payload=JSON.parse(sql('appointment',`select payload from outbox_events where aggregate_id='${id}' and event_type='APPOINTMENT_CREATED' limit 1`));
 const auth='Basic '+Buffer.from(env.RABBITMQ_DEFAULT_USER+':'+env.RABBITMQ_DEFAULT_PASS).toString('base64');
 async function rabbit(route,body){const r=await fetch('http://localhost:15672/api/'+route,{method:body?'POST':'GET',headers:{Authorization:auth,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});if(!r.ok)throw Error('Rabbit management '+r.status);return r.json()}
 const queue='queues/%2F/notification.appointment.dlq';
 const before=await rabbit(queue); const rows=Number(sql('notification',`select count(*) from notifications where event_id='${payload.eventId}'`));
 check('Real booking produced distinct Patient and Doctor rows',rows===2);
 const published=await rabbit('exchanges/%2F/clinic.events/publish',{properties:{content_type:'application/json',delivery_mode:2},routing_key:'appointment.created',payload:JSON.stringify(payload),payload_encoding:'string'});
 check('Fresh booking event redelivery was routed',published.routed===true);
 await new Promise(r=>setTimeout(r,2000));
 check('Redelivery creates no duplicate notifications',Number(sql('notification',`select count(*) from notifications where event_id='${payload.eventId}'`))===rows);
 let sent=0;for(let i=0;i<20;i++){sent=Number(sql('notification',`select count(*) from notifications where appointment_id='${id}' and status='SENT' and sent_at is not null`));if(sent>=6)break;await new Promise(r=>setTimeout(r,1000))}
 check('Confirmation/reschedule/cancel SMTP delivery confirmed for both recipients',sent===6,{sent});
 const malformed={...payload,eventId:randomUUID(),schemaVersion:999};
 await rabbit('exchanges/%2F/clinic.events/publish',{properties:{content_type:'application/json',delivery_mode:2},routing_key:'appointment.created',payload:JSON.stringify(malformed),payload_encoding:'string'});
 let after;for(let i=0;i<20;i++){after=await rabbit(queue);if(after.messages>before.messages)break;await new Promise(r=>setTimeout(r,1000))}
 check('Malformed event isolated in DLQ; old messages retained',after.messages===before.messages+1,{before:before.messages,after:after.messages});
 check('Malformed event writes no notification',sql('notification',`select count(*) from notifications where event_id='${malformed.eventId}'`)==='0');
 const mailbox=await fetch('http://localhost:8025/api/v2/search?kind=to&query='+encodeURIComponent(state.a.email)+'&limit=100').then(r=>r.json());
 check('MailHog received synthetic Patient emails',mailbox.total>=3,{messages:mailbox.total});
}
async function edges(){
 const a=await login(state.a),doctor=await seeded('ROLE_DOCTOR');
 const body={patientId:state.patient.id,doctorId:old.doctor.id,slotId:state.slots[0].id};
 const post=(payload,status,label)=>req('POST','/api/v2/appointments',payload,a.accessToken,status,label,{'Idempotency-Key':randomUUID()});
 const before=sql('appointment',`select count(*) from appointments where patient_id='${state.patient.id}'`);
 await post({...body,doctorId:randomUUID()},404,'Missing Doctor reference is rejected');
 await post({...body,slotId:randomUUID()},409,'Missing published Slot reference is not bookable');
 await post({...body,slotId:'invalid-uuid'},400,'Malformed Slot UUID rejected');
 await req('GET','/api/v2/appointments/'+randomUUID(),undefined,a.accessToken,404,'Missing Appointment is not fabricated');
 const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
 const expired=await req('POST',`/api/v1/doctors/${old.doctor.id}/slots`,{slotDate:date,startTime:'00:00',endTime:'01:00'},doctor.accessToken,201,'Publish synthetic elapsed offer for negative test');
 await post({...body,slotId:expired.id},409,'Elapsed published Slot cannot be booked');
 await req('POST','/api/v1/auth/logout',{refreshToken:a.refreshToken},a.accessToken,200,'Logout synthetic API session');
 await req('POST','/api/v2/appointments',body,a.accessToken,401,'Revoked real token cannot book',{'Idempotency-Key':randomUUID()});
 check('All rejected edge writes preserve appointment count',before===sql('appointment',`select count(*) from appointments where patient_id='${state.patient.id}'`));
}
const phase=process.argv[2], brokerPhase=phase==='broker',edgePhase=phase==='edges';
(brokerPhase?broker():edgePhase?edges():main()).catch(e=>{console.error('Acceptance stopped:',e.message);process.exitCode=1}).finally(()=>{fs.writeFileSync(path.join(__dirname,brokerPhase?'notification-broker-acceptance.json':edgePhase?'appointment-edge-evidence.json':'onboarding-notification-evidence.json'),JSON.stringify({timestamp:new Date().toISOString(),selfServiceLinking:'BLOCKED: no trusted ownership contract; fail-closed guard',trustedOperatorProvisioning:true,evidence},null,2));console.log(`${evidence.filter(x=>x.pass).length}/${evidence.length} executed checks passed`) });
