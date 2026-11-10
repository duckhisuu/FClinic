// Real local API/PostgreSQL regression; creates synthetic Doctor/Slot fixtures only.
const fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),checks=[];
const output={checkedAt:new Date().toISOString(),checks};
const publish=()=>fs.writeFileSync(path.join(__dirname,'doctor-slot-overlap-evidence.json'),JSON.stringify(output,null,2));
function check(label,pass,details={}){checks.push({label,pass,...details});publish();if(!pass)throw Error(label);}
const realm=JSON.parse(fs.readFileSync(path.join(root,'ClinicAppointmentBE/infra/keycloak/fclinic-realm.json')));
const env=Object.fromEntries(fs.readFileSync(path.join(root,'ClinicAppointmentBE/.env'),'utf8').split(/\r?\n/).filter(s=>/^[A-Z_]+=/.test(s)).map(s=>[s.slice(0,s.indexOf('=')),s.slice(s.indexOf('=')+1)]));
const sql=q=>execFileSync('docker',['exec','fclinic-postgres-doctor','psql','-U',env.DB_USERNAME,'-d',env.DOCTOR_DB_NAME,'-Atc',q],{encoding:'utf8'}).trim();
let token;
async function req(method,route,body){const r=await fetch('http://localhost:8080'+route,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)});return {status:r.status,body:await r.json()};}
async function main(){
 const u=realm.users.find(u=>u.realmRoles?.includes('ROLE_DOCTOR'));
 const login=await req('POST','/api/v1/auth/login',{username:u.username,password:u.credentials.find(c=>c.type==='password').value});
 check('Real Doctor login',login.status===200);token=login.body.data.accessToken;
 const overlapSql="select count(*) from doctor_time_slots a join doctor_time_slots b on a.doctor_id=b.doctor_id and a.slot_date=b.slot_date and a.id<b.id and a.start_time<b.end_time and b.start_time<a.end_time";
 output.preexistingOverlapPairs=Number(sql(overlapSql));publish();
 async function doctor(){const r=await req('POST','/api/v1/doctors',{name:'Synthetic Overlap QA '+Date.now(),specialty:'QA',department:'QA',consultationFee:1});check('Create synthetic Doctor fixture',r.status===201);return r.body.data.id;}
 const id=await doctor();output.doctorId=id;const base='/api/v1/doctors/'+id+'/slots',slot={slotDate:'2050-02-15',startTime:'08:00',endTime:'09:00'};
 const results=await Promise.all(Array.from({length:8},()=>req('POST',base,slot)));
 check('Concurrent identical slots have exactly one winner',results.filter(r=>r.status===201).length===1,{statuses:results.map(r=>r.status)});
 check('All competing writes return explicit overlap conflict',results.filter(r=>r.status===409&&r.body.code==='DOCTOR_SLOT_OVERLAP').length===7);
 const adjacent=await req('POST',base,{...slot,startTime:'09:00',endTime:'10:00'});check('Adjacent slot allowed',adjacent.status===201);
 const otherDay=await req('POST',base,{...slot,slotDate:'2050-02-16'});check('Same hour on another date allowed',otherDay.status===201);
 const other=await doctor();const otherDoctor=await req('POST','/api/v1/doctors/'+other+'/slots',slot);check('Different Doctor same hour allowed',otherDoctor.status===201);
 const duplicate=await req('POST',base,slot);check('Sequential duplicate rejected',duplicate.status===409&&duplicate.body.code==='DOCTOR_SLOT_OVERLAP');
 const list=await req('GET',base+'?date='+slot.slotDate);check('Readback contains exactly two adjacent slots',list.status===200&&list.body.data.length===2);
 check('PostgreSQL has one row for contended slot',sql(`select count(*) from doctor_time_slots where doctor_id='${id}' and slot_date='2050-02-15' and start_time='08:00'`)==='1');
 check('No new overlap pairs and existing rows preserved',Number(sql(overlapSql))===output.preexistingOverlapPairs);
}
main().then(()=>console.log(JSON.stringify({checks:checks.length,failures:checks.filter(c=>!c.pass).length,...{doctorId:output.doctorId,preexistingOverlapPairs:output.preexistingOverlapPairs}}))).catch(e=>{publish();console.error(e.message);process.exitCode=1;});
