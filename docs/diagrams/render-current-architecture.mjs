import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const out = path.dirname(fileURLToPath(import.meta.url));
const C = { ink:'#132d46', muted:'#53677c', border:'#d7e2eb', rest:'#2563eb', auth:'#16866a', ref:'#7c3aed', event:'#c97213', db:'#b68b25' };
const shapes=[], arrows=[], labels=[];
const esc = s => String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
const text=(x,y,s,size=26,weight=400,fill=C.ink,anchor='start',target=shapes)=>target.push(`<text x="${x}" y="${y}" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}">${esc(s)}</text>`);
function panel(y,h,n,title,subtitle){
  shapes.push(`<rect x="48" y="${y}" width="2464" height="${h}" rx="24" fill="#fff" stroke="${C.border}" stroke-width="2"/>`);
  shapes.push(`<rect x="76" y="${y+24}" width="40" height="40" rx="12" fill="#e9f0f8"/>`);
  text(96,y+53,n,25,700,C.ink,'middle'); text(134,y+53,title,31,700);
  if(subtitle)text(134,y+89,subtitle,24,400,C.muted);
}
function node(x,y,w,h,title,lines=[],kind='rest'){
  const fill={rest:'#eff5ff',auth:'#eaf7f2',ref:'#f4efff',event:'#fff5e8',neutral:'#f5f7fa'}[kind];
  const color=C[kind]||C.ink;
  shapes.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="16" fill="${fill}" stroke="${color}" stroke-opacity=".42" stroke-width="2"/>`);
  text(x+w/2,y+39,title,28,650,C.ink,'middle');
  lines.forEach((s,i)=>text(x+w/2,y+75+i*30,s,23,400,C.muted,'middle'));
}
function database(x,y,w,title,lines=[]){
  const h=104;
  shapes.push(`<path d="M${x},${y+17} C${x},${y-5} ${x+w},${y-5} ${x+w},${y+17} L${x+w},${y+h-17} C${x+w},${y+h+5} ${x},${y+h+5} ${x},${y+h-17} Z" fill="#fff9e6" stroke="${C.db}" stroke-width="2"/><ellipse cx="${x+w/2}" cy="${y+17}" rx="${w/2}" ry="17" fill="#fff3ca" stroke="${C.db}" stroke-width="2"/>`);
  text(x+w/2,y+54,title,25,650,C.ink,'middle');
  lines.forEach((s,i)=>text(x+w/2,y+83+i*25,s,21,400,C.muted,'middle'));
}
function edge(d,kind='rest',label='',x=0,y=0,dashed=false){
  arrows.push(`<path d="${d}" fill="none" stroke="${C[kind]||C.ink}" stroke-width="3.1" stroke-linecap="round" stroke-linejoin="round" marker-end="url(#${kind})"${dashed?' stroke-dasharray="10 7"':''}/>`);
  if(label){const w=label.length*11.7+22; labels.push(`<rect x="${x-w/2}" y="${y-23}" width="${w}" height="32" rx="8" fill="#fff" fill-opacity=".96"/>`);text(x,y,label,22,500,C[kind]||C.ink,'middle',labels);}
}
function badge(x,y,w,s,color=C.auth){ shapes.push(`<rect x="${x}" y="${y}" width="${w}" height="44" rx="12" fill="${color}" fill-opacity=".08"/>`);text(x+w/2,y+30,s,23,500,C.ink,'middle'); }

shapes.push('<rect width="2560" height="1980" fill="#f4f7fb"/>');
text(64,66,'FClinic · Kiến trúc & luồng đặt lịch hiện tại',44,700);
text(64,109,'Cập nhật 10/11/2026 · Đặt lịch, phát thông báo và phân quyền hồ sơ theo bác sĩ trong lịch hẹn',26,400,C.muted);
const legend=[['rest','REST / HTTP'],['auth','Xác thực / scope'],['ref','Đọc tham chiếu / M2M'],['event','Sự kiện bất đồng bộ']];
legend.forEach(([kind,s],i)=>{let x=76+i*560;shapes.push(`<line x1="${x}" y1="155" x2="${x+55}" y2="155" stroke="${C[kind]}" stroke-width="4"${kind==='event'?' stroke-dasharray="9 6"':''}/>`);text(x+70,163,s,24,500,C.muted);});

panel(196,988,'A','Đặt lịch khám & gửi thông báo','Patient / Staff chọn bác sĩ và giờ khám; Appointment lưu giao dịch trước khi relay phát sự kiện.');
node(90,332,180,106,'Patient / Staff',['Người dùng'],'neutral');
node(340,332,270,106,'React Client',[':5173 · Bearer token']);
node(700,332,290,106,'API Gateway',[':8080 · định tuyến']);
node(1110,332,330,106,'Auth Service',[':8085 · HTTP cục bộ'],'auth');
node(1600,332,270,106,'Keycloak',[':8180 · OAuth2 / OIDC'],'auth');
database(2070,332,340,'Auth DB · PostgreSQL',['Tài khoản / vai trò / UUID']);
edge('M270 385 H340','rest','',0,0);
edge('M610 385 H700','rest','1',655,374);
edge('M990 385 H1110','rest','2 Login',1050,373);
edge('M1440 385 H1600','auth','OIDC / tokens',1520,372);
edge('M1275 332 V308 H2240 V332','auth','Registry người dùng',1905,302);

node(260,550,340,120,'Doctor Service',[':8082 · bác sĩ / slots'],'ref');
node(820,550,390,120,'Appointment Service',[':8083 · book / cancel / reschedule']);
node(1450,550,340,120,'Patient Service',[':8081 · hồ sơ / bệnh án'],'ref');
database(260,726,340,'Doctor DB · PostgreSQL',['Bác sĩ / khung giờ khám']);
database(820,726,390,'Appointment DB · PostgreSQL',['appointments / history / results / outbox']);
database(1450,726,340,'Patient DB · PostgreSQL',['Hồ sơ / bệnh án / revisions']);
edge('M746 438 V485 H430 V550','rest','3 Duyệt bác sĩ',560,477);
edge('M922 438 V495 H975 V550','rest','4 Slots / đặt lịch',1051,491);
edge('M1130 550 V438','auth','5 Identity / scope',1250,475);
edge('M820 599 H600','ref','6 Doctor / slot',710,589);
edge('M1210 622 H1450','ref','6 Patient profile',1330,612);
edge('M1210 570 H1330 V507 H1735 V438','ref','M2M service token',1550,500);
edge('M430 670 V726','ref');
edge('M1015 670 V726','rest','7 Commit',1087,704);
edge('M1620 670 V726','ref');
text(1970,547,'Kết quả booking',27,650);
text(1970,584,'8 Trả về qua Gateway → Client',23,400,C.muted);
text(1970,619,'Khóa slot cục bộ: PostgreSQL',23,400,C.muted);
text(1970,654,'Idempotency + optimistic version',23,400,C.muted);

node(285,925,380,122,'Auth recipient resolver',['UUID tài khoản Patient + Doctor',':8085 · credential riêng'],'auth');
node(820,925,390,122,'Outbox relay',['Đọc sau commit · retry / confirm','Thuộc Appointment Service'],'event');
node(1370,925,320,122,'RabbitMQ',['clinic.events · AMQP :5672','Event v3 · UUID'],'event');
node(1850,925,330,122,'Notification Service',[':8084 · ingest / chống trùng','Lưu thông báo & gửi email'],'event');
node(2290,925,180,122,'SMTP',['MailHog',':1025'],'event');
database(1850,1070,330,'Notification DB',['UUID / processed events / delivery']);
edge('M1015 830 V925','event','9a Đọc outbox',1135,888,true);
edge('M820 980 H665','auth','Resolve recipients',742,965);
edge('M1210 986 H1370','event','9b Publish',1290,971,true);
edge('M1690 986 H1850','event','9c Consume',1770,971,true);
edge('M2180 986 H2290','event','Email',2235,972,true);
edge('M2015 1047 V1070','event');
text(1765,875,'Inbox riêng: GET /notifications/me qua Gateway',23,400,C.rest);

panel(1205,365,'B','Quyền xem hồ sơ tự động theo lịch hẹn','Bác sĩ được chọn trong booking có quyền đọc hồ sơ; Auth lấy phân công trực tiếp từ Appointment DB.');
node(90,1320,290,100,'Doctor · React',['Mở Hồ sơ & bệnh án']);
node(490,1320,260,100,'Gateway',[':8080']);
node(860,1320,280,100,'Patient',[':8081 · kiểm tra scope']);
node(1250,1320,280,100,'TLS proxy local',[':8445 · chỉ /auth/scopes'],'auth');
node(1640,1320,280,100,'Auth',[':8085 · CLINICAL'],'auth');
node(2030,1320,380,100,'Appointment',['Đọc doctor_id ↔ patient_id'],'auth');
edge('M380 1370 H490','rest','GET',435,1357);
edge('M750 1370 H860','rest','patients',805,1357);
edge('M1140 1370 H1250','auth','Scope',1195,1357);
edge('M1530 1370 H1640','auth','POST',1585,1357);
edge('M1920 1370 H2030','auth','Basic',1975,1357);
badge(90,1456,725,'CONFIRMED / COMPLETED → được đọc hồ sơ');
badge(845,1456,725,'CANCELLED / NO_SHOW → không tạo quyền',C.ref);
badge(1600,1456,810,'Không gán UUID tĩnh · Không hạn demo 8 giờ',C.rest);
text(90,1542,'Patient chỉ trả hồ sơ trong tập UUID được phép. Hủy một lịch vẫn giữ quyền nếu còn lịch khác hợp lệ với cùng bác sĩ.',24,400,C.muted);

panel(1607,282,'C','Những điểm đã cập nhật so với sơ đồ cũ','');
const notes=[
  ['01','Thông báo đã hoạt động','UUID/schema v3 xuyên suốt; consumer không còn','bị chặn bởi lỗi chuyển UUID → Long.'],
  ['02','Thông báo riêng từng tài khoản','Resolve người nhận qua Auth; chống event lặp.','Retry / DLQ dành cho lỗi xử lý hoặc event sai.'],
  ['03','Phân công theo lịch hẹn','Bác sĩ chỉ đọc hồ sơ có quan hệ lịch khám.','Kiểm tra lại từng request, không cấp quyền bằng role.']
];
notes.forEach(([n,title,a,b],i)=>{const x=90+i*820;text(x,1711,n,25,700,C.rest);text(x+58,1711,title,27,650);text(x+58,1755,a,23,400,C.muted);text(x+58,1791,b,23,400,C.muted);});
text(64,1942,'Runtime cục bộ · 7 service health 200 · Notification: 14 SENT, queue chính 1 consumer · Eureka :8761 (không vẽ các đường discovery)',23,400,C.muted);

const defs=Object.keys(C).filter(k=>['rest','auth','ref','event'].includes(k)).map(k=>`<marker id="${k}" markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0 0 L10 5 L0 10 Z" fill="${C[k]}"/></marker>`).join('');
// Layer panel backgrounds below paths and nodes; keep labels above all connectors.
const bg=shapes.filter(s=>s.startsWith('<rect width=')||s.includes('width="2464"'));
const front=shapes.filter(s=>!s.startsWith('<rect width=')&&!s.includes('width="2464"'));
const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="2560" height="1980" viewBox="0 0 2560 1980" role="img" aria-labelledby="title desc"><title id="title">FClinic — Kiến trúc hiện tại ngày 10 tháng 11 năm 2026</title><desc id="desc">Luồng đặt lịch qua Gateway, Auth và Keycloak, đọc Patient và Doctor, ghi Appointment cùng outbox, publish RabbitMQ tới Notification và SMTP. Luồng mới: quyền đọc hồ sơ của bác sĩ lấy từ lịch CONFIRMED hoặc COMPLETED trong Appointment database; không dùng UUID gán cứng.</desc><defs>${defs}</defs><g font-family="Segoe UI, Arial, sans-serif">${bg.join('')}${arrows.join('')}${front.join('')}${labels.join('')}</g></svg>`;
fs.writeFileSync(path.join(out,'fclinic-current-architecture.svg'),svg);
fs.writeFileSync(path.join(out,'fclinic-current-architecture.html'),`<!doctype html><html lang="vi"><meta charset="utf-8"><title>FClinic architecture</title><style>html,body{margin:0;width:2560px;height:1980px;background:#f4f7fb}svg{display:block}</style>${svg}</html>`);
console.log('Rendered SVG and HTML: '+out);
