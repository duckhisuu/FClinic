import React from 'react';
import {render,screen} from '@testing-library/react';
import {it,expect,vi} from 'vitest';
import {MedicalRecordsPage} from '../pages/MedicalRecordsPage';
import {MedicalRecordDetailPage} from '../pages/MedicalRecordDetailPage';
import {makeFixtures} from '../preview/fixtures';

const {patients:[patient],records:[record]}=makeFixtures();
const session={patientId:patient.id,permissions:{canCreateRecord:()=>false,canUpdateRecord:()=>false}};

it('shows the successful empty medical list without treating missing write access as a read failure',async()=>{
 const api={getPatient:vi.fn().mockResolvedValue(patient),listRecords:vi.fn().mockResolvedValue({content:[],totalElements:0,page:0,size:10,totalPages:0})};
 render(<MedicalRecordsPage api={api} patientId={patient.id} session={session} navigate={vi.fn()}/>);
 expect(await screen.findByText('Chưa có bệnh án')).toBeVisible();
 expect(screen.queryByText('Chưa sẵn sàng trên hệ thống thật')).not.toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'Tạo bệnh án'})).not.toBeInTheDocument();
});

it('displays a readable record without edit permissions or a false blocker',async()=>{
 const api={getPatient:vi.fn().mockResolvedValue(patient),getRecord:vi.fn().mockResolvedValue(record)};
 render(<MedicalRecordDetailPage api={api} patientId={patient.id} recordId={record.id} session={session} navigate={vi.fn()}/>);
 expect(await screen.findByText(record.diagnosis)).toBeVisible();
 expect(screen.queryByText('Chưa sẵn sàng trên hệ thống thật')).not.toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'Chỉnh sửa bệnh án'})).not.toBeInTheDocument();
});
